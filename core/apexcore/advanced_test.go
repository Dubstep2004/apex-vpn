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

func TestPortRulesAndWarp(t *testing.T) {
	if e := ValidateUserRule("udp:27000-27100"); e != "" {
		t.Fatal(e)
	}
	if e := ValidateUserRule("port:70000"); e == "" {
		t.Fatal("порт 70000 должен быть ошибкой")
	}
	kp, err := WarpKeypair()
	if err != nil {
		t.Fatal(err)
	}
	priv, _, _ := strings.Cut(kp, " ")
	resp := `{"config":{"client_id":"AQID","peers":[{"public_key":"bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=","endpoint":{"v4":"162.159.192.7:0","host":"engage.cloudflareclient.com:2408"}}],"interface":{"addresses":{"v4":"172.16.0.2","v6":"2606:4700:110:8a36::1"}}}}`
	wj, err := WarpParse(resp, priv)
	if err != nil {
		t.Fatal(err)
	}
	var w WarpConfig
	_ = json.Unmarshal([]byte(wj), &w)
	if w.Host != "162.159.192.7" || w.Port != 2408 || len(w.Reserved) != 3 || w.Reserved[2] != 3 {
		t.Fatalf("WARP разобран неверно: %+v", w)
	}
	opts, _ := json.Marshal(map[string]any{
		"userRules": []map[string]string{{"value": "udp:27000-27100", "action": "direct"}, {"value": "tcp:25565", "action": "proxy"}},
		"warp":      w,
	})
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"27000:27100"`) || !strings.Contains(cfg, `"tag": "warp"`) || !strings.Contains(cfg, `"final": "warp"`) {
		t.Fatalf("нет правил по портам или WARP:\n%s", cfg)
	}
}

func TestAdsCounterDNS(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts, _ := json.Marshal(map[string]any{"adBlock": true, "rulesDir": rulesDirForTest(), "adsDnsPort": 45123})
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"adblock"`) || !strings.Contains(cfg, `45123`) {
		t.Fatalf("нет DNS счётчика рекламы:\n%s", cfg)
	}
}

func TestCustomLists(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	opts, _ := json.Marshal(map[string]any{
		"mode":        "selected",
		"services":    []string{"netflix", "figma"},
		"customLists": []map[string]any{{"name": "Работа", "items": []string{"jira.example.com", "10.8.0.0/16", "keyword:corp"}}},
	})
	cfg := checkAll(t, nodes, string(opts))
	if !strings.Contains(cfg, `"jira.example.com"`) || !strings.Contains(cfg, `"10.8.0.0/16"`) || !strings.Contains(cfg, `"nflxvideo.net"`) {
		t.Fatalf("нет своих списков или новых сервисов:\n%s", cfg)
	}
	out, _ := MatchRoute(string(opts), "jira.example.com")
	if !strings.Contains(out, "Работа") {
		t.Fatalf("проверка сайта: %s", out)
	}
}

func TestStableAuto(t *testing.T) {
	got := stableAuto([]string{"a", "ru", "b", "c"}, []string{"ru"}, []string{"c", "a"})
	if strings.Join(got, ",") != "c,a,b" {
		t.Fatalf("got %v", got)
	}
	if got := stableAuto([]string{"ru"}, []string{"ru"}, nil); len(got) != 1 {
		t.Fatalf("all excluded must keep original: %v", got)
	}
}

func TestAllDNSProviders(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	for id := range dnsServers {
		opts, _ := json.Marshal(map[string]any{"dns": id, "findProcess": true})
		cfg := checkAll(t, nodes, string(opts))
		if !strings.Contains(cfg, dnsServers[id]["server"].(string)) {
			t.Fatalf("%s: нет сервера DNS в конфиге", id)
		}
	}
}

func TestBlockQUIC(t *testing.T) {
	nodes := parseNodes(t, "vless://"+testUUID+"@1.2.3.4:443?type=tcp&security=tls&sni=a.example.com#A")
	for _, on := range []bool{false, true} {
		raw, _ := json.Marshal(map[string]any{"mode": "global", "blockQuic": on})
		cfg := checkAll(t, nodes, string(raw))
		if got := strings.Contains(cfg, `"protocol": "quic"`); got != on {
			t.Fatalf("blockQuic=%v: правило QUIC есть=%v", on, got)
		}
	}
}
