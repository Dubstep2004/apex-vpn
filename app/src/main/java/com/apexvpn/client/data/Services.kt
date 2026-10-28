package com.apexvpn.client.data

/** Режим маршрутизации. id совпадают с ядром (core/apexcore/config.go). */
enum class RouteMode(val id: String, val title: String, val subtitle: String) {
    Smart("smart", "Умный", "Российские сайты напрямую и быстро, зарубежные через VPN"),
    All("all", "Всё через VPN", "Весь трафик телефона идёт через сервер"),
    Selected("selected", "Только выбранные", "Через VPN только сервисы ниже, остальное напрямую");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Smart
    }
}

/**
 * Сервис для переключателя «Через VPN». id совпадают с каталогом в ядре
 * (core/apexcore/services.go). Значок — монограмма на цветной плашке.
 */
data class ServiceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val color: Long,
    val glyph: String,
    val defaultOn: Boolean = true,
)

object ServiceCatalog {
    val all = listOf(
        ServiceItem("youtube", "YouTube", "Видео и сервисы Google", 0xFFE53935, "▶"),
        ServiceItem("instagram", "Instagram", "Instagram, Facebook, Threads", 0xFFD6249F, "◎"),
        ServiceItem("telegram", "Telegram", "Каналы, звонки, медиа", 0xFF2AABEE, "➤"),
        ServiceItem("whatsapp", "WhatsApp", "Звонки и сообщения", 0xFF25D366, "✆"),
        ServiceItem("tiktok", "TikTok", "Короткие видео", 0xFF1F1F24, "♪"),
        ServiceItem("discord", "Discord", "Голос и сообщества", 0xFF5865F2, "◉"),
        ServiceItem("chatgpt", "ChatGPT", "Нейросеть OpenAI", 0xFF10A37F, "✳"),
        ServiceItem("gemini", "Gemini", "Нейросеть Google", 0xFF4C7DF0, "✦"),
        ServiceItem("claude", "Claude", "Нейросеть Anthropic", 0xFFD97757, "✺"),
        ServiceItem("x", "X", "Бывший Twitter", 0xFF1F1F24, "𝕏"),
        ServiceItem("twitch", "Twitch", "Стримы", 0xFF9146FF, "▣"),
        ServiceItem("cloudflare", "Cloudflare", "Сайты, которые не открываются", 0xFFF38020, "☁", defaultOn = false),
    )

    val defaultEnabled: Set<String> = all.filter { it.defaultOn }.map { it.id }.toSet()
}

/**
 * Приложения банков и госуслуг: их исключаем из VPN, чтобы они видели
 * обычную сеть телефона и не ругались на VPN.
 */
object BankApps {
    // Список можно дополнить: пакет приложения есть в адресе его страницы в Google Play (?id=…)
    val packages = listOf(
        "ru.sberbankmobile",                       // СберБанк Онлайн
        "com.idamob.tinkoff.android",              // Т-Банк
        "ru.tinkoff.investing",                    // Т-Инвестиции
        "ru.vtb24.mobilebanking.android",          // ВТБ Онлайн
        "ru.alfabank.mobile.android",              // Альфа-Банк
        "ru.gazprombank.android.mobilebank.app",   // Газпромбанк
        "ru.rostel",                               // Госуслуги
        "ru.ozon.app.android",                     // Ozon
        "ru.ozon.fintech.finance",                 // Ozon Банк
        "com.wildberries.ru",                      // Wildberries
        "ru.nspk.mirpay",                          // Mir Pay
    )

    const val SUMMARY = "Сбер, Т-Банк, ВТБ, Госуслуги, Ozon"
}

/** Публичный DNS для зарубежных запросов (идёт через VPN). */
enum class DnsChoice(val id: String, val title: String, val subtitle: String) {
    Cloudflare("cloudflare", "Cloudflare", "1.1.1.1 — быстрый"),
    Google("google", "Google", "8.8.8.8"),
    Quad9("quad9", "Quad9", "9.9.9.9 — защита от вредоносных сайтов"),
    AdGuard("adguard", "AdGuard", "Блокирует рекламу на уровне DNS"),
    Custom("custom", "Свой DNS", "Любой адрес DNS-over-HTTPS");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Cloudflare
    }
}

/** Профиль подключения: частота проверки серверов и поведение при переключении. */
enum class ConnProfile(val id: String, val title: String, val subtitle: String) {
    Normal("normal", "Обычный", "Баланс скорости и стабильности"),
    Gaming("gaming", "Игры", "Самый низкий пинг, игры не обрываются при смене сервера"),
    Calls("calls", "Звонки", "Стабильный сервер для Telegram, WhatsApp, Discord"),
    Battery("battery", "Экономия батареи", "Редкие проверки серверов");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** Какие приложения идут через VPN. */
enum class AppsMode(val id: String, val title: String, val subtitle: String) {
    AllApps("all", "Все приложения", "Через VPN работает весь телефон"),
    Exclude("exclude", "Все, кроме выбранных", "Выбранные приложения идут мимо VPN"),
    Include("include", "Только выбранные", "Через VPN только отмеченные приложения");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: AllApps
    }
}

enum class ThemeMode(val id: String, val title: String) {
    System("system", "Как в системе"),
    Dark("dark", "Тёмная"),
    Light("light", "Светлая"),
    Amoled("amoled", "AMOLED");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

enum class AccentColor(val id: String, val title: String, val argb: Long) {
    Violet("violet", "Фиолетовый", 0xFF7B6CFF),
    Blue("blue", "Синий", 0xFF3B82F6),
    Green("green", "Зелёный", 0xFF22C55E),
    Turquoise("turquoise", "Бирюзовый", 0xFF14B8C4);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Violet
    }
}

/** Каким ядром подключать серверы. */
enum class CoreMode(val id: String, val title: String, val subtitle: String) {
    Auto("auto", "Авто", "sing-box, а Xray — для XHTTP, mKCP и подобных"),
    Xray("xray", "Только Xray", "Как в Happ: всё, что умеет Xray, — через Xray"),
    SingBox("singbox", "Только sing-box", "Без Xray: серверы XHTTP и mKCP скрыты");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Auto
    }
}
