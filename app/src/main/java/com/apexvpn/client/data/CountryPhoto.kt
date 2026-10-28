package com.apexvpn.client.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 354. Фото дня страны сервера: каждый день — новое фото столицы из статьи Википедии
 * (свободные фото Викисклада). Скачивается один раз в день, хранится на телефоне.
 */
object CountryPhoto {
    data class Photo(val bitmap: Bitmap, val caption: String)

    /** Столицы (или главный город) — название статьи в русской Википедии. */
    private val cities = mapOf(
        "ru" to "Москва", "ua" to "Киев", "by" to "Минск", "kz" to "Алма-Ата", "uz" to "Ташкент", "kg" to "Бишкек",
        "am" to "Ереван", "ge" to "Тбилиси", "az" to "Баку", "tr" to "Стамбул", "de" to "Берлин", "nl" to "Амстердам",
        "fi" to "Хельсинки", "se" to "Стокгольм", "no" to "Осло", "dk" to "Копенгаген", "ee" to "Таллин", "lv" to "Рига",
        "lt" to "Вильнюс", "pl" to "Варшава", "cz" to "Прага", "at" to "Вена", "ch" to "Цюрих", "fr" to "Париж",
        "gb" to "Лондон", "ie" to "Дублин", "es" to "Мадрид", "pt" to "Лиссабон", "it" to "Рим", "ro" to "Бухарест",
        "bg" to "София", "md" to "Кишинёв", "hu" to "Будапешт", "rs" to "Белград", "gr" to "Афины", "il" to "Тель-Авив",
        "ae" to "Дубай", "in" to "Мумбаи", "sg" to "Сингапур", "hk" to "Гонконг", "jp" to "Токио", "kr" to "Сеул",
        "cn" to "Шанхай", "tw" to "Тайбэй", "vn" to "Хошимин", "th" to "Бангкок", "id" to "Джакарта", "my" to "Куала-Лумпур",
        "au" to "Сидней", "nz" to "Окленд", "us" to "Нью-Йорк", "ca" to "Торонто", "mx" to "Мехико", "br" to "Сан-Паулу",
        "ar" to "Буэнос-Айрес", "cl" to "Сантьяго", "za" to "Йоханнесбург", "eg" to "Каир", "ng" to "Лагос",
        "is" to "Рейкьявик", "lu" to "Люксембург (город)", "be" to "Брюссель", "cy" to "Никосия", "mn" to "Улан-Батор",
        "tj" to "Душанбе", "tm" to "Ашхабад", "sa" to "Эр-Рияд",
    )

    fun supports(code: String?) = code != null && code in cities

    private fun dir(context: Context) = File(context.filesDir, "country-photo").apply { mkdirs() }
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun clear(context: Context) {
        runCatching { File(context.filesDir, "country-photo").deleteRecursively() }
    }

    /** Фото из кэша на сегодня или из Википедии; null — нет интернета или фото. */
    suspend fun load(context: Context, code: String): Photo? = withContext(Dispatchers.IO) {
        val city = cities[code] ?: return@withContext null
        val base = File(dir(context), "$code-${today()}")
        val img = File(base.path + ".jpg")
        val cap = File(base.path + ".txt")
        if (img.exists() && img.length() > 0) {
            BitmapFactory.decodeFile(img.path)?.let { return@withContext Photo(it, cap.takeIf { f -> f.exists() }?.readText() ?: city) }
        }
        runCatching {
            val url = pickPhoto(city) ?: return@runCatching null
            val bytes = get(url).use { it.readBytes() }
            val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@runCatching null
            // Старые фото этой страны больше не нужны
            dir(context).listFiles()?.filter { it.name.startsWith("$code-") }?.forEach { it.delete() }
            img.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val caption = "$city · фото: Википедия"
            cap.writeText(caption)
            Photo(bmp, caption)
        }.getOrNull()
    }

    /** Фото из статьи о городе: каждый день — следующее (гербы, флаги и карты пропускаем). */
    private fun pickPhoto(city: String): String? {
        val title = URLEncoder.encode(city.replace(' ', '_'), "UTF-8")
        val json = get("https://ru.wikipedia.org/api/rest_v1/page/media-list/$title").use { it.bufferedReader().readText() }
        val items = JSONObject(json).optJSONArray("items") ?: return null
        val skip = listOf("flag", "coat", "герб", "флаг", "map", "карта", "logo", "locator", "emblem", "seal", "svg", "signature")
        val photos = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("type") == "image" }
            .filter { o -> val t = o.optString("title").lowercase(); skip.none { it in t } && (t.endsWith(".jpg") || t.endsWith(".jpeg")) }
            .mapNotNull { o ->
                val set = o.optJSONArray("srcset") ?: return@mapNotNull null
                // Самый крупный вариант (обычно 2x ≈ 1280 px)
                (0 until set.length()).mapNotNull { set.optJSONObject(it) }.lastOrNull()?.optString("src")
            }
            .filter { it.isNotBlank() }
        if (photos.isEmpty()) return null
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val src = photos[day % photos.size]
        return if (src.startsWith("//")) "https:$src" else src
    }

    private fun get(url: String): java.io.InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        // Википедия просит указывать приложение в User-Agent
        c.setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android; github.com/${BuildConfig.GITHUB_REPO})")
        check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
        return c.inputStream
    }
}
