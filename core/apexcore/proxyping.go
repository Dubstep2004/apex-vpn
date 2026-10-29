package apexcore

import (
	"context"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

// ProxyPing — «пинг через прокси»: HTTP-запрос к url через конкретный сервер (tag) в
// работающем ядре. method — "GET" или "HEAD". mode:
//
//	"default"   — один запрос с новым соединением (подключение + шифрование + ответ);
//	"double"    — два запроса, каждый с новым соединением; лучший (второй быстрее за счёт DNS);
//	"keepalive" — два запроса по одному соединению; лучший — чистое время ответа через сервер.
//
// Возвращает мс или -1 (не ответил). Работает только при включённом VPN (SetProbeProxy).
func ProxyPing(tag, url, method, mode string, timeoutMs int32) int32 {
	probeProxy.RLock()
	base := probeProxy.client
	probeProxy.RUnlock()
	if base == nil || tag == "" {
		return -1
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	url = urlTestOrDefault(url)
	method = strings.ToUpper(method)
	if method != http.MethodHead {
		method = http.MethodGet
	}
	pass := probePass()
	client := socks.NewClient(N.SystemDialer, probeAddr(), socks.Version5, "n:"+tag, pass)

	newTransport := func(keepAlive bool) *http.Transport {
		return &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return client.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddr(addr))
			},
			DisableKeepAlives:   !keepAlive,
			TLSHandshakeTimeout: timeout,
			MaxIdleConns:        1,
			ForceAttemptHTTP2:   true,
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	once := func(tr *http.Transport) int {
		req, err := http.NewRequestWithContext(ctx, method, url, nil)
		if err != nil {
			return -1
		}
		start := time.Now()
		resp, err := (&http.Client{Transport: tr, Timeout: timeout}).Do(req)
		if err != nil {
			return -1
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64*1024))
		resp.Body.Close()
		ms := int(time.Since(start) / time.Millisecond)
		if ms < 1 {
			ms = 1
		}
		return ms
	}
	best := func(a, b int) int {
		switch {
		case a < 0:
			return b
		case b < 0:
			return a
		case b < a:
			return b
		}
		return a
	}
	var ms int
	switch strings.ToLower(mode) {
	case "double":
		t1 := newTransport(false)
		ms = once(t1)
		ms = best(ms, once(newTransport(false)))
	case "keepalive":
		tr := newTransport(true)
		defer tr.CloseIdleConnections()
		first := once(tr)
		if first < 0 {
			return -1
		}
		ms = best(first, once(tr))
	default:
		tr := newTransport(false)
		ms = once(tr)
	}
	if ms < 0 {
		return -1
	}
	return int32(ms)
}
