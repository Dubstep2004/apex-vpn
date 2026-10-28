package apexcore

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"
)

func TestDescriptionAdsMetaCore(t *testing.T) {
	desc := base64.StdEncoding.EncodeToString([]byte("⚠️ РЕКЛАМА НА YOUTUBE ЕСТЬ! 📢"))
	body := strings.Join([]string{
		"#announce: base64:" + base64.StdEncoding.EncodeToString([]byte("Привет")),
		"#support-url: https://t.me/support",
		"vless://" + testUUID + "@1.1.1.1:443?type=ws&security=tls&sni=a.example.com#%F0%9F%87%AB%F0%9F%87%AE%20Финляндия%20•%20Wi-Fi?serverDescription=" + desc,
		"vless://" + testUUID + "@2.2.2.2:443?type=ws&security=tls&sni=b.example.com#Германия",
		"vless://" + testUUID + "@3.3.3.3:443?type=xhttp&security=tls&sni=c.example.com#XH",
	}, "\n")
	out, err := ParseSubscription(base64.StdEncoding.EncodeToString([]byte(body)))
	if err != nil {
		t.Fatal(err)
	}
	var res parseResult
	json.Unmarshal([]byte(out), &res)
	n := res.Nodes
	if n[0].Name != "🇫🇮 Финляндия • Wi-Fi" || !strings.Contains(n[0].Description, "РЕКЛАМА") || !n[0].Ads || n[1].Ads {
		t.Fatalf("bad description: %+v / %+v", n[0], n[1])
	}
	if res.Meta["announce"] == "" || res.Meta["support-url"] != "https://t.me/support" {
		t.Fatalf("bad meta %v", res.Meta)
	}
	data, _ := json.Marshal(n)
	lookupIPs = func(string) []string { return nil }

	auto := func(opts string) []string {
		sb, _, err := buildAll(string(data), AutoTag, opts)
		if err != nil {
			t.Fatal(err)
		}
		var c struct {
			Outbounds []struct {
				Tag       string   `json:"tag"`
				Type      string   `json:"type"`
				Outbounds []string `json:"outbounds"`
			} `json:"outbounds"`
		}
		json.Unmarshal([]byte(sb), &c)
		for _, o := range c.Outbounds {
			if o.Tag == AutoTag {
				return o.Outbounds
			}
		}
		return nil
	}
	if a := auto(`{"excludeAds":true}`); strings.Join(a, ",") != "node-2,node-3" {
		t.Fatalf("ads must be excluded from autopilot: %v", a)
	}
	if a := auto(`{"core":"singbox"}`); strings.Join(a, ",") != "node-1,node-2" {
		t.Fatalf("singbox mode must drop xray-only: %v", a)
	}
	_, xr, _ := buildAll(string(data), AutoTag, `{"core":"xray","xrayAuth":"k"}`)
	if strings.Count(xr, `"protocol": "socks"`) != 3 {
		t.Fatal("xray mode must route all through Xray")
	}
	sb, _, _ := buildAll(string(data), AutoTag, `{"clashApi":9095,"xrayAuth":"sec"}`)
	if !strings.Contains(sb, `"127.0.0.1:9095"`) || !strings.Contains(sb, `"secret": "sec"`) {
		t.Fatal("clash api missing")
	}
	checkAll(t, n, `{"clashApi":9095,"xrayAuth":"sec"}`)
}
