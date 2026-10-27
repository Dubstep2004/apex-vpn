package apexcore

import (
	"context"
	"encoding/json"
	"os"
	"sort"
	"strings"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/srs"
	"github.com/sagernet/sing-box/route/rule"
)

type matchResult struct {
	Domain   string `json:"domain"`
	Outbound string `json:"outbound"` // proxy | direct | block
	Reason   string `json:"reason"`
}

var (
	ruleCacheMu sync.Mutex
	ruleCache   = map[string][]adapter.HeadlessRule{}
)

// loadRuleSet читает .srs и кэширует собранные правила.
func loadRuleSet(path string) []adapter.HeadlessRule {
	if path == "" {
		return nil
	}
	ruleCacheMu.Lock()
	defer ruleCacheMu.Unlock()
	if r, ok := ruleCache[path]; ok {
		return r
	}
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()
	compat, err := srs.Read(f, true)
	if err != nil {
		return nil
	}
	plain, err := compat.Upgrade()
	if err != nil {
		return nil
	}
	var out []adapter.HeadlessRule
	for _, r := range plain.Rules {
		hr, err := rule.NewHeadlessRule(context.Background(), r)
		if err == nil {
			out = append(out, hr)
		}
	}
	ruleCache[path] = out
	return out
}

func matchSet(path, domain string) bool {
	for _, r := range loadRuleSet(path) {
		if r.Match(&adapter.InboundContext{Domain: domain}) {
			return true
		}
	}
	return false
}

func suffixMatch(domain string, list []string) bool {
	for _, s := range list {
		if domain == s || strings.HasSuffix(domain, "."+s) {
			return true
		}
	}
	return false
}

// MatchRoute отвечает, куда пойдёт сайт при текущих настройках.
// Возвращает JSON {"domain","outbound":"proxy|direct|block","reason"}.
func MatchRoute(optionsJSON string, rawDomain string) (string, error) {
	opts, err := parseOptions(optionsJSON)
	if err != nil {
		return "", err
	}
	domain := cleanDomain(rawDomain)
	res := matchResult{Domain: domain}
	switch {
	case domain == "":
		res.Outbound, res.Reason = "", "Введите адрес сайта, например youtube.com"
	case opts.AdBlock && matchSet(opts.rulesFile(fileAds), domain):
		res.Outbound, res.Reason = "block", "Реклама или трекер — заблокировано"
	case suffixMatch(domain, cleanDomains(opts.CustomDomains)):
		res.Outbound, res.Reason = "proxy", "В списке «Свои сайты»"
	case suffixMatch(domain, cleanDomains(opts.DirectDomains)):
		res.Outbound, res.Reason = "direct", "В списке «Всегда напрямую»"
	default:
		enabled := map[string]bool{}
		for _, id := range opts.Services {
			enabled[id] = true
		}
		ids := make([]string, 0, len(services))
		for id := range services {
			ids = append(ids, id)
		}
		sort.Strings(ids)
		for _, id := range ids {
			if suffixMatch(domain, services[id].domains) {
				if enabled[id] {
					res.Outbound, res.Reason = "proxy", "Сервис «"+serviceTitle(id)+"» включён"
				} else {
					res.Outbound, res.Reason = "direct", "Сервис «"+serviceTitle(id)+"» выключен"
				}
				return marshal(res)
			}
		}
		switch opts.Mode {
		case ModeAll:
			res.Outbound, res.Reason = "proxy", "Режим «Всё через VPN»"
		case ModeSelected:
			res.Outbound, res.Reason = "direct", "Режим «Только выбранные»: сайта нет в списке"
		default:
			if suffixMatch(domain, ruDomainSuffixes) || matchSet(opts.rulesFile(fileGeositeRU), domain) {
				res.Outbound, res.Reason = "direct", "Российский сайт — напрямую"
			} else {
				res.Outbound, res.Reason = "proxy", "Зарубежный сайт — через VPN"
			}
		}
	}
	return marshal(res)
}

func marshal(v any) (string, error) {
	b, err := json.Marshal(v)
	return string(b), err
}

var serviceTitles = map[string]string{
	"youtube": "YouTube", "instagram": "Instagram", "telegram": "Telegram", "whatsapp": "WhatsApp",
	"tiktok": "TikTok", "discord": "Discord", "chatgpt": "ChatGPT", "gemini": "Gemini",
	"claude": "Claude", "x": "X", "twitch": "Twitch", "cloudflare": "Cloudflare",
}

func serviceTitle(id string) string {
	if t, ok := serviceTitles[id]; ok {
		return t
	}
	return id
}
