package com.apexvpn.client.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 413. Трафик по приложениям через VPN. Ядро знает, чьё каждое соединение (Android 10+),
 * а мы раз в 3 секунды читаем список соединений (Clash API) и складываем прирост по приложениям.
 * Считаем только то, что шло через VPN (не «напрямую»). Хранится 7 дней, только на телефоне.
 */
object AppTraffic {
    data class Usage(val pkg: String, val up: Long, val down: Long) {
        val total get() = up + down
    }

    private val _today = MutableStateFlow<List<Usage>>(emptyList())
    val today = _today.asStateFlow()

    private var job: Job? = null
    private val last = HashMap<String, LongArray>() // id соединения → [отдано, получено]
    private lateinit var ctx: Context

    private fun day(offset: Int = 0) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - offset * 86_400_000L))
    private fun sp() = ctx.getSharedPreferences("app_traffic", Context.MODE_PRIVATE)

    fun init(context: Context) {
        ctx = context.applicationContext
        _today.value = load(day())
    }

    /** Итог за последние [days] дней (1 — сегодня). */
    fun period(days: Int): List<Usage> {
        if (!::ctx.isInitialized) return emptyList()
        val sum = HashMap<String, LongArray>()
        for (i in 0 until days) load(day(i)).forEach { u ->
            val a = sum.getOrPut(u.pkg) { LongArray(2) }
            a[0] += u.up; a[1] += u.down
        }
        return sum.map { (k, v) -> Usage(k, v[0], v[1]) }.sortedByDescending { it.total }
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        last.clear()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { poll() }
                delay(3000)
            }
        }
    }

    fun stop() {
        ConnLog.save()
        job?.cancel()
        job = null
        last.clear()
    }

    fun clear() {
        if (!::ctx.isInitialized) return
        sp().edit().clear().apply()
        _today.value = emptyList()
    }

    private fun poll() {
        val traffic = AppSettings.value.appTraffic
        if (!traffic && !AppSettings.value.connLog) return
        val c = URL("http://127.0.0.1:${Prefs.clashApiPort}/connections").openConnection() as HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 3000
        c.setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
        val body = try {
            if (c.responseCode != 200) return
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val conns = JSONObject(body).optJSONArray("connections") ?: return
        val add = HashMap<String, LongArray>()
        val seen = HashSet<String>()
        for (i in 0 until conns.length()) {
            val o = conns.optJSONObject(i) ?: continue
            val id = o.optString("id")
            seen += id
            // 604. Журнал соединений
            ConnLog.onConnection(o)
            if (!traffic) continue
            val chains = o.optJSONArray("chains")
            val direct = chains != null && (0 until chains.length()).any { chains.optString(it) == "direct" || chains.optString(it) == "block" }
            val up = o.optLong("upload")
            val down = o.optLong("download")
            val prev = last[id]
            last[id] = longArrayOf(up, down)
            if (direct) continue
            val pkg = o.optJSONObject("metadata")?.optString("processPath").orEmpty().substringBefore(" (").trim()
            if (pkg.isEmpty() || pkg.all { it.isDigit() }) continue
            val du = up - (prev?.get(0) ?: 0)
            val dd = down - (prev?.get(1) ?: 0)
            if (du <= 0 && dd <= 0) continue
            val a = add.getOrPut(pkg) { LongArray(2) }
            a[0] += du.coerceAtLeast(0); a[1] += dd.coerceAtLeast(0)
        }
        last.keys.retainAll(seen)
        ConnLog.retain(seen)
        if (add.isEmpty()) return
        val key = day()
        val cur = load(key).associateBy { it.pkg }.mapValues { longArrayOf(it.value.up, it.value.down) }.toMutableMap()
        add.forEach { (k, v) ->
            val a = cur.getOrPut(k) { LongArray(2) }
            a[0] += v[0]; a[1] += v[1]
        }
        save(key, cur)
        _today.value = load(key)
    }

    private fun load(key: String): List<Usage> = runCatching {
        val o = JSONObject(sp().getString(key, "{}") ?: "{}")
        o.keys().asSequence().map { k ->
            val a = o.getJSONArray(k)
            Usage(k, a.optLong(0), a.optLong(1))
        }.sortedByDescending { it.total }.toList()
    }.getOrDefault(emptyList())

    private fun save(key: String, map: Map<String, LongArray>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, org.json.JSONArray().put(v[0]).put(v[1])) }
        val e = sp().edit().putString(key, o.toString())
        // Храним неделю
        val keep = (0 until 7).map { day(it) }.toSet()
        sp().all.keys.filter { it !in keep }.forEach { e.remove(it) }
        e.apply()
    }
}
