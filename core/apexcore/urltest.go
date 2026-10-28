package apexcore

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	sjson "github.com/sagernet/sing/common/json"
)

// URLTestLink — адрес для замера задержки (как у автопилота).
const URLTestLink = "https://www.gstatic.com/generate_204"

// UrlTest измеряет настоящую задержку каждого сервера: соединение через сам
// протокол и HTTP-запрос (работает и для UDP: Hysteria, TUIC, WireGuard).
// Вызывается, когда VPN выключен; при включённом VPN приложение спрашивает
// работающее ядро через Clash API. Серверы только для Xray не измеряются.
// Возвращает JSON {"node-1": 120, "node-2": -1, …}.
func UrlTest(nodesJSON string, timeoutMs int32) (string, error) {
	var nodes []Node
	if err := json.Unmarshal([]byte(nodesJSON), &nodes); err != nil {
		return "", err
	}
	var outbounds, endpoints []any
	var tags []string
	for i := range nodes {
		n := &nodes[i]
		if n.Outbound == nil || n.Tag == "" {
			continue
		}
		n.Outbound["tag"] = n.Tag
		linkChain(n)
		tags = append(tags, n.Tag)
		if n.Endpoint {
			endpoints = append(endpoints, n.Outbound)
		} else {
			outbounds = append(outbounds, n.Outbound)
			for _, e := range n.Extra {
				outbounds = append(outbounds, e)
			}
		}
	}
	result := map[string]int{}
	if len(tags) == 0 {
		out, _ := json.Marshal(result)
		return string(out), nil
	}
	outbounds = append(outbounds, map[string]any{"type": "direct", "tag": "direct"})
	cfg := map[string]any{
		"log":       map[string]any{"disabled": true},
		"dns":       map[string]any{"servers": []any{map[string]any{"type": "local", "tag": "local"}}},
		"outbounds": outbounds,
		"route":     map[string]any{"final": "direct", "default_domain_resolver": "local"},
	}
	if len(endpoints) > 0 {
		cfg["endpoints"] = endpoints
	}
	raw, _ := json.Marshal(cfg)

	ctx, cancel := context.WithCancel(include.Context(context.Background()))
	defer cancel()
	options, err := sjson.UnmarshalExtendedContext[option.Options](ctx, raw)
	if err != nil {
		return "", fmt.Errorf("конфиг замера: %w", err)
	}
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		return "", fmt.Errorf("ядро замера: %w", err)
	}
	defer instance.Close()
	if err := instance.Start(); err != nil {
		return "", fmt.Errorf("ядро замера: %w", err)
	}

	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	var mu sync.Mutex
	var wg sync.WaitGroup
	limit := make(chan struct{}, 10)
	for _, tag := range tags {
		wg.Add(1)
		go func(tag string) {
			defer wg.Done()
			limit <- struct{}{}
			defer func() { <-limit }()
			delay := -1
			if ob, ok := instance.Outbound().Outbound(tag); ok {
				tctx, tcancel := context.WithTimeout(ctx, time.Duration(timeoutMs)*time.Millisecond)
				if d, err := urltest.URLTest(tctx, URLTestLink, ob); err == nil {
					delay = int(d)
				}
				tcancel()
			} else if ep, ok := instance.Endpoint().Get(tag); ok {
				tctx, tcancel := context.WithTimeout(ctx, time.Duration(timeoutMs)*time.Millisecond)
				if d, err := urltest.URLTest(tctx, URLTestLink, ep); err == nil {
					delay = int(d)
				}
				tcancel()
			}
			mu.Lock()
			result[tag] = delay
			mu.Unlock()
		}(tag)
	}
	wg.Wait()
	out, _ := json.Marshal(result)
	return string(out), nil
}
