package apexcore

import (
	"encoding/base64"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// ─── Категории протоколов ───────────────────────────────────────────────

// categoryNames — тип outbound sing-box → категория для экрана выбора сервера.
var categoryNames = map[string]string{
	"vless":       "VLESS",
	"vmess":       "VMess",
	"trojan":      "Trojan",
	"shadowsocks": "Shadowsocks",
	"hysteria2":   "Hysteria 2",
	"hysteria":    "Hysteria",
	"tuic":        "TUIC",
	"wireguard":   "WireGuard",
	"anytls":      "AnyTLS",
	"naive":       "NaiveProxy",
	"socks":       "SOCKS",
	"http":        "HTTP",
	"ssh":         "SSH",
	"shadowtls":   "ShadowTLS",
}

// udpTypes — протоколы поверх UDP/QUIC: их задержку нельзя измерить TCP-соединением.
var udpTypes = map[string]bool{"hysteria2": true, "hysteria": true, "tuic": true, "wireguard": true}

// fillMeta заполняет категорию, описание подключения и признак UDP.
func fillMeta(n *Node) {
	if n.Outbound == nil && n.Xray != nil {
		if n.Category == "" {
			n.Category = categoryOf(n)
		}
		if n.Detail == "" {
			n.Detail = xrayDetail(n.Xray)
		}
		stream, _ := n.Xray["streamSettings"].(map[string]any)
		n.UDP = stream["network"] == "kcp"
		return
	}
	if n.Category == "" {
		n.Category = categoryOf(n)
	}
	if n.Detail == "" {
		n.Detail = detailOf(n)
	}
	n.UDP = udpTypes[n.Type]
	if n.Type == "naive" {
		if q, _ := n.Outbound["quic"].(bool); q {
			n.UDP = true
		}
	}
}

func categoryOf(n *Node) string {
	// Shadowsocks через ShadowTLS — отдельная категория
	if n.Type == "shadowsocks" && len(n.Extra) > 0 {
		if t, _ := n.Extra[0]["type"].(string); t == "shadowtls" {
			return "ShadowTLS"
		}
	}
	if c, ok := categoryNames[n.Type]; ok {
		return c
	}
	return strings.ToUpper(n.Type)
}

var transportNames = map[string]string{
	"ws": "WS", "grpc": "gRPC", "http": "HTTP/2", "httpupgrade": "HTTPUpgrade", "quic": "QUIC",
}

// detailOf — короткое описание: «Reality · Vision · TCP», «TLS · WS», «2022-blake3 · obfs».
func detailOf(n *Node) string {
	ob := n.Outbound
	var parts []string
	tls, _ := ob["tls"].(map[string]any)
	switch n.Type {
	case "vless", "vmess", "trojan", "anytls", "http":
		if tls != nil && tls["enabled"] == true {
			if r, _ := tls["reality"].(map[string]any); r != nil && r["enabled"] == true {
				parts = append(parts, "Reality")
			} else {
				parts = append(parts, "TLS")
			}
		}
		if f, _ := ob["flow"].(string); f != "" {
			parts = append(parts, "Vision")
		}
		if n.Type != "anytls" && n.Type != "http" {
			if t, _ := ob["transport"].(map[string]any); t != nil {
				typ, _ := t["type"].(string)
				if name, ok := transportNames[typ]; ok {
					if typ == "http" {
						if _, hasMethod := t["method"]; hasMethod {
							name = "TCP (HTTP)"
						}
					}
					parts = append(parts, name)
				}
			} else {
				parts = append(parts, "TCP")
			}
		}
	case "shadowsocks":
		if m, _ := ob["method"].(string); m != "" {
			parts = append(parts, strings.TrimPrefix(m, "2022-"))
		}
		if p, _ := ob["plugin"].(string); p != "" {
			parts = append(parts, p)
		}
		if len(n.Extra) > 0 {
			if v, ok := n.Extra[0]["version"].(float64); ok {
				parts = append(parts, fmt.Sprintf("ShadowTLS v%d", int(v)))
			} else if v, ok := n.Extra[0]["version"].(int); ok {
				parts = append(parts, fmt.Sprintf("ShadowTLS v%d", v))
			}
		}
	case "hysteria2":
		parts = append(parts, "QUIC")
		if o, _ := ob["obfs"].(map[string]any); o != nil {
			parts = append(parts, "obfs")
		}
		if _, ok := ob["server_ports"]; ok {
			parts = append(parts, "смена портов")
		}
	case "hysteria":
		parts = append(parts, "QUIC")
		if o, _ := ob["obfs"].(string); o != "" {
			parts = append(parts, "obfs")
		}
	case "tuic":
		parts = append(parts, "QUIC")
		if c, _ := ob["congestion_control"].(string); c != "" {
			parts = append(parts, c)
		}
	case "wireguard":
		parts = append(parts, "UDP")
	case "naive":
		if q, _ := ob["quic"].(bool); q {
			parts = append(parts, "QUIC")
		} else {
			parts = append(parts, "HTTPS")
		}
	case "socks":
		if v, _ := ob["version"].(string); v != "" {
			parts = append(parts, "v"+v)
		}
	case "ssh":
		if _, ok := ob["private_key"]; ok {
			parts = append(parts, "ключ")
		} else {
			parts = append(parts, "пароль")
		}
	}
	if n.Type == "http" && len(parts) == 0 {
		parts = append(parts, "без TLS")
	}
	if len(n.Extra) > 0 && n.Type != "shadowsocks" {
		parts = append(parts, "цепочка")
	}
	return strings.Join(parts, " · ")
}

// ─── Hysteria (v1) ──────────────────────────────────────────────────────

// hysteria://host:port?auth=…&peer=sni&insecure=1&upmbps=50&downmbps=100&obfsParam=…&alpn=h3#name
func parseHysteria(link string) (*Node, error) {
	link, portSpec := splitMultiPort(link)
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("hysteria: %w", err)
	}
	q := u.Query()
	name := fragmentName(u)
	if p := strings.ToLower(q.Get("protocol")); p != "" && p != "udp" {
		return nil, fmt.Errorf("hysteria %s: режим %s не поддерживается", name, p)
	}
	server := u.Hostname()
	port, err := strconv.Atoi(u.Port())
	if err != nil || port <= 0 {
		return nil, fmt.Errorf("hysteria %s: неверный порт", name)
	}
	ob := map[string]any{"type": "hysteria", "server": server}
	if portSpec == "" {
		portSpec = q.Get("mport")
	}
	applyPorts(ob, port, portSpec)
	if server == "" {
		return nil, fmt.Errorf("hysteria %s: нет адреса", name)
	}
	up, _ := strconv.Atoi(firstNonEmpty(q.Get("upmbps"), q.Get("up")))
	down, _ := strconv.Atoi(firstNonEmpty(q.Get("downmbps"), q.Get("down")))
	if up <= 0 {
		up = 50
	}
	if down <= 0 {
		down = 100
	}
	ob["up_mbps"] = up
	ob["down_mbps"] = down
	if a := firstNonEmpty(q.Get("auth"), q.Get("auth_str"), u.User.Username()); a != "" {
		ob["auth_str"] = a
	}
	if o := firstNonEmpty(q.Get("obfsParam"), q.Get("obfs-password")); o != "" {
		ob["obfs"] = o
	}
	ob["tls"] = tlsOptions("tls", firstNonEmpty(q.Get("peer"), q.Get("sni")), "", firstNonEmpty(q.Get("alpn"), "hysteria"), "", "", truthy(q.Get("insecure")), server)
	return &Node{Name: name, Type: "hysteria", Server: server, Port: port, Outbound: ob}, nil
}

// ─── SOCKS / HTTP ───────────────────────────────────────────────────────

// userPass достаёт логин и пароль; v2rayN кодирует «user:pass» в base64.
func userPass(u *url.URL) (string, string) {
	if u.User == nil {
		return "", ""
	}
	user := u.User.Username()
	pass, hasPass := u.User.Password()
	if !hasPass && user != "" {
		if dec, ok := decodeBase64(user); ok {
			if a, b, ok := strings.Cut(dec, ":"); ok {
				return a, b
			}
		}
	}
	return user, pass
}

// socks://base64(user:pass)@host:port#name, socks5://user:pass@host:port, socks4://host:port
func parseSOCKS(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("socks: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("socks: %w", err)
	}
	ob := map[string]any{"type": "socks", "server": server, "server_port": port}
	switch strings.ToLower(u.Scheme) {
	case "socks4":
		ob["version"] = "4"
	case "socks4a":
		ob["version"] = "4a"
	default:
		ob["version"] = "5"
	}
	if user, pass := userPass(u); user != "" {
		ob["username"] = user
		ob["password"] = pass
	}
	return &Node{Name: fragmentName(u), Type: "socks", Server: server, Port: port, Outbound: ob}, nil
}

// http://user:pass@host:port#name, https://… — HTTP-прокси (CONNECT), https — с TLS
func parseHTTPProxy(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("http: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("http: %w", err)
	}
	q := u.Query()
	ob := map[string]any{"type": "http", "server": server, "server_port": port}
	if user, pass := userPass(u); user != "" {
		ob["username"] = user
		ob["password"] = pass
	}
	if strings.EqualFold(u.Scheme, "https") || strings.EqualFold(q.Get("security"), "tls") {
		ob["tls"] = tlsOptions("tls", q.Get("sni"), q.Get("fp"), q.Get("alpn"), "", "", truthy(q.Get("insecure")) || truthy(q.Get("allowInsecure")), server)
	}
	return &Node{Name: fragmentName(u), Type: "http", Server: server, Port: port, Outbound: ob}, nil
}

// ─── SSH ────────────────────────────────────────────────────────────────

// ssh://user:pass@host:22?pk=<ключ, url- или base64>&hk=<host key>&passphrase=…#name
func parseSSH(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("ssh: %w", err)
	}
	server := u.Hostname()
	port := 22
	if p := u.Port(); p != "" {
		if port, err = strconv.Atoi(p); err != nil || port <= 0 || port > 65535 {
			return nil, fmt.Errorf("ssh: неверный порт %q", p)
		}
	}
	if server == "" {
		return nil, errors.New("ssh: нет адреса")
	}
	q := u.Query()
	name := fragmentName(u)
	ob := map[string]any{"type": "ssh", "server": server, "server_port": port}
	if u.User != nil {
		ob["user"] = u.User.Username()
		if p, ok := u.User.Password(); ok {
			ob["password"] = p
		}
	}
	if pk := firstNonEmpty(q.Get("pk"), q.Get("private_key"), q.Get("privateKey")); pk != "" {
		if !strings.Contains(pk, "PRIVATE KEY") {
			if dec, ok := decodeBase64(pk); ok && strings.Contains(dec, "PRIVATE KEY") {
				pk = dec
			}
		}
		ob["private_key"] = pk
	}
	if pp := q.Get("passphrase"); pp != "" {
		ob["private_key_passphrase"] = pp
	}
	if hk := splitList(firstNonEmpty(q.Get("hk"), q.Get("host_key"), q.Get("hostkey"))); len(hk) > 0 {
		ob["host_key"] = hk
	}
	if _, hasPass := ob["password"]; !hasPass {
		if _, hasKey := ob["private_key"]; !hasKey {
			return nil, fmt.Errorf("ssh %s: нет пароля или ключа", name)
		}
	}
	return &Node{Name: name, Type: "ssh", Server: server, Port: port, Outbound: ob}, nil
}

// ─── AnyTLS ─────────────────────────────────────────────────────────────

// anytls://password@host:port?sni=…&insecure=1&fp=…#name
func parseAnyTLS(link string) (*Node, error) {
	u, err := url.Parse(link)
	if err != nil {
		return nil, fmt.Errorf("anytls: %w", err)
	}
	server, port, err := hostPort(u)
	if err != nil {
		return nil, fmt.Errorf("anytls: %w", err)
	}
	q := u.Query()
	name := fragmentName(u)
	password := u.User.Username()
	if p, ok := u.User.Password(); ok {
		password += ":" + p
	}
	if password == "" {
		password = q.Get("password")
	}
	if password == "" {
		return nil, fmt.Errorf("anytls %s: нет пароля", name)
	}
	security := q.Get("security")
	if security == "" || security == "none" {
		security = "tls"
	}
	ob := map[string]any{
		"type": "anytls", "server": server, "server_port": port, "password": password,
		"tls": tlsOptions(security, firstNonEmpty(q.Get("sni"), q.Get("peer")), q.Get("fp"), q.Get("alpn"), q.Get("pbk"), q.Get("sid"), truthy(q.Get("insecure")) || truthy(q.Get("allowInsecure")), server),
	}
	return &Node{Name: name, Type: "anytls", Server: server, Port: port, Outbound: ob}, nil
}

// ─── NaiveProxy ─────────────────────────────────────────────────────────

// naive+https://user:pass@host:443?padding=1#name, naive+quic://…
func parseNaive(link string) (*Node, error) {
	scheme, rest, _ := strings.Cut(link, "://")
	u, err := url.Parse("https://" + rest)
	if err != nil {
		return nil, fmt.Errorf("naive: %w", err)
	}
	server := u.Hostname()
	port := 443
	if p := u.Port(); p != "" {
		if port, err = strconv.Atoi(p); err != nil || port <= 0 || port > 65535 {
			return nil, fmt.Errorf("naive: неверный порт %q", p)
		}
	}
	if server == "" {
		return nil, errors.New("naive: нет адреса")
	}
	q := u.Query()
	ob := map[string]any{"type": "naive", "server": server, "server_port": port}
	if user, pass := userPass(u); user != "" {
		ob["username"] = user
		ob["password"] = pass
	}
	if strings.HasSuffix(strings.ToLower(scheme), "quic") {
		ob["quic"] = true
	}
	if ec := q.Get("extra-headers"); ec != "" {
		headers := map[string]any{}
		for _, h := range strings.Split(ec, "\r\n") {
			if k, v, ok := strings.Cut(h, ":"); ok {
				headers[strings.TrimSpace(k)] = strings.TrimSpace(v)
			}
		}
		if len(headers) > 0 {
			ob["extra_headers"] = headers
		}
	}
	ob["tls"] = tlsOptions("tls", q.Get("sni"), "", "", "", "", truthy(q.Get("insecure")), server)
	// uTLS у naive не используется — у него свой стек Chromium
	delete(ob["tls"].(map[string]any), "utls")
	return &Node{Name: fragmentName(u), Type: "naive", Server: server, Port: port, Outbound: ob}, nil
}

// ─── Плагины Shadowsocks (SIP003) ───────────────────────────────────────

// ssPlugin переводит «obfs-local;obfs=http;obfs-host=…» в plugin/plugin_opts sing-box.
// Ядро умеет obfs-local (simple-obfs) и v2ray-plugin.
func ssPlugin(raw string) (string, string, error) {
	name, opts, _ := strings.Cut(raw, ";")
	switch strings.ToLower(strings.TrimSpace(name)) {
	case "obfs-local", "simple-obfs", "obfs":
		return "obfs-local", opts, nil
	case "v2ray-plugin":
		return "v2ray-plugin", opts, nil
	default:
		return "", "", fmt.Errorf("плагин %s не поддерживается (есть obfs-local и v2ray-plugin)", name)
	}
}

// ─── ShadowsocksR ───────────────────────────────────────────────────────

func decodeSSRName(s string) string {
	for _, enc := range []*base64.Encoding{base64.RawURLEncoding, base64.URLEncoding, base64.StdEncoding, base64.RawStdEncoding} {
		if b, err := enc.DecodeString(s); err == nil {
			return string(b)
		}
	}
	return s
}

// ShadowsocksR удалён из sing-box — понятная ошибка вместо «неизвестный протокол».
func parseSSR(link string) (*Node, error) {
	name := ""
	if dec, ok := decodeBase64(strings.TrimPrefix(link, "ssr://")); ok {
		if _, q, ok := strings.Cut(dec, "/?"); ok {
			if v, _ := url.ParseQuery(q); v.Get("remarks") != "" {
				name = " " + decodeSSRName(v.Get("remarks"))
			}
		}
	}
	return nil, fmt.Errorf("ShadowsocksR%s: протокол устарел и не поддерживается ядром sing-box", name)
}

// splitMultiPort вынимает из ссылки список портов вида «443,20000-30000»
// (смена портов Hysteria), который url.Parse не принимает. Возвращает ссылку
// с одним портом и исходный список (пусто, если порт обычный).
func splitMultiPort(link string) (string, string) {
	i := strings.Index(link, "://")
	if i < 0 {
		return link, ""
	}
	start := i + 3
	end := len(link)
	if j := strings.IndexAny(link[start:], "/?#"); j >= 0 {
		end = start + j
	}
	authority := link[start:end]
	hostStart := strings.LastIndex(authority, "@") + 1
	hostPart := authority[hostStart:]
	colon := strings.LastIndex(hostPart, ":")
	// IPv6 без порта: двоеточие внутри [...]
	if colon < 0 || strings.HasPrefix(hostPart, "[") && colon < strings.Index(hostPart, "]") {
		return link, ""
	}
	ports := hostPart[colon+1:]
	if !strings.ContainsAny(ports, ",-") {
		return link, ""
	}
	first := strings.SplitN(strings.SplitN(ports, ",", 2)[0], "-", 2)[0]
	if _, err := strconv.Atoi(first); err != nil {
		return link, ""
	}
	clean := link[:start] + authority[:hostStart] + hostPart[:colon+1] + first + link[end:]
	return clean, ports
}

// applyPorts записывает server_port и, если портов несколько, server_ports.
func applyPorts(ob map[string]any, port int, spec string) {
	ob["server_port"] = port
	if spec == "" {
		return
	}
	var ports []string
	for _, p := range splitList(spec) {
		if !strings.Contains(p, "-") {
			// одиночный порт как диапазон из одного значения
			p = p + "-" + p
		}
		ports = append(ports, strings.ReplaceAll(p, "-", ":"))
	}
	ob["server_ports"] = ports
	delete(ob, "server_port")
}
