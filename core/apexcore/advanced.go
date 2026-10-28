package apexcore

// Маршрутизация «для опытных» (как в v2rayNG/Happ): свои правила, стратегия доменов,
// FakeDNS, удалённый и домашний DNS с выбором протокола.

import (
	"errors"
	"fmt"
	"net"
	"net/netip"
	"net/url"
	"strconv"
	"strings"
)

// UserRule — своё правило: значение и действие (proxy | direct | block).
//
//	example.com          — домен и поддомены
//	full:example.com     — только этот домен
//	keyword:google       — домены, где есть слово
//	regexp:^ads\.        — регулярное выражение
//	1.2.3.4, 10.0.0.0/8  — IP-адрес или подсеть
//	port:443, udp:27000-27100, tcp:25565 — порт или диапазон (35. обход по портам)
type UserRule struct {
	Value  string `json:"value"`
	Action string `json:"action"`
}

// DNSSpec — DNS для «удалённых» (через VPN) или «домашних» (напрямую) запросов.
type DNSSpec struct {
	Type   string `json:"type"`   // https | tls | quic | h3 | udp | tcp
	Server string `json:"server"` // https://host/path, host или IP
	IP     string `json:"ip"`     // IP сервера, чтобы не узнавать его через другой DNS
}

func (d *DNSSpec) empty() bool {
	return d == nil || strings.TrimSpace(d.Server) == "" && strings.TrimSpace(d.IP) == ""
}

// dnsFromSpec собирает DNS-сервер sing-box (формат 1.12+).
func dnsFromSpec(d DNSSpec) (map[string]any, error) {
	typ := strings.ToLower(strings.TrimSpace(d.Type))
	if typ == "" || typ == "doh" {
		typ = "https"
	}
	if typ == "dot" {
		typ = "tls"
	}
	if typ == "doq" {
		typ = "quic"
	}
	switch typ {
	case "https", "tls", "quic", "h3", "udp", "tcp":
	default:
		return nil, fmt.Errorf("неизвестный тип DNS %q", d.Type)
	}
	raw := strings.TrimSpace(d.Server)
	ip := strings.TrimSpace(d.IP)
	host, path, port := raw, "", 0
	if strings.Contains(raw, "://") {
		u, err := url.Parse(raw)
		if err != nil || u.Hostname() == "" {
			return nil, fmt.Errorf("неверный адрес DNS %q", raw)
		}
		host = u.Hostname()
		if u.Port() != "" {
			port, _ = strconv.Atoi(u.Port())
		}
		if u.Path != "" && u.Path != "/" {
			path = u.Path
		}
	} else if h, p, err := net.SplitHostPort(raw); err == nil {
		host = h
		port, _ = strconv.Atoi(p)
	}
	if host == "" {
		host = ip
	}
	if host == "" {
		return nil, errors.New("не указан адрес DNS")
	}
	if ip != "" {
		if _, err := netip.ParseAddr(ip); err != nil {
			return nil, fmt.Errorf("неверный IP DNS %q", ip)
		}
	}
	s := map[string]any{"type": typ}
	isIP := net.ParseIP(host) != nil
	switch {
	case ip != "":
		s["server"] = ip
	default:
		s["server"] = host
	}
	if port > 0 {
		s["server_port"] = port
	}
	if typ == "https" || typ == "h3" {
		if path != "" && path != "/dns-query" {
			s["path"] = path
		}
	}
	if typ == "https" || typ == "tls" || typ == "quic" || typ == "h3" {
		if !isIP {
			s["tls"] = map[string]any{"enabled": true, "server_name": host}
		}
	}
	if ip == "" && !isIP {
		s["domain_resolver"] = "local"
	}
	return s, nil
}

// ValidateDNSSpec — "" если DNS указан верно, иначе текст ошибки (для формы в приложении).
func ValidateDNSSpec(typ, server, ip string) string {
	if _, err := dnsFromSpec(DNSSpec{Type: typ, Server: server, IP: ip}); err != nil {
		return err.Error()
	}
	return ""
}

type userRuleSet struct {
	suffix, full, keyword, regex, cidr []string
	ports                              []portSpec
}

// portSpec — порт или диапазон; network: "" (любой), "tcp" или "udp".
type portSpec struct {
	network string
	port    int
	rng     string // "27000:27100"
}

func (s *userRuleSet) empty() bool {
	return len(s.suffix)+len(s.full)+len(s.keyword)+len(s.regex)+len(s.cidr) == 0
}

// portRules — отдельные правила для портов (по одному на сеть), не смешиваются с доменами.
func (s *userRuleSet) portRules(extra map[string]any) []any {
	byNet := map[string]map[string]any{}
	var order []string
	for _, p := range s.ports {
		r, ok := byNet[p.network]
		if !ok {
			r = map[string]any{}
			if p.network != "" {
				r["network"] = p.network
			}
			for k, v := range extra {
				r[k] = v
			}
			byNet[p.network] = r
			order = append(order, p.network)
		}
		if p.rng != "" {
			list, _ := r["port_range"].([]string)
			r["port_range"] = append(list, p.rng)
		} else {
			list, _ := r["port"].([]int)
			r["port"] = append(list, p.port)
		}
	}
	out := make([]any, 0, len(order))
	for _, n := range order {
		out = append(out, byNet[n])
	}
	return out
}

// parsePort разбирает «443» или «27000-27100».
func parsePort(v string) (int, string, bool) {
	v = strings.TrimSpace(v)
	if a, b, ok := strings.Cut(v, "-"); ok {
		x, e1 := strconv.Atoi(strings.TrimSpace(a))
		y, e2 := strconv.Atoi(strings.TrimSpace(b))
		if e1 != nil || e2 != nil || x < 1 || y > 65535 || x > y {
			return 0, "", false
		}
		return 0, fmt.Sprintf("%d:%d", x, y), true
	}
	n, err := strconv.Atoi(v)
	if err != nil || n < 1 || n > 65535 {
		return 0, "", false
	}
	return n, "", true
}

// rule — правило sing-box для этих значений (nil, если пусто).
func (s *userRuleSet) rule(extra map[string]any) map[string]any {
	if s.empty() {
		return nil
	}
	r := map[string]any{}
	if len(s.suffix) > 0 {
		r["domain_suffix"] = s.suffix
	}
	if len(s.full) > 0 {
		r["domain"] = s.full
	}
	if len(s.keyword) > 0 {
		r["domain_keyword"] = s.keyword
	}
	if len(s.regex) > 0 {
		r["domain_regex"] = s.regex
	}
	if len(s.cidr) > 0 {
		r["ip_cidr"] = s.cidr
	}
	for k, v := range extra {
		r[k] = v
	}
	return r
}

// domainOnly — то же без IP (для DNS-правил).
func (s *userRuleSet) domainOnly() *userRuleSet {
	return &userRuleSet{suffix: s.suffix, full: s.full, keyword: s.keyword, regex: s.regex}
}

// groupUserRules раскладывает свои правила по действиям; неверные пропускает с ошибкой.
func groupUserRules(list []UserRule) (map[string]*userRuleSet, []string) {
	out := map[string]*userRuleSet{"proxy": {}, "direct": {}, "block": {}}
	var bad []string
	for _, r := range list {
		set, ok := out[r.Action]
		if !ok {
			bad = append(bad, r.Value+": неизвестное действие")
			continue
		}
		v := strings.TrimSpace(r.Value)
		low := strings.ToLower(v)
		switch {
		case v == "":
			continue
		case strings.HasPrefix(low, "port:") || strings.HasPrefix(low, "udp:") || strings.HasPrefix(low, "tcp:"):
			network, rest, _ := strings.Cut(low, ":")
			if network == "port" {
				network = ""
			}
			n, rng, ok := parsePort(rest)
			if !ok {
				bad = append(bad, v+": неверный порт (1–65535 или 1000-2000)")
				continue
			}
			set.ports = append(set.ports, portSpec{network: network, port: n, rng: rng})
		case strings.HasPrefix(low, "keyword:"):
			set.keyword = append(set.keyword, strings.TrimSpace(v[len("keyword:"):]))
		case strings.HasPrefix(low, "regexp:"):
			set.regex = append(set.regex, strings.TrimSpace(v[len("regexp:"):]))
		case strings.HasPrefix(low, "full:"):
			set.full = append(set.full, strings.ToLower(strings.TrimSpace(v[len("full:"):])))
		case strings.HasPrefix(low, "domain:"):
			set.suffix = append(set.suffix, cleanDomain(v[len("domain:"):]))
		default:
			if p, err := netip.ParsePrefix(v); err == nil {
				set.cidr = append(set.cidr, p.Masked().String())
			} else if a, err := netip.ParseAddr(v); err == nil {
				set.cidr = append(set.cidr, netip.PrefixFrom(a, a.BitLen()).String())
			} else if d := cleanDomain(v); d != "" {
				set.suffix = append(set.suffix, d)
			} else {
				bad = append(bad, v+": не домен и не IP")
			}
		}
	}
	return out, bad
}

// ValidateUserRule — "" если правило понятно ядру.
func ValidateUserRule(value string) string {
	_, bad := groupUserRules([]UserRule{{Value: value, Action: "proxy"}})
	if len(bad) > 0 {
		return bad[0]
	}
	return ""
}

// CustomList — свой список «Через VPN»: название и домены, IP или подсети (как в своих правилах).
type CustomList struct {
	Name  string   `json:"name"`
	Items []string `json:"items"`
}

func customListRules(lists []CustomList) []UserRule {
	var out []UserRule
	for _, l := range lists {
		for _, it := range l.Items {
			out = append(out, UserRule{Value: it, Action: "proxy"})
		}
	}
	return out
}

// customListMatch — название списка, в который входит домен ("" — ни в один).
func customListMatch(lists []CustomList, domain string) string {
	for _, l := range lists {
		if userMatch(customListRules([]CustomList{l}), domain) == "proxy" {
			return l.Name
		}
	}
	return ""
}
