package apexcore

// service — набор доменов и сетей одного сервиса для правил маршрутизации.
type service struct {
	domains  []string // domain_suffix
	cidrs    []string // ip_cidr
	udpPorts string   // port_range для UDP (голос и т.п.), необязательно
}

// Каталог сервисов. Идентификаторы совпадают с Kotlin (data/Services.kt).
var services = map[string]service{
	"youtube": {domains: []string{
		"youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "ggpht.com",
		"youtube-nocookie.com", "youtubei.googleapis.com", "youtube.googleapis.com",
		"yt3.ggpht.com", "youtubekids.com", "music.youtube.com",
	}},
	"instagram": {domains: []string{
		"instagram.com", "cdninstagram.com", "ig.me", "facebook.com", "fb.com",
		"fbcdn.net", "facebook.net", "fbsbx.com", "messenger.com", "threads.net",
		"threads.com", "meta.com",
	}},
	"telegram": {
		domains: []string{
			"telegram.org", "t.me", "telegram.me", "telegra.ph", "tdesktop.com",
			"telesco.pe", "telegram.dog", "cdn-telegram.org", "tg.dev",
		},
		cidrs: []string{
			"91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22", "91.108.16.0/22",
			"91.108.20.0/22", "91.108.56.0/22", "91.105.192.0/23", "95.161.64.0/20",
			"149.154.160.0/20", "185.76.151.0/24",
			"2001:67c:4e8::/48", "2001:b28:f23c::/48", "2001:b28:f23d::/48", "2001:b28:f23f::/48",
		},
	},
	"whatsapp": {domains: []string{
		"whatsapp.com", "whatsapp.net", "wa.me", "whatsapp.biz", "wl.co",
	}},
	"tiktok": {domains: []string{
		"tiktok.com", "tiktokv.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.us",
		"byteoversea.com", "ibytedtos.com", "ibyteimg.com", "muscdn.com", "musical.ly",
		"ttwstatic.com", "tiktokw.us", "byteintlapi.com",
	}},
	"discord": {
		domains: []string{
			"discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media",
			"discordcdn.com", "discord.dev", "discord.new", "discord.gift", "discordstatus.com",
		},
		udpPorts: "50000:65535",
	},
	"chatgpt": {domains: []string{
		"openai.com", "chatgpt.com", "oaistatic.com", "oaiusercontent.com", "sora.com",
		"chat.com", "openai.org",
	}},
	"gemini": {domains: []string{
		"gemini.google.com", "bard.google.com", "aistudio.google.com", "ai.google.dev",
		"generativelanguage.googleapis.com", "alkalimakersuite-pa.clients6.google.com",
		"notebooklm.google.com", "notebooklm.google",
	}},
	"claude": {domains: []string{
		"claude.ai", "claude.com", "anthropic.com", "claudeusercontent.com",
	}},
	"x": {domains: []string{
		"x.com", "twitter.com", "twimg.com", "t.co", "x.ai", "grok.com",
	}},
	"twitch": {domains: []string{
		"twitch.tv", "ttvnw.net", "jtvnw.net", "twitchcdn.net", "twitchsvc.net",
		"twitch.a2z.com", "ext-twitch.tv",
	}},
	// Новые сервисы
	"netflix": {domains: []string{
		"netflix.com", "netflix.net", "nflxvideo.net", "nflximg.net", "nflximg.com", "nflxext.com", "nflxso.net",
	}},
	"spotify": {domains: []string{
		"spotify.com", "scdn.co", "spotifycdn.com", "spotifycdn.net", "pscdn.co", "spotify.design", "spoti.fi",
	}},
	"linkedin":   {domains: []string{"linkedin.com", "licdn.com", "lnkd.in"}},
	"reddit":     {domains: []string{"reddit.com", "redd.it", "redditmedia.com", "redditstatic.com", "reddituploads.com", "reddit.app.link"}},
	"signal":     {domains: []string{"signal.org", "whispersystems.org", "signal.art", "signal.group", "signal.me", "signal.link"}},
	"viber":      {domains: []string{"viber.com", "viber.io", "vbrpl.io"}},
	"snapchat":   {domains: []string{"snapchat.com", "snap.com", "sc-cdn.net", "sc-static.net", "snapkit.com", "snap-dev.net", "feelinsonice-hrd.appspot.com"}},
	"pinterest":  {domains: []string{"pinterest.com", "pinimg.com", "pin.it", "pinterest.ru"}},
	"soundcloud": {domains: []string{"soundcloud.com", "sndcdn.com", "soundcloud.cloud"}},
	"medium":     {domains: []string{"medium.com", "medium.engineering", "miro.medium.com"}},
	"patreon":    {domains: []string{"patreon.com", "patreonusercontent.com"}},
	"perplexity": {domains: []string{"perplexity.ai", "pplx.ai", "perplexity.com"}},
	"copilot":    {domains: []string{"copilot.microsoft.com", "sydney.bing.com", "edgeservices.bing.com", "copilot.cloud.microsoft"}},
	"notion":     {domains: []string{"notion.so", "notion.site", "notion.com", "notion-static.com", "notionusercontent.com"}},
	"figma":      {domains: []string{"figma.com", "figma.net", "figma-alpha-api.s3.us-west-2.amazonaws.com"}},
	"cloudflare": {cidrs: []string{
		"173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
		"141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
		"197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
		"104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
		"2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
		"2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32",
	}},
}

// Российские домены, которые в «Умном» режиме всегда идут напрямую.
var ruDomainSuffixes = []string{
	"ru", "su", "xn--p1ai", "xn--p1acf", "xn--80asehdb", "xn--d1acj3b", "moscow", "tatar",
	"yandex.net", "yandex.com", "yastatic.net", "yandexcloud.net", "ya.cc",
	"vk.com", "vk.me", "vkuser.net", "userapi.com", "vk-cdn.net", "mycdn.me", "okcdn.ru",
	"mail.ru", "imgsmail.ru", "mradx.net", "avito.st", "sberbank.com", "tbank-online.com",
	"tinkoff.com", "ozone.ru", "ozonusercontent.com", "wbbasket.ru", "wbstatic.net",
	"gosuslugi.ru", "rutube.ru", "kinopoisk.ru", "dzen.ru", "max.ru", "oneme.ru",
}
