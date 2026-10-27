package apexcore

import (
	"encoding/base64"
	"encoding/json"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/experimental/libbox"
)

const (
	testUUID = "b831381d-6324-4d53-ad4f-8cda48b30811"
	testPBK  = "Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBrjqnAw"
)

func sampleLinks() []string {
	vmess := base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"VMess WS","add":"vm.example.com","port":"443","id":"` + testUUID + `","aid":"0","scy":"auto","net":"ws","type":"none","host":"vm.example.com","path":"/ws?ed=2048","tls":"tls","sni":"vm.example.com","fp":"chrome"}`))
	ss := base64.RawURLEncoding.EncodeToString([]byte("chacha20-ietf-poly1305:pass123"))
	return []string{
		"vless://" + testUUID + "@1.2.3.4:443?type=tcp&security=reality&pbk=" + testPBK + "&fp=chrome&sni=www.microsoft.com&sid=6ba85179e30d4fc2&flow=xtls-rprx-vision#%F0%9F%87%B3%F0%9F%87%B1%20Netherlands",
		"vless://" + testUUID + "@vl.example.com:443?type=grpc&serviceName=grpcsvc&security=tls&sni=vl.example.com&alpn=h2#gRPC",
		"vless://" + testUUID + "@vl.example.com:8080?type=ws&path=%2Fws&host=vl.example.com&security=none#WS%20plain",
		"vmess://" + vmess,
		"trojan://secret@tr.example.com:443?security=tls&type=tcp&sni=tr.example.com#Trojan",
		"ss://" + ss + "@5.6.7.8:8388#SS",
		"hysteria2://authpass@hy.example.com:443?sni=hy.example.com&obfs=salamander&obfs-password=x&insecure=1#Hy2",
		"tuic://" + testUUID + ":pw@tu.example.com:443?congestion_control=bbr&alpn=h3#TUIC",
		"vless://" + testUUID + "@x.example.com:443?type=xhttp&security=tls#XHTTP",
		"wireguard://abc@1.1.1.1:51820#WG",
	}
}

func TestParseBase64Subscription(t *testing.T) {
	body := base64.StdEncoding.EncodeToString([]byte(strings.Join(sampleLinks(), "\n")))
	out, err := ParseSubscription(body)
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	if err := json.Unmarshal([]byte(out), &res); err != nil {
		t.Fatal(err)
	}
	if len(res.Nodes) != 8 {
		t.Fatalf("want 8 nodes, got %d (skipped: %v)", len(res.Nodes), res.Skipped)
	}
	if len(res.Skipped) != 2 {
		t.Fatalf("want 2 skipped, got %v", res.Skipped)
	}
	if res.Nodes[0].Name != "🇳🇱 Netherlands" {
		t.Fatalf("bad name %q", res.Nodes[0].Name)
	}
	nodes, _ := json.Marshal(res.Nodes)

	for _, sel := range []string{AutoTag, "node-1", "node-4", "missing"} {
		cfg, err := BuildConfig(string(nodes), sel, "")
		if err != nil {
			t.Fatal(err)
		}
		if err := libbox.CheckConfig(cfg); err != nil {
			t.Fatalf("sing-box rejected config (%s): %v\n%s", sel, err, cfg)
		}
	}
}

func TestParsePlainAndSingBoxJSON(t *testing.T) {
	if _, err := ParseSubscription(strings.Join(sampleLinks()[:2], "\r\n")); err != nil {
		t.Fatal(err)
	}
	sb := `{"outbounds":[{"type":"selector","tag":"s","outbounds":["a"]},{"type":"vless","tag":"a","server":"1.2.3.4","server_port":443,"uuid":"` + testUUID + `"},{"type":"direct","tag":"direct"}]}`
	out, err := ParseSubscription(sb)
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	json.Unmarshal([]byte(out), &res)
	if len(res.Nodes) != 1 || res.Nodes[0].Name != "a" {
		t.Fatalf("unexpected %v", out)
	}
	nodes, _ := json.Marshal(res.Nodes)
	cfg, err := BuildConfig(string(nodes), AutoTag, "")
	if err != nil {
		t.Fatal(err)
	}
	if err := libbox.CheckConfig(cfg); err != nil {
		t.Fatal(err)
	}
}

func TestErrors(t *testing.T) {
	if _, err := ParseSubscription(""); err == nil {
		t.Fatal("empty must fail")
	}
	if _, err := ParseSubscription("<html>nope</html>"); err == nil {
		t.Fatal("html must fail")
	}
}

func TestModes(t *testing.T) {
	body := strings.Join(sampleLinks()[:3], "\n")
	out, err := ParseSubscription(body)
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	json.Unmarshal([]byte(out), &res)
	nodes, _ := json.Marshal(res.Nodes)
	rulesDir, _ := filepath.Abs("../../app/src/main/assets/rules")
	if _, err := os.Stat(filepath.Join(rulesDir, "geoip-ru.srs")); err != nil {
		rulesDir = os.Getenv("APEX_RULES_DIR")
	}
	cases := []map[string]any{
		{"mode": "smart", "services": []string{"youtube", "telegram", "discord"}, "customDomains": []string{"https://www.Twitch.tv/x", "example.org"}, "rulesDir": rulesDir, "dns": "adguard", "ipv6": true},
		{"mode": "all", "services": []string{"youtube"}, "dns": "google"},
		{"mode": "selected", "services": []string{"chatgpt", "claude", "cloudflare"}, "dns": "quad9"},
		{"mode": "selected"},
		{"mode": "bogus", "rulesDir": rulesDir},
	}
	for i, c := range cases {
		o, _ := json.Marshal(c)
		cfg, err := BuildConfig(string(nodes), AutoTag, string(o))
		if err != nil {
			t.Fatal(err)
		}
		if err := libbox.CheckConfig(cfg); err != nil {
			t.Fatalf("case %d rejected: %v\n%s", i, err, cfg)
		}
	}
	// Режим «Только выбранные» по умолчанию идёт напрямую
	o, _ := json.Marshal(map[string]any{"mode": "selected", "services": []string{"claude"}})
	cfg, _ := BuildConfig(string(nodes), AutoTag, string(o))
	var parsed struct {
		Route struct {
			Final string `json:"final"`
		} `json:"route"`
	}
	json.Unmarshal([]byte(cfg), &parsed)
	if parsed.Route.Final != "direct" {
		t.Fatalf("selected mode final = %s", parsed.Route.Final)
	}
}

func TestCleanDomains(t *testing.T) {
	got := cleanDomains([]string{"https://www.Twitch.tv/path?x=1", "twitch.tv", " .example.com. ", "nodot", "", "a.b:8080"})
	want := []string{"twitch.tv", "example.com", "a.b"}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Fatalf("got %v", got)
	}
}

const wgKey = "YNXtAzepDqRv9H52osJVDQnznT5AL11+tO1jyzQ+WFI="
const wgPub = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

func allOptions(rulesDir string) string {
	o, _ := json.Marshal(map[string]any{
		"mode": "smart", "services": []string{"youtube", "discord"},
		"customDomains": []string{"twitch.tv"}, "directDomains": []string{"https://www.kinopoisk.ru/film", "mysite.com"},
		"rulesDir": rulesDir, "dns": "custom", "customDns": "https://dns.example.com:8443/q",
		"kids": true, "adBlock": true, "ipv6": true, "profile": "gaming",
		"share": map[string]any{"enabled": true, "port": 2080, "username": "u", "password": "p"},
	})
	return string(o)
}

func rulesDirForTest() string {
	d, _ := filepath.Abs("../../app/src/main/assets/rules")
	if _, err := os.Stat(filepath.Join(d, "geoip-ru.srs")); err != nil {
		return os.Getenv("APEX_RULES_DIR")
	}
	return d
}

func TestWireGuardAndAllOptions(t *testing.T) {
	links := sampleLinks()[:2]
	links = append(links,
		"wireguard://"+url.QueryEscape(wgKey)+"@162.159.192.1:2408?publickey="+url.QueryEscape(wgPub)+"&address=172.16.0.2/32,2606:4700:110:8a36::1/128&reserved=1,2,3&mtu=1280#WARP",
		"awg://x@1.2.3.4:51820#AWG",
	)
	out, err := ParseSubscription(strings.Join(links, "\n"))
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	json.Unmarshal([]byte(out), &res)
	if len(res.Nodes) != 3 || !res.Nodes[2].Endpoint || len(res.Skipped) != 1 {
		t.Fatalf("unexpected parse: %s", out)
	}
	nodes, _ := json.Marshal(res.Nodes)
	for _, profile := range []string{"normal", "gaming", "calls", "battery"} {
		for _, sel := range []string{AutoTag, "node-3"} {
			var m map[string]any
			json.Unmarshal([]byte(allOptions(rulesDirForTest())), &m)
			m["profile"] = profile
			o, _ := json.Marshal(m)
			cfg, err := BuildConfig(string(nodes), sel, string(o))
			if err != nil {
				t.Fatal(err)
			}
			if err := libbox.CheckConfig(cfg); err != nil {
				t.Fatalf("%s/%s rejected: %v\n%s", profile, sel, err, cfg)
			}
		}
	}
}

func TestMatchRoute(t *testing.T) {
	base := map[string]any{"mode": "smart", "services": []string{"youtube"}, "customDomains": []string{"twitch.tv"},
		"directDomains": []string{"example.org"}, "rulesDir": rulesDirForTest(), "adBlock": true}
	o, _ := json.Marshal(base)
	cases := map[string]string{
		"www.youtube.com":      "proxy",
		"https://m.twitch.tv/": "proxy",
		"api.example.org":      "direct",
		"web.whatsapp.com":     "direct", // сервис выключен
		"yandex.ru":            "direct",
		"github.com":           "proxy",
		"doubleclick.net":      "block",
	}
	for d, want := range cases {
		r, err := MatchRoute(string(o), d)
		if err != nil {
			t.Fatal(err)
		}
		var mr matchResult
		json.Unmarshal([]byte(r), &mr)
		if mr.Outbound != want {
			t.Errorf("%s: got %s (%s), want %s", d, mr.Outbound, mr.Reason, want)
		}
	}
	sel, _ := json.Marshal(map[string]any{"mode": "selected", "services": []string{"claude"}})
	r, _ := MatchRoute(string(sel), "github.com")
	if !strings.Contains(r, `"direct"`) {
		t.Errorf("selected mode: %s", r)
	}
}

func TestValidateDNS(t *testing.T) {
	if ValidateDNS("https://dns.example.com/dns-query") != "" || ValidateDNS("1.1.1.1") != "" {
		t.Fatal("valid DoH rejected")
	}
	if ValidateDNS("http://x.com") == "" || ValidateDNS("") == "" {
		t.Fatal("invalid DoH accepted")
	}
}
