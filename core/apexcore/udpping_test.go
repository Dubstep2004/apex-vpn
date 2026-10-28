package apexcore

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"math/big"
	"net"
	"testing"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/sing-quic/hysteria2"
)

func selfSigned(t *testing.T) tls.Certificate {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	tpl := &x509.Certificate{SerialNumber: big.NewInt(1), NotBefore: time.Now(), NotAfter: time.Now().Add(time.Hour), DNSNames: []string{"x.example.com"}}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}
}

// QUIC-сервер на 127.0.0.1 (по желанию — с Salamander), принимает соединения в фоне
func quicServer(t *testing.T, salamander string) int {
	udp, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	var pc net.PacketConn = udp
	if salamander != "" {
		pc = hysteria2.NewSalamanderConn(udp, []byte(salamander))
	}
	ln, err := quic.Listen(pc, &tls.Config{Certificates: []tls.Certificate{selfSigned(t)}, NextProtos: []string{"h3"}}, nil)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept(context.Background())
			if err != nil {
				return
			}
			go func() { time.Sleep(time.Second); c.CloseWithError(0, "") }()
		}
	}()
	return udp.LocalAddr().(*net.UDPAddr).Port
}

func TestUdpPing(t *testing.T) {
	plain := quicServer(t, "")
	obfs := quicServer(t, "secret")
	links := "hysteria2://pw@127.0.0.1:" + itoa(plain) + "?sni=x.example.com#Hy2\n" +
		"hysteria2://pw@127.0.0.1:" + itoa(obfs) + "?sni=x.example.com&obfs=salamander&obfs-password=secret#Hy2obfs\n" +
		"tuic://" + testUUID + ":pw@127.0.0.1:" + itoa(plain) + "?sni=x.example.com#TUIC\n" +
		"hysteria2://pw@127.0.0.1:1?sni=x.example.com#Dead\n" +
		"wireguard://YNXtAzepDqRv9H52osJVDQnznT5AL11eVoT6mQhBb2g=@127.0.0.1:51820?publickey=Z1XXLsKYkYxuiYjJIkRvtIKFepCYHTgON+GwPq7SOV4=&address=10.0.0.2/32#WG\n" +
		"vless://" + testUUID + "@127.0.0.1:443?type=tcp&security=tls#TCP"
	nodes := parseNodes(t, links)
	data, _ := json.Marshal(nodes)
	out, err := UdpPing(string(data), 1500)
	if err != nil {
		t.Fatal(err)
	}
	var r map[string]int
	json.Unmarshal([]byte(out), &r)
	t.Log(out)
	if r["node-1"] < 1 || r["node-2"] < 1 || r["node-3"] < 1 {
		t.Fatalf("QUIC servers must answer: %v", r)
	}
	if r["node-4"] != -1 || r["node-5"] != -2 {
		t.Fatalf("dead → -1, WireGuard → -2: %v", r)
	}
	if _, ok := r["node-6"]; ok {
		t.Fatal("TCP servers are measured by the app")
	}
}

func itoa(n int) string { return joinHostPort("", n)[1:] }
