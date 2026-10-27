package com.apexvpn.client.data

/** Разбор названия сервера из подписки: флаг, чистое имя, особые метки. */
object ServerInfo {
    private val lteWords = listOf("lte", "бс", "обход", "mobile", "моб", "4g", "5g")

    /** Эмодзи-флаг в начале или внутри названия (пара региональных символов). */
    fun flag(name: String): String? {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (isRegional(cps[i]) && isRegional(cps[i + 1])) {
                return String(cps, i, 2)
            }
        }
        return null
    }

    /** Название без флага и лишних разделителей. */
    fun title(name: String): String {
        val f = flag(name)
        val s = (if (f != null) name.replace(f, "") else name).trim(' ', '|', '-', '·', '•', '_')
        return s.ifBlank { name }
    }

    fun isLte(name: String): Boolean {
        val low = name.lowercase()
        return lteWords.any { w ->
            // целое слово, чтобы «моб» не ловило «автомобиль» внутри других слов
            Regex("(^|[^\\p{L}])${Regex.escape(w)}").containsMatchIn(low)
        }
    }

    fun isRussia(name: String): Boolean {
        val low = name.lowercase()
        return flag(name) == "🇷🇺" || "россия" in low || "russia" in low || "москва" in low
    }

    /** «Особые» серверы: Россия и обход белых списков (LTE). */
    fun isSpecial(name: String) = isLte(name) || isRussia(name)

    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF
}
