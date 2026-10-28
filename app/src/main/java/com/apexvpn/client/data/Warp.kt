package com.apexvpn.client.data

import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 31. WARP-цепочка: бесплатный аккаунт Cloudflare WARP регистрируется один раз,
 * дальше сайты через VPN выходят в интернет с IP Cloudflare.
 */
object Warp {
    private const val API = "https://api.cloudflareclient.com/v0a1922/reg"

    /** Регистрирует аккаунт WARP; возвращает данные для ядра (JSON) или бросает ошибку с понятным текстом. */
    suspend fun register(): String = withContext(Dispatchers.IO) {
        val pair = Apexcore.warpKeypair().split(" ")
        check(pair.size == 2) { "не удалось создать ключи" }
        val (priv, pub) = pair
        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = """{"install_id":"","fcm_token":"","tos":"$tos","key":"$pub","type":"Android","model":"PC","locale":"en_US","warp_enabled":true}"""
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("User-Agent", "okhttp/3.12.1")
            setRequestProperty("CF-Client-Version", "a-6.3-1922")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) error("Cloudflare ответил HTTP $code")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            Apexcore.warpParse(text, priv)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Cloudflare недоступен: ${e.message ?: "нет связи"}. Включите VPN и попробуйте снова.")
        } finally {
            c.disconnect()
        }
    }
}
