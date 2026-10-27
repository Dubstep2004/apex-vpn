package apexcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// AutoTag is the outbound that picks the fastest server automatically.
const AutoTag = "auto"

const (
	ModeSmart    = "smart"    // российское напрямую, остальное через VPN
	ModeAll      = "all"      // весь трафик через VPN
	ModeSelected = "selected" // через VPN только выбранные сервисы и свои сайты
)

const (
	fileGeoipRU  = "geoip-ru.srs"
	fileGeositeRU = "geosite-category-ru.srs"
	fileAds      = "geosite-category-ads-all.srs"
)

type shareOptions struct {
	Enabled  bool   `json:"enabled"`
	Port     int    `json:"port"`
	Username string `json:"username"`
	Password string `json:"password"`
}

type routeOptions struct {
	Mode          string       `json:"mode"`
	Services      []string     `json:"services"`      // включённые сервисы (через VPN)
	CustomDomains []string     `json:"customDomains"` // свои сайты — всегда через VPN
	DirectDomains []string     `json:"directDomains"` // свои сайты — всегда напрямую
	RulesDir      string       `json:"rulesDir"`      // папка с базами .srs
	DNS           string       `json:"dns"`           // cloudflare | google | quad9 | adguard | custom
	CustomDNS     string       `json:"customDns"`     // https://host/dns-query
	Kids          bool         `json:"kids"`          // семейный DNS для всех запросов
	AdBlock       bool         `json:"adBlock"`       // блокировка рекламы и трекеров
	IPv6          bool         `json:"ipv6"`
	Profile       string       `json:"profile"`       // normal | gaming | calls | battery
	Share         shareOptions `json:"share"`         // раздача на компьютер
}

// профиль подключения: как часто проверять серверы и рвать ли соединения при смене
type profile struct {
	interval  string
	tolerance int
	interrupt bool
}

var profiles = map[string]profile{
	"normal":  {"3m", 80, true},
	"gaming":  {"1m", 25, false},  // минимальный пинг, игры не обрываются
	"calls":   {"10m", 300, false}, // сервер «залипает», звонки не рвутся
	"battery": {"30m", 150, false}, // редкие проверки — меньше расход батареи
}

var dnsServers = map[string]map[string]any{
	"cloudflare": {"type": "https", "server": "1.1.1.1"},
	"google":     {"type": "https", "server": "8.8.8.8"},
	"quad9":      {"type": "https", "server": "9.9.9.9"},
	"adguard":    {"type": "https", "server": "dns.adguard-dns.com", "domain_resolver": "local"},
}

var familyDNS = map[string]any{"type": "https", "server": "family.adguard-dns.com", "domain_resolver": "local"}

func parseOptions(optionsJSON string) (routeOptions, error) {
	opts := routeOptions{Mode: ModeSmart, DNS: "cloudflare", Profile: "normal"}
	if strings.TrimSpace(optionsJSON) != "" {
		if err := json.Unmarshal([]byte(optionsJSON), &opts); err != nil {
			return opts, fmt.Errorf("неверные настройки: %w", err)
		}
	}
	switch opts.Mode {
	case ModeSmart, ModeAll, ModeSelected:
	default:
		opts.Mode = ModeSmart
	}
	if _, ok := profiles[opts.Profile]; !ok {
		opts.Profile = "normal"
	}
	return opts, nil
}

func (o routeOptions) rulesFile(name string) string {
	if o.RulesDir == "" {
		return ""
	}
	p := filepath.Join(o.RulesDir, name)
	if _, err := os.Stat(p); err != nil {
		return ""
	}
	return p
}

// BuildConfig собирает полный конфиг sing-box.
//   nodesJSON   — массив "nodes" из ParseSubscription
//   selectedTag — "auto" или тег сервера
//   optionsJSON — настройки (см. routeOptions), можно ""
func BuildConfig(nodesJSON string, selectedTag string, optionsJSON string) (string, error) {
	var nodes []Node
	if err := json.Unmarshal([]byte(nodesJSON), &nodes); err != nil {
		return "", fmt.Errorf("неверный список серверов: %w", err)
	}
	opts, err := parseOptions(optionsJSON)
	if err != nil {
		return "", err
	}
	prof := profiles[opts.Profile]

	tags := make([]string, 0, len(nodes))
	found := selectedTag == AutoTag
	var proxyOutbounds, endpoints []any
	for _, n := range nodes {
		if n.Outbound == nil || n.Tag == "" {
			continue
		}
		n.Outbound["tag"] = n.Tag
		tags = append(tags, n.Tag)
		if n.Tag == selectedTag {
			found = true
		}
		if n.Endpoint {
			endpoints = append(endpoints, n.Outbound)
		} else {
			proxyOutbounds = append(proxyOutbounds, n.Outbound)
		}
	}
	if len(tags) == 0 {
		return "", errors.New("нет серверов")
	}
	if !found {
		selectedTag = AutoTag
	}

	outbounds := []any{
		map[string]any{
			"type": "selector", "tag": "proxy",
			"outbounds":                   append([]string{AutoTag}, tags...),
			"default":                     selectedTag,
			"interrupt_exist_connections": prof.interrupt,
		},
		map[string]any{
			"type": "urltest", "tag": AutoTag,
			"outbounds":                   tags,
			"url":                         "https://www.gstatic.com/generate_204",
			"interval":                    prof.interval,
			"tolerance":                   prof.tolerance,
			"interrupt_exist_connections": prof.interrupt,
		},
	}
	outbounds = append(outbounds, proxyOutbounds...)
	outbounds = append(outbounds, map[string]any{"type": "direct", "tag": "direct"})

	// ── сервисы ──────────────────────────────────────────────────────────
	enabled := map[string]bool{}
	for _, id := range opts.Services {
		enabled[id] = true
	}
	ids := make([]string, 0, len(services))
	for id := range services {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	var onDomains, offDomains, onCIDR, offCIDR, onUDP []string
	for _, id := range ids {
		s := services[id]
		if enabled[id] {
			onDomains = append(onDomains, s.domains...)
			onCIDR = append(onCIDR, s.cidrs...)
			if s.udpPorts != "" {
				onUDP = append(onUDP, s.udpPorts)
			}
		} else {
			offDomains = append(offDomains, s.domains...)
			offCIDR = append(offCIDR, s.cidrs...)
		}
	}
	custom := cleanDomains(opts.CustomDomains)
	direct := cleanDomains(opts.DirectDomains)

	// ── базы правил ──────────────────────────────────────────────────────
	var ruleSets []any
	geositeRU, geoipRU, ads := "", "", ""
	if opts.Mode == ModeSmart {
		geositeRU, geoipRU = opts.rulesFile(fileGeositeRU), opts.rulesFile(fileGeoipRU)
	}
	if opts.AdBlock {
		ads = opts.rulesFile(fileAds)
	}
	addSet := func(tag, path string) {
		if path != "" {
			ruleSets = append(ruleSets, map[string]any{"type": "local", "tag": tag, "format": "binary", "path": path})
		}
	}
	addSet("geosite-ru", geositeRU)
	addSet("geoip-ru", geoipRU)
	addSet("ads", ads)

	// ── правила маршрутизации (сверху вниз, первое совпадение) ───────────
	rules := []any{
		map[string]any{"action": "sniff"},
		map[string]any{"protocol": "dns", "action": "hijack-dns"},
		map[string]any{"ip_is_private": true, "outbound": "direct"},
	}
	if ads != "" {
		rules = append(rules, map[string]any{"rule_set": "ads", "action": "reject"})
	}
	if len(custom) > 0 {
		rules = append(rules, map[string]any{"domain_suffix": custom, "outbound": "proxy"})
	}
	if len(direct) > 0 {
		rules = append(rules, map[string]any{"domain_suffix": direct, "outbound": "direct"})
	}
	if r := addrRule(onDomains, onCIDR, "proxy"); r != nil {
		rules = append(rules, r)
	}
	if len(onUDP) > 0 {
		rules = append(rules, map[string]any{"network": "udp", "port_range": onUDP, "outbound": "proxy"})
	}
	// Выключенные сервисы идут напрямую во всех режимах
	if r := addrRule(offDomains, offCIDR, "direct"); r != nil {
		rules = append(rules, r)
	}
	final := "proxy"
	switch opts.Mode {
	case ModeSmart:
		rules = append(rules, map[string]any{"domain_suffix": ruDomainSuffixes, "outbound": "direct"})
		var sets []string
		if geositeRU != "" {
			sets = append(sets, "geosite-ru")
		}
		if geoipRU != "" {
			sets = append(sets, "geoip-ru")
		}
		if len(sets) > 0 {
			rules = append(rules, map[string]any{"rule_set": sets, "outbound": "direct"})
		}
	case ModeSelected:
		final = "direct"
	}

	// ── DNS ───────────────────────────────────────────────────────────────
	remote := dnsServers[opts.DNS]
	if opts.DNS == "custom" {
		if c, err := customDNSServer(opts.CustomDNS); err == nil {
			remote = c
		}
	}
	if opts.Kids {
		remote = familyDNS
	}
	if remote == nil {
		remote = dnsServers["cloudflare"]
	}
	remoteServer := map[string]any{"tag": "remote", "detour": "proxy"}
	for k, v := range remote {
		remoteServer[k] = v
	}
	servers := []any{remoteServer, map[string]any{"type": "local", "tag": "local"}}
	// «Прямые» запросы: системный DNS, а в детском режиме — семейный напрямую
	directDNS := "local"
	if opts.Kids {
		fam := map[string]any{"tag": "family-direct"}
		for k, v := range familyDNS {
			fam[k] = v
		}
		servers = append(servers, fam)
		directDNS = "family-direct"
	}
	var dnsRules []any
	if ads != "" {
		dnsRules = append(dnsRules, map[string]any{"rule_set": "ads", "action": "reject"})
	}
	if len(custom) > 0 || len(onDomains) > 0 {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": append(append([]string{}, custom...), onDomains...), "server": "remote"})
	}
	if len(direct) > 0 || len(offDomains) > 0 {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": append(append([]string{}, direct...), offDomains...), "server": directDNS})
	}
	if opts.Mode == ModeSmart {
		dnsRules = append(dnsRules, map[string]any{"domain_suffix": ruDomainSuffixes, "server": directDNS})
		if geositeRU != "" {
			dnsRules = append(dnsRules, map[string]any{"rule_set": "geosite-ru", "server": directDNS})
		}
	}
	dnsFinal := "remote"
	if opts.Mode == ModeSelected {
		dnsFinal = directDNS
	}
	strategy := "ipv4_only"
	if opts.IPv6 {
		strategy = "prefer_ipv4"
	}
	dnsSection := map[string]any{"servers": servers, "final": dnsFinal, "strategy": strategy}
	if len(dnsRules) > 0 {
		dnsSection["rules"] = dnsRules
	}

	route := map[string]any{
		"rules":                   rules,
		"final":                   final,
		"auto_detect_interface":   true,
		"default_domain_resolver": "local",
	}
	if len(ruleSets) > 0 {
		route["rule_set"] = ruleSets
	}

	inbounds := []any{map[string]any{
		"type": "tun", "tag": "tun-in",
		"address":      []string{"172.19.0.1/30", "fdfe:dcba:9876::1/126"},
		"mtu":          9000,
		"auto_route":   true,
		"strict_route": true,
		"stack":        "mixed",
	}}
	if opts.Share.Enabled {
		port := opts.Share.Port
		if port <= 0 || port > 65535 {
			port = 2080
		}
		in := map[string]any{"type": "mixed", "tag": "share-in", "listen": "0.0.0.0", "listen_port": port}
		if opts.Share.Username != "" && opts.Share.Password != "" {
			in["users"] = []any{map[string]any{"username": opts.Share.Username, "password": opts.Share.Password}}
		}
		inbounds = append(inbounds, in)
	}

	cfg := map[string]any{
		"log":       map[string]any{"level": "warn", "timestamp": true},
		"dns":       dnsSection,
		"inbounds":  inbounds,
		"outbounds": outbounds,
		"route":     route,
	}
	if len(endpoints) > 0 {
		cfg["endpoints"] = endpoints
	}
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return "", err
	}
	return string(out), nil
}

// customDNSServer разбирает адрес DNS-over-HTTPS вида https://host[:port]/path.
func customDNSServer(raw string) (map[string]any, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, errors.New("пустой адрес")
	}
	if !strings.Contains(raw, "://") {
		raw = "https://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" || u.Hostname() == "" {
		return nil, errors.New("нужен адрес вида https://dns.example.com/dns-query")
	}
	s := map[string]any{"type": "https", "server": u.Hostname()}
	if p := u.Port(); p != "" {
		if n, err := strconv.Atoi(p); err == nil {
			s["server_port"] = n
		}
	}
	if u.Path != "" && u.Path != "/dns-query" {
		s["path"] = u.Path
	}
	if net.ParseIP(u.Hostname()) == nil {
		s["domain_resolver"] = "local"
	}
	return s, nil
}

// ValidateDNS возвращает "" если адрес DoH корректен, иначе текст ошибки.
func ValidateDNS(raw string) string {
	if _, err := customDNSServer(raw); err != nil {
		return err.Error()
	}
	return ""
}

func addrRule(domains, cidrs []string, outbound string) map[string]any {
	if len(domains) == 0 && len(cidrs) == 0 {
		return nil
	}
	r := map[string]any{"outbound": outbound}
	if len(domains) > 0 {
		r["domain_suffix"] = domains
	}
	if len(cidrs) > 0 {
		r["ip_cidr"] = cidrs
	}
	return r
}

// cleanDomains приводит «https://www.Site.com/path» к «site.com».
func cleanDomains(in []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, d := range in {
		if d = cleanDomain(d); d == "" || seen[d] {
			continue
		}
		seen[d] = true
		out = append(out, d)
	}
	return out
}

func cleanDomain(d string) string {
	d = strings.ToLower(strings.TrimSpace(d))
	if i := strings.Index(d, "://"); i >= 0 {
		d = d[i+3:]
	}
	if i := strings.IndexAny(d, "/?#:"); i >= 0 {
		d = d[:i]
	}
	d = strings.TrimPrefix(strings.Trim(d, "."), "www.")
	if !strings.Contains(d, ".") {
		return ""
	}
	return d
}
