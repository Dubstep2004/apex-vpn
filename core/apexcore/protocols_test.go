package apexcore

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/experimental/libbox"
)

func extraLinks() []string {
	socksAuth := base64.StdEncoding.EncodeToString([]byte("user:pw"))
	return []string{
		"hysteria://hy1.example.com:443?protocol=udp&auth=secret&peer=hy1.example.com&upmbps=30&downmbps=200&obfsParam=xyz&alpn=h3#Hy1",
		"socks://" + socksAuth + "@9.9.9.1:1080#SOCKS%20v2rayN",
		"socks5://user:pw@9.9.9.2:1080#SOCKS5",
		"http://user:pw@9.9.9.3:8080#HTTP",
		"https://user:pw@px.example.com:443#HTTPS",
		"ssh://root:pw@9.9.9.4:22#SSH",
		"anytls://pass@at.example.com:443?sni=at.example.com&insecure=1#AnyTLS",
		"ss://" + base64.RawURLEncoding.EncodeToString([]byte("aes-256-gcm:pw")) + "@9.9.9.5:8388?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dbing.com#SS%20obfs",
		"ss://" + base64.RawURLEncoding.EncodeToString([]byte("aes-128-gcm:pw")) + "@9.9.9.6:443?plugin=v2ray-plugin%3Btls%3Bhost%3Dv2.example.com#SS%20v2ray",
		"hy2://pw@h2.example.com:443,20000-30000?sni=h2.example.com#Hy2%20hop",
	}
}

func parseNodes(t *testing.T, body string) []Node {
	t.Helper()
	out, err := ParseSubscription(body)
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	if err := json.Unmarshal([]byte(out), &res); err != nil {
		t.Fatal(err)
	}
	if len(res.Skipped) > 0 {
		t.Fatalf("unexpected skipped: %v", res.Skipped)
	}
	return res.Nodes
}

func checkAll(t *testing.T, nodes []Node, opts string) string {
	t.Helper()
	data, _ := json.Marshal(nodes)
	cfg, err := BuildConfig(string(data), AutoTag, opts)
	if err != nil {
		t.Fatal(err)
	}
	if err := libbox.CheckConfig(cfg); err != nil {
		t.Fatalf("sing-box rejected config: %v\n%s", err, cfg)
	}
	return cfg
}

func TestNewProtocols(t *testing.T) {
	nodes := parseNodes(t, strings.Join(extraLinks(), "\n"))
	if len(nodes) != len(extraLinks()) {
		t.Fatalf("want %d nodes, got %d", len(extraLinks()), len(nodes))
	}
	wantCat := []string{"Hysteria", "SOCKS", "SOCKS", "HTTP", "HTTP", "SSH", "AnyTLS", "Shadowsocks", "Shadowsocks", "Hysteria 2"}
	for i, n := range nodes {
		if n.Category != wantCat[i] {
			t.Errorf("%s: category %q, want %q", n.Name, n.Category, wantCat[i])
		}
		t.Logf("%-12s %-12s %s", n.Name, n.Category, n.Detail)
	}
	if nodes[1].Outbound["username"] != "user" || nodes[1].Outbound["password"] != "pw" {
		t.Errorf("v2rayN socks auth not decoded: %v", nodes[1].Outbound)
	}
	if nodes[7].Outbound["plugin"] != "obfs-local" || nodes[7].Outbound["plugin_opts"] != "obfs=http;obfs-host=bing.com" {
		t.Errorf("bad ss plugin: %v", nodes[7].Outbound)
	}
	if !nodes[0].UDP || nodes[1].UDP {
		t.Errorf("bad UDP flags")
	}
	checkAll(t, nodes, "")
}

func TestNaiveLink(t *testing.T) {
	nodes := parseNodes(t, "naive+https://u:p@nv.example.com:443#Naive\nnaive+quic://u:p@nq.example.com#NaiveQ")
	if nodes[0].Category != "NaiveProxy" || nodes[0].Detail != "HTTPS" || nodes[1].Detail != "QUIC" || !nodes[1].UDP {
		t.Fatalf("bad naive meta: %+v", nodes)
	}
	if nodes[1].Port != 443 || nodes[0].Outbound["username"] != "u" {
		t.Fatalf("bad naive outbound: %v", nodes[0].Outbound)
	}
}

func TestShadowTLSChain(t *testing.T) {
	sb := `{"outbounds":[
	  {"type":"shadowsocks","tag":"SS-STLS","detour":"stls","method":"2022-blake3-aes-128-gcm","password":"8JCsPssfgS8tiRwiMlhARg==","server":"5.5.5.5","server_port":443},
	  {"type":"shadowtls","tag":"stls","version":3,"password":"pw","server":"5.5.5.5","server_port":443,"tls":{"enabled":true,"server_name":"www.apple.com","utls":{"enabled":true,"fingerprint":"chrome"}}},
	  {"type":"vless","tag":"Plain","server":"1.2.3.4","server_port":443,"uuid":"` + testUUID + `","detour":"direct"},
	  {"type":"direct","tag":"direct"}]}`
	nodes := parseNodes(t, sb)
	if len(nodes) != 2 {
		t.Fatalf("want 2 nodes (shadowtls hidden), got %d", len(nodes))
	}
	if nodes[0].Category != "ShadowTLS" || len(nodes[0].Extra) != 1 || nodes[0].Outbound["detour"] != "node-1-via1" {
		t.Fatalf("bad chain: %+v", nodes[0])
	}
	if _, ok := nodes[1].Outbound["detour"]; ok {
		t.Fatalf("detour to direct must be dropped")
	}
	cfg := checkAll(t, nodes, "")
	if !strings.Contains(cfg, `"node-1-via1"`) {
		t.Fatal("chain outbound missing in config")
	}
}

func TestAutoProtocols(t *testing.T) {
	nodes := parseNodes(t, strings.Join(append(sampleLinks()[:7], extraLinks()[:2]...), "\n"))
	cfg := checkAll(t, nodes, `{"autoProtocols":["VLESS"]}`)
	var c struct {
		Outbounds []struct {
			Tag       string   `json:"tag"`
			Outbounds []string `json:"outbounds"`
		} `json:"outbounds"`
	}
	json.Unmarshal([]byte(cfg), &c)
	for _, o := range c.Outbounds {
		if o.Tag == AutoTag {
			if len(o.Outbounds) != 3 {
				t.Fatalf("autopilot must use 3 VLESS servers, got %v", o.Outbounds)
			}
		}
		if o.Tag == "proxy" && len(o.Outbounds) != len(nodes)+1 {
			t.Fatalf("manual selection must keep all servers")
		}
	}
	// Нет таких протоколов — автопилот берёт все
	cfg = checkAll(t, nodes, `{"autoProtocols":["SSH"]}`)
	if !strings.Contains(cfg, `"node-9"`) {
		t.Fatal("fallback to all servers expected")
	}
}

func TestUnsupportedMessages(t *testing.T) {
	_, err := ParseSubscription("ssr://" + base64.RawURLEncoding.EncodeToString([]byte("1.1.1.1:80:origin:aes-256-cfb:plain:cHc/?remarks="+base64.RawURLEncoding.EncodeToString([]byte("Old")))))
	if err == nil || !strings.Contains(err.Error(), "ShadowsocksR Old") {
		t.Fatalf("want SSR message, got %v", err)
	}
	_, err = ParseSubscription("ss://" + base64.RawURLEncoding.EncodeToString([]byte("aes-256-gcm:pw")) + "@1.1.1.1:1?plugin=kcptun#K")
	if err == nil || !strings.Contains(err.Error(), "kcptun") {
		t.Fatalf("want plugin message, got %v", err)
	}
}

func TestMultiPort(t *testing.T) {
	cases := map[string]string{
		"hysteria2://pw@h.example.com:443,20000-30000?sni=x#A": "hysteria2://pw@h.example.com:443?sni=x#A",
		"hy://[2001:db8::1]:1000-2000#B":                       "hy://[2001:db8::1]:1000#B",
		"hysteria2://pw@[2001:db8::1]:443#C":                   "hysteria2://pw@[2001:db8::1]:443#C",
		"hysteria2://pw@h.example.com#D":                       "hysteria2://pw@h.example.com#D",
	}
	for in, want := range cases {
		if got, _ := splitMultiPort(in); got != want {
			t.Errorf("%s → %s, want %s", in, got, want)
		}
	}
	nodes := parseNodes(t, "hy2://pw@h.example.com:443,20000-30000?sni=h.example.com#Hop")
	ports, _ := nodes[0].Outbound["server_ports"].([]any)
	if len(ports) != 2 || ports[0] != "443:443" || ports[1] != "20000:30000" || nodes[0].Port != 443 {
		t.Fatalf("bad ports: %v", nodes[0].Outbound)
	}
}
