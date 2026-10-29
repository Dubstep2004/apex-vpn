package apexcore

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

// Замеры DNS идут через работающее ядро (локальный SOCKS «probe-in» → выбранный сервер),
// а не напрямую из приложения: так проверяется ровно тот путь, по которому ходит DNS
// под VPN, и замер работает даже на мобильной сети с белыми списками.
var probeProxy struct {
	sync.RWMutex
	client *socks.Client
	// direct — тот же SOCKS, но в обход сервера: для пинга до самих серверов
	direct *socks.Client
	port   uint16
	pass   string
}

func probeAddr() M.Socksaddr {
	probeProxy.RLock()
	defer probeProxy.RUnlock()
	return M.ParseSocksaddrHostPort("127.0.0.1", probeProxy.port)
}

func probePass() string {
	probeProxy.RLock()
	defer probeProxy.RUnlock()
	return probeProxy.pass
}

// SetProbeProxy включает замеры через ядро (port > 0) или выключает их (port = 0).
func SetProbeProxy(port int32, user, pass string) {
	probeProxy.Lock()
	defer probeProxy.Unlock()
	if port <= 0 || port > 65535 {
		probeProxy.client = nil
		probeProxy.direct = nil
		probeProxy.port, probeProxy.pass = 0, ""
		return
	}
	probeProxy.port, probeProxy.pass = uint16(port), pass
	addr := M.ParseSocksaddrHostPort("127.0.0.1", uint16(port))
	probeProxy.client = socks.NewClient(N.SystemDialer, addr, socks.Version5, user, pass)
	probeProxy.direct = socks.NewClient(N.SystemDialer, addr, socks.Version5, "direct", pass)
}

func currentDirectProxy() *socks.Client {
	probeProxy.RLock()
	defer probeProxy.RUnlock()
	return probeProxy.direct
}

func currentProbeProxy() *socks.Client {
	probeProxy.RLock()
	defer probeProxy.RUnlock()
	return probeProxy.client
}

func dialTCP(ctx context.Context, address string, timeout time.Duration) (net.Conn, error) {
	if c := currentProbeProxy(); c != nil {
		return c.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddr(address))
	}
	d := &net.Dialer{Timeout: timeout}
	return d.DialContext(ctx, "tcp", address)
}

// DnsProbeHTTPS меряет DNS-over-HTTPS (url вида https://1.1.1.1/dns-query): лучший из трёх
// запросов по одному соединению, мс; -1 — не ответил.
func DnsProbeHTTPS(url string, timeoutMs int32) int32 {
	if timeoutMs <= 0 {
		timeoutMs = 3000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), timeout*2)
	defer cancel()
	tr := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return dialTCP(ctx, addr, timeout)
		},
		TLSHandshakeTimeout: timeout,
		ForceAttemptHTTP2:   true,
		MaxIdleConns:        1,
	}
	defer tr.CloseIdleConnections()
	client := &http.Client{Transport: tr, Timeout: timeout}
	q := base64.RawURLEncoding.EncodeToString(dnsQuery())
	best := 0
	for i := 0; i < 3; i++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url+"?dns="+q, nil)
		if err != nil {
			return -1
		}
		req.Header.Set("Accept", "application/dns-message")
		start := time.Now()
		resp, err := client.Do(req)
		if err != nil {
			continue
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		resp.Body.Close()
		if resp.StatusCode != http.StatusOK || len(body) < 12 {
			continue
		}
		ms := int(time.Since(start) / time.Millisecond)
		if ms < 1 {
			ms = 1
		}
		if best == 0 || ms < best {
			best = ms
		}
	}
	if best == 0 {
		return -1
	}
	return int32(best)
}


// DnsProbe меряет DNS-over-TLS ("tls") или DNS-over-QUIC ("quic"): одно соединение
// с ip:853 (имя сертификата — sni), затем три запроса подряд; возвращает лучший
// ответ в миллисекундах или -1. Нужен приложению для «DNS Авто» (DoH меряет само).
func DnsProbe(kind, ip, sni string, timeoutMs int32) int32 {
	if timeoutMs <= 0 {
		timeoutMs = 3000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), timeout*2)
	defer cancel()
	var (
		ms  int
		err error
	)
	switch kind {
	case "tls":
		ms, err = probeDoT(ctx, ip, sni, timeout)
	case "quic":
		ms, err = probeDoQ(ctx, ip, sni, timeout)
	default:
		err = errors.New("unknown kind")
	}
	if err != nil || ms <= 0 {
		return -1
	}
	return int32(ms)
}

// dnsQuery — запрос A для www.example.com (id 0, как требует DoQ).
func dnsQuery() []byte {
	q := []byte{0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0}
	for _, l := range []string{"www", "example", "com"} {
		q = append(q, byte(len(l)))
		q = append(q, l...)
	}
	return append(q, 0, 0, 1, 0, 1)
}

func framed(q []byte) []byte {
	b := make([]byte, 2+len(q))
	binary.BigEndian.PutUint16(b, uint16(len(q)))
	copy(b[2:], q)
	return b
}

func readFramed(r io.Reader) error {
	var l [2]byte
	if _, err := io.ReadFull(r, l[:]); err != nil {
		return err
	}
	n := binary.BigEndian.Uint16(l[:])
	if n < 12 {
		return errors.New("short answer")
	}
	_, err := io.ReadFull(r, make([]byte, n))
	return err
}

func probeDoT(ctx context.Context, ip, sni string, timeout time.Duration) (int, error) {
	raw, err := dialTCP(ctx, net.JoinHostPort(ip, "853"), timeout)
	if err != nil {
		return 0, err
	}
	c := tls.Client(raw, &tls.Config{ServerName: sni, MinVersion: tls.VersionTLS12})
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(timeout * 2))
	if err := c.HandshakeContext(ctx); err != nil {
		return 0, err
	}
	best := 0
	for i := 0; i < 3; i++ {
		start := time.Now()
		if _, err := c.Write(framed(dnsQuery())); err != nil {
			return best, err
		}
		if err := readFramed(c); err != nil {
			return best, err
		}
		ms := int(time.Since(start) / time.Millisecond)
		if ms < 1 {
			ms = 1
		}
		if best == 0 || ms < best {
			best = ms
		}
	}
	return best, nil
}

func probeDoQ(ctx context.Context, ip, sni string, timeout time.Duration) (int, error) {
	raddr := &net.UDPAddr{IP: net.ParseIP(ip), Port: 853}
	if raddr.IP == nil {
		return 0, errors.New("bad ip")
	}
	var pc net.PacketConn
	if c := currentProbeProxy(); c != nil {
		// UDP через SOCKS5 (UDP ASSOCIATE) — тот же путь, что у DNS под VPN
		p, err := c.ListenPacket(ctx, M.SocksaddrFromNet(raddr))
		if err != nil {
			return 0, err
		}
		pc = p
	} else {
		udp, err := net.DialUDP("udp", nil, raddr)
		if err != nil {
			return 0, err
		}
		pc = bufio.NewUnbindPacketConn(udp)
	}
	defer pc.Close()
	conn, err := quic.Dial(ctx, pc, raddr, &tls.Config{ServerName: sni, NextProtos: []string{"doq"}, MinVersion: tls.VersionTLS13}, &quic.Config{HandshakeIdleTimeout: timeout})
	if err != nil {
		return 0, err
	}
	defer conn.CloseWithError(0, "")
	best := 0
	for i := 0; i < 3; i++ {
		start := time.Now()
		s, err := conn.OpenStreamSync(ctx)
		if err != nil {
			return best, err
		}
		_ = s.SetDeadline(time.Now().Add(timeout))
		if _, err := s.Write(framed(dnsQuery())); err != nil {
			return best, err
		}
		_ = s.Close() // DoQ: клиент закрывает свою сторону после запроса
		if err := readFramed(s); err != nil {
			return best, err
		}
		ms := int(time.Since(start) / time.Millisecond)
		if ms < 1 {
			ms = 1
		}
		if best == 0 || ms < best {
			best = ms
		}
	}
	return best, nil
}
