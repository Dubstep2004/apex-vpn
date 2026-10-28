package apexcore

// 31. WARP-цепочка: сервер VPN → Cloudflare WARP. Сайты видят IP Cloudflare,
// а провайдер — только ваш сервер. Регистрация бесплатного аккаунта WARP делается
// в приложении (HTTP-запрос), здесь — ключи, разбор ответа и конфиг ядра.

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strconv"
	"strings"

	"golang.org/x/crypto/curve25519"
)

// WarpConfig — данные аккаунта WARP, которые хранит приложение.
type WarpConfig struct {
	PrivateKey string `json:"privateKey"`
	PublicKey  string `json:"publicKey"` // ключ сервера Cloudflare
	Address4   string `json:"address4"`
	Address6   string `json:"address6"`
	Host       string `json:"host"`
	Port       int    `json:"port"`
	Reserved   []int  `json:"reserved"`
}

const (
	warpPeerKey  = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
	warpHost     = "162.159.192.1"
	warpPort     = 2408
	warpEndpoint = "warp"
)

// WarpKeypair — новая пара ключей WireGuard: "приватный публичный" (base64).
func WarpKeypair() (string, error) {
	var priv [32]byte
	if _, err := rand.Read(priv[:]); err != nil {
		return "", err
	}
	priv[0] &= 248
	priv[31] = (priv[31] & 127) | 64
	pub, err := curve25519.X25519(priv[:], curve25519.Basepoint)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(priv[:]) + " " + base64.StdEncoding.EncodeToString(pub), nil
}

// WarpParse разбирает ответ регистрации api.cloudflareclient.com и возвращает WarpConfig (JSON).
func WarpParse(response string, privateKey string) (string, error) {
	var r struct {
		Config struct {
			ClientID string `json:"client_id"`
			Peers    []struct {
				PublicKey string `json:"public_key"`
				Endpoint  struct {
					V4   string `json:"v4"`
					Host string `json:"host"`
				} `json:"endpoint"`
			} `json:"peers"`
			Interface struct {
				Addresses struct {
					V4 string `json:"v4"`
					V6 string `json:"v6"`
				} `json:"addresses"`
			} `json:"interface"`
		} `json:"config"`
	}
	if err := json.Unmarshal([]byte(response), &r); err != nil {
		return "", fmt.Errorf("неверный ответ WARP: %w", err)
	}
	c := WarpConfig{PrivateKey: privateKey, PublicKey: warpPeerKey, Host: warpHost, Port: warpPort}
	c.Address4 = r.Config.Interface.Addresses.V4
	c.Address6 = r.Config.Interface.Addresses.V6
	if c.Address4 == "" {
		return "", errors.New("WARP не выдал адрес")
	}
	if len(r.Config.Peers) > 0 {
		p := r.Config.Peers[0]
		if p.PublicKey != "" {
			c.PublicKey = p.PublicKey
		}
		if h, _, err := net.SplitHostPort(p.Endpoint.V4); err == nil && net.ParseIP(h) != nil {
			c.Host = h
		}
		if _, port, err := net.SplitHostPort(p.Endpoint.Host); err == nil {
			if n, _ := strconv.Atoi(port); n > 0 {
				c.Port = n
			}
		}
	}
	if id, err := base64.StdEncoding.DecodeString(r.Config.ClientID); err == nil && len(id) == 3 {
		c.Reserved = []int{int(id[0]), int(id[1]), int(id[2])}
	}
	out, err := json.Marshal(c)
	return string(out), err
}

// warpOutbound — точка WireGuard до WARP, которая подключается через VPN-сервер.
func warpOutbound(c *WarpConfig) (map[string]any, error) {
	if c == nil || c.PrivateKey == "" || c.Address4 == "" {
		return nil, errors.New("нет данных WARP")
	}
	addr := []string{withPrefix(c.Address4, 32)}
	if c.Address6 != "" {
		addr = append(addr, withPrefix(c.Address6, 128))
	}
	peer := map[string]any{
		"address":     firstNonEmpty(c.Host, warpHost),
		"port":        c.Port,
		"public_key":  firstNonEmpty(c.PublicKey, warpPeerKey),
		"allowed_ips": []string{"0.0.0.0/0", "::/0"},
	}
	if c.Port == 0 {
		peer["port"] = warpPort
	}
	if len(c.Reserved) == 3 {
		peer["reserved"] = c.Reserved
	}
	return map[string]any{
		"type":        "wireguard",
		"tag":         warpEndpoint,
		"address":     addr,
		"private_key": c.PrivateKey,
		"peers":       []any{peer},
		"mtu":         1280,
		"detour":      "proxy",
	}, nil
}

func withPrefix(a string, bits int) string {
	a = strings.TrimSpace(a)
	if strings.Contains(a, "/") {
		return a
	}
	if ip, err := netip.ParseAddr(a); err == nil {
		return netip.PrefixFrom(ip, bits).String()
	}
	return a
}

// throughWarp направляет в WARP всё, что шло в VPN.
func throughWarp(rules []any) {
	for _, r := range rules {
		if m, ok := r.(map[string]any); ok && m["outbound"] == "proxy" {
			m["outbound"] = warpEndpoint
		}
	}
}
