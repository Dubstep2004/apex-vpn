package com.apexvpn.client.data

/**
 * Готовые шаблоны своих списков «Через VPN». После добавления список можно изменить:
 * убрать лишнее, дописать своё. Домены — с поддоменами.
 */
data class ListTemplate(val id: String, val name: String, val glyph: String, val color: Long, val subtitle: String, val items: List<String>)

object ListTemplates {
    val all = listOf(
        ListTemplate(
            "games", "Игры", "🎮", 0xFFF59E0B, "Steam, Epic, Riot, Battle.net, EA, PlayStation, Xbox, Roblox…",
            listOf(
                "steampowered.com", "steamcommunity.com", "steamstatic.com", "steamserver.net",
                "epicgames.com", "epicgames.dev", "unrealengine.com",
                "riotgames.com", "leagueoflegends.com", "playvalorant.com", "riotcdn.net",
                "battle.net", "blizzard.com", "blzstatic.com",
                "ea.com", "origin.com", "eaassets-a.akamaihd.net",
                "ubisoft.com", "ubi.com", "ubisoftconnect.com",
                "playstation.com", "playstation.net", "sonyentertainmentnetwork.com",
                "xbox.com", "xboxlive.com", "xboxservices.com",
                "nintendo.com", "nintendo.net",
                "roblox.com", "rbxcdn.com",
                "minecraft.net", "mojang.com", "minecraftservices.com",
                "rockstargames.com", "gog.com", "itch.io", "faceit.com", "supercell.com",
            ),
        ),
        ListTemplate(
            "ai", "Нейросети", "🤖", 0xFF8B5CF6, "Midjourney, Hugging Face, Mistral, Character.AI, Poe, Suno…",
            listOf(
                "midjourney.com", "huggingface.co", "hf.co", "mistral.ai", "character.ai", "poe.com",
                "suno.com", "suno.ai", "runwayml.com", "elevenlabs.io", "leonardo.ai", "ideogram.ai",
                "stability.ai", "replicate.com", "groq.com", "cursor.com", "cursor.sh", "v0.dev", "lovable.dev",
            ),
        ),
        ListTemplate(
            "work", "Работа", "💼", 0xFF22D3EE, "Slack, Zoom, Atlassian, Microsoft 365, Asana, Miro, Canva…",
            listOf(
                "slack.com", "slack-edge.com", "zoom.us", "zoom.com",
                "atlassian.com", "atlassian.net", "jira.com", "trello.com", "bitbucket.org",
                "office.com", "microsoft365.com", "teams.microsoft.com", "sharepoint.com", "live.com", "outlook.com",
                "asana.com", "miro.com", "canva.com", "dropbox.com", "clickup.com", "monday.com", "airtable.com",
                "calendly.com", "loom.com", "grammarly.com", "deepl.com",
            ),
        ),
        ListTemplate(
            "dev", "Разработка", "💻", 0xFF34D399, "GitHub, GitLab, Docker, npm, PyPI, Stack Overflow, JetBrains…",
            listOf(
                "github.com", "githubusercontent.com", "githubassets.com", "ghcr.io",
                "gitlab.com", "docker.com", "docker.io", "npmjs.com", "npmjs.org", "pypi.org", "pythonhosted.org",
                "stackoverflow.com", "stackexchange.com", "jetbrains.com", "vercel.com", "netlify.com",
                "heroku.com", "digitalocean.com", "aws.amazon.com", "cloud.google.com", "developer.android.com",
            ),
        ),
        ListTemplate(
            "study", "Учёба", "🎓", 0xFF3B82F6, "Coursera, Udemy, edX, Khan Academy, Duolingo, Quizlet…",
            listOf(
                "coursera.org", "udemy.com", "udemycdn.com", "edx.org", "khanacademy.org", "duolingo.com",
                "quizlet.com", "skillshare.com", "brilliant.org", "codecademy.com", "academia.edu",
                "researchgate.net", "sciencedirect.com", "springer.com", "jstor.org",
            ),
        ),
        ListTemplate(
            "movies", "Кино и сериалы", "🎬", 0xFFE50914, "Disney+, Prime Video, HBO Max, Hulu, Apple TV+, IMDb…",
            listOf(
                "disneyplus.com", "disney-plus.net", "bamgrid.com", "primevideo.com", "amazonvideo.com",
                "max.com", "hbomax.com", "hulu.com", "huluim.com", "tv.apple.com", "paramountplus.com",
                "imdb.com", "media-imdb.com", "crunchyroll.com", "letterboxd.com",
            ),
        ),
        ListTemplate(
            "music", "Музыка", "🎧", 0xFF1DB954, "Deezer, Tidal, Bandcamp, Last.fm, Apple Music, Genius…",
            listOf(
                "deezer.com", "dzcdn.net", "tidal.com", "bandcamp.com", "bcbits.com", "last.fm",
                "music.apple.com", "genius.com", "mixcloud.com", "audius.co",
            ),
        ),
        ListTemplate(
            "social", "Соцсети", "💬", 0xFFEC4899, "Tumblr, Quora, Mastodon, Bluesky, Imgur, DeviantArt…",
            listOf(
                "tumblr.com", "quora.com", "mastodon.social", "bsky.app", "bsky.social",
                "imgur.com", "9gag.com", "deviantart.com", "behance.net", "dribbble.com", "flickr.com",
            ),
        ),
        ListTemplate(
            "shop", "Покупки", "🛍️", 0xFFEF4444, "Amazon, eBay, Etsy, iHerb, Walmart…",
            listOf(
                "amazon.com", "amazon.de", "media-amazon.com", "ebay.com", "ebayimg.com", "etsy.com", "etsystatic.com",
                "iherb.com", "walmart.com", "bestbuy.com",
            ),
        ),
    )
}
