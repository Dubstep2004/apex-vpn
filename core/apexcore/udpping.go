package apexcore

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/sing-quic/hysteria2"
	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
)

// UdpPing измеряет задержку серверов на QUIC (Hysteria 2, Hysteria, TUIC, NaiveProxy QUIC):
// время QUIC-рукопожатия с сервером — то же, что время TCP-соединения для остальных.
// Данные через сервер не передаются, поэтому проверка сертификата не нужна.
// Возвращает JSON {"node-1": 85, "node-2": -1 (нет ответа), "node-3": -2 (не измерить)}.
func UdpPing(nodesJSON string, timeoutMs int32) (string, error) {
	var nodes []Node
	if err := json.Unmarshal([]byte(nodesJSON), &nodes); err != nil {
		return "", err
	}
	if timeoutMs <= 0 {
		timeoutMs = 3000
	}
	result := map[string]int{}
	var mu sync.Mutex
	var wg sync.WaitGroup
	limit := make(chan struct{}, 8)
	for i := range nodes {
		n := nodes[i]
		if !n.UDP && !udpTypes[n.Type] {
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			limit <- struct{}{}
			defer func() { <-limit }()
			ms := quicPing(n, time.Duration(timeoutMs)*time.Millisecond)
			mu.Lock()
			result[n.Tag] = ms
			mu.Unlock()
		}()
	}
	wg.Wait()
	out, _ := json.Marshal(result)
	return string(out), nil
}

func quicPing(n Node, timeout time.Duration) (ms int) {
	defer func() {
		if recover() != nil {
			ms = -1
		}
	}()
	ob := n.Outbound
	if ob == nil {
		return -2 // только Xray (mKCP) — не измерить
	}
	alpn := []string{"h3"}
	switch n.Type {
	case "hysteria2", "tuic":
	case "hysteria":
		alpn = []string{"hysteria"}
	case "naive":
		if q, _ := ob["quic"].(bool); !q {
			return -2
		}
	default:
		return -2 // WireGuard и прочее — без рукопожатия не измерить
	}
	port := n.Port
	if port <= 0 {
		return -2
	}
	sni := n.Server
	if t, ok := ob["tls"].(map[string]any); ok {
		if s, _ := t["server_name"].(string); s != "" {
			sni = s
		}
		if a, ok := t["alpn"].([]any); ok && len(a) > 0 {
			alpn = nil
			for _, v := range a {
				if s, ok := v.(string); ok {
					alpn = append(alpn, s)
				}
			}
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	addrs, err := net.DefaultResolver.LookupIPAddr(ctx, n.Server)
	if err != nil || len(addrs) == 0 {
		return -1
	}
	raddr := &net.UDPAddr{IP: addrs[0].IP, Port: port}
	var pc net.PacketConn
	if d := currentDirectProxy(); d != nil {
		// VPN включён: сокет приложения попал бы в туннель. Идём через ядро напрямую —
		// так меряется пинг до самого сервера, как без VPN
		p, err := d.ListenPacket(ctx, M.SocksaddrFromNet(raddr))
		if err != nil {
			return -1
		}
		pc = p
	} else {
		udp, err := net.DialUDP("udp", nil, raddr)
		if err != nil {
			return -1
		}
		// как в sing-quic: подключённый сокет → обычный PacketConn
		pc = bufio.NewUnbindPacketConn(udp)
	}
	defer pc.Close()
	// Hysteria 2 с обфускацией Salamander
	if n.Type == "hysteria2" {
		if o, ok := ob["obfs"].(map[string]any); ok {
			if t, _ := o["type"].(string); t == "salamander" {
				pass, _ := o["password"].(string)
				pc = hysteria2.NewSalamanderConn(pc, []byte(pass))
			}
		}
	}
	if n.Type == "hysteria" {
		if o, _ := ob["obfs"].(string); o != "" {
			return -2 // обфускация Hysteria v1 — не измерить рукопожатием
		}
	}
	tlsConf := &tls.Config{ServerName: sni, NextProtos: alpn, InsecureSkipVerify: true, MinVersion: tls.VersionTLS13}
	start := time.Now()
	conn, err := quic.Dial(ctx, pc, raddr, tlsConf, &quic.Config{HandshakeIdleTimeout: timeout})
	if err != nil {
		return -1
	}
	elapsed := time.Since(start)
	_ = conn.CloseWithError(0, "")
	ms = int(elapsed / time.Millisecond)
	if ms < 1 {
		ms = 1
	}
	return ms
}

// для тестов
func joinHostPort(h string, p int) string { return net.JoinHostPort(h, strconv.Itoa(p)) }

var _ = strings.TrimSpace
