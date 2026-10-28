package apexcore

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestAdvancedRouting(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts := map[string]any{
		"mode":     "smart",
		"rulesDir": rulesDirForTest(),
		"userRules": []map[string]string{
			{"value": "example.org", "action": "proxy"},
			{"value": "keyword:casino", "action": "block"},
			{"value": "full:bank.ru", "action": "direct"},
			{"value": "10.20.0.0/16", "action": "direct"},
			{"value": "8.8.8.8", "action": "proxy"},
		},
		"domainStrategy": "ip_if_non_match",
		"fakeDns":        true,
		"remoteDns":      map[string]string{"type": "tls", "server": "dns.google", "ip": "8.8.8.8"},
		"directDns":      map[string]string{"type": "https", "server": "https://dns.yandex.ru/dns-query", "ip": "77.88.8.8"},
	}
	raw, _ := json.Marshal(opts)
	cfg := checkAll(t, nodes, string(raw))
	for _, want := range []string{`"fakeip"`, `"action": "resolve"`, `"8.8.8.8/32"`, `"server_name": "dns.google"`, `"tag": "home"`, `"casino"`} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("missing %s in config", want)
		}
	}
	opts["domainStrategy"] = "ip_on_demand"
	opts["remoteDns"] = map[string]string{"type": "udp", "server": "1.1.1.1"}
	raw, _ = json.Marshal(opts)
	checkAll(t, nodes, string(raw))

	for _, c := range []struct {
		typ, server, ip string
		ok              bool
	}{
		{"https", "https://cloudflare-dns.com/dns-query", "1.1.1.1", true},
		{"tls", "dns.google", "", true},
		{"quic", "dns.adguard-dns.com", "", true},
		{"udp", "8.8.8.8", "", true},
		{"https", "", "", false},
		{"weird", "1.1.1.1", "", false},
		{"https", "dns.google", "not-an-ip", false},
	} {
		if got := ValidateDNSSpec(c.typ, c.server, c.ip) == ""; got != c.ok {
			t.Errorf("%v: valid=%v", c, got)
		}
	}
	if ValidateUserRule("exa mple") == "" || ValidateUserRule("10.0.0.0/8") != "" || ValidateUserRule("keyword:x") != "" {
		t.Error("rule validation")
	}
	out, _ := MatchRoute(string(raw), "www.example.org")
	if !strings.Contains(out, `"proxy"`) || !strings.Contains(out, "Своё правило") {
		t.Fatalf("site check: %s", out)
	}
}
