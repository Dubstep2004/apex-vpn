package apexcore

import (
	"context"
	"crypto/tls"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/sing/common/bufio"
)

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
	d := &net.Dialer{Timeout: timeout}
	raw, err := d.DialContext(ctx, "tcp", net.JoinHostPort(ip, "853"))
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
	udp, err := net.DialUDP("udp", nil, raddr)
	if err != nil {
		return 0, err
	}
	pc := bufio.NewUnbindPacketConn(udp)
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
