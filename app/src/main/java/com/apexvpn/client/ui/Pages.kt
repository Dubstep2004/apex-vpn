package com.apexvpn.client.ui

import android.Manifest
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AccentColor
import com.apexvpn.client.data.Design
import com.apexvpn.client.data.AppsMode
import com.apexvpn.client.data.ConnProfile
import com.apexvpn.client.data.DnsChoice
import com.apexvpn.client.data.ThemeMode
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.WifiInfoReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/** Дополнительные страницы, открываются поверх вкладок. */
enum class Page { Apps, SpeedTest, SiteCheck, Wifi, Share, Dns, Profile, Appearance, Changelog, Advanced }

@Composable
fun PageHost(page: Page, viewModel: MainViewModel, onBack: () -> Unit) {
    when (page) {
        Page.Apps -> AppsPage(viewModel, onBack)
        Page.SpeedTest -> SpeedTestPage(viewModel, onBack)
        Page.SiteCheck -> SiteCheckPage(viewModel, onBack)
        Page.Wifi -> WifiPage(viewModel, onBack)
        Page.Share -> SharePage(viewModel, onBack)
        Page.Dns -> DnsPage(viewModel, onBack)
        Page.Profile -> ProfilePage(viewModel, onBack)
        Page.Appearance -> AppearancePage(viewModel, onBack)
        Page.Changelog -> ChangelogPage(onBack)
        Page.Advanced -> AdvancedPage(viewModel, onBack)
    }
}

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
    com.apexvpn.client.ui.theme.DesignBackground()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Apex.Text) }
            Spacer(Modifier.width(4.dp))
            Text(title, color = Apex.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListRow(title, subtitle, leading = { RadioDot(selected) }, onClick = onClick)
}

// ─── 6. Приложения через VPN ────────────────────────────────────────────

@Composable
private fun AppsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val appList by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadApps() }

    PageScaffold("Приложения", onBack, scroll = false) {
        Group {
            AppsMode.entries.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                ChoiceRow(m.title, m.subtitle, s.appsMode == m) { viewModel.change { copy(appsMode = m) } }
            }
        }
        if (s.appsMode != AppsMode.AllApps) {
            Spacer(Modifier.height(12.dp))
            SearchField(query, "Поиск приложения") { query = it }
            Spacer(Modifier.height(10.dp))
            val list = appList
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apex.Accent)
                }
            } else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                    .sortedByDescending { it.packageName in s.apps }
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Apex.Surface),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val on = app.packageName in s.apps
                        val icon = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                        ListRow(
                            title = app.label,
                            subtitle = app.packageName,
                            leading = {
                                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else GlyphTile(app.label.take(1), Apex.Accent)
                            },
                            onClick = { viewModel.change { copy(apps = if (on) apps - app.packageName else apps + app.packageName) } },
                        ) {
                            ApexSwitch(on, { v -> viewModel.change { copy(apps = if (v) apps + app.packageName else apps - app.packageName) } })
                        }
                    }
                }
            }
        } else {
            Caption("Выберите режим выше, чтобы отметить отдельные приложения.")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Apex.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Apex.TextDim)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Apex.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Apex.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── 5. Тест скорости ───────────────────────────────────────────────────

@Composable
private fun SpeedTestPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val st by viewModel.speed.collectAsStateWithLifecycle()
    val running = st.phase in listOf(SpeedPhase.Ping, SpeedPhase.Download, SpeedPhase.Upload)
    PageScaffold("Тест скорости", { viewModel.cancelSpeedTest(); onBack() }) {
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val big = when (st.phase) {
                SpeedPhase.Download, SpeedPhase.Upload -> "%.1f".format(Locale.US, st.live)
                SpeedPhase.Done -> "%.1f".format(Locale.US, st.result.downMbps)
                else -> "—"
            }
            Text(big, color = Apex.Text, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
            Text("Мбит/с", color = Apex.TextDim, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                when (st.phase) {
                    SpeedPhase.Idle -> "Измерим пинг, загрузку и отдачу"
                    SpeedPhase.Ping -> "Измеряем пинг…"
                    SpeedPhase.Download -> "Загрузка…"
                    SpeedPhase.Upload -> "Отдача…"
                    SpeedPhase.Done -> "Готово"
                    SpeedPhase.Error -> st.error ?: "Ошибка"
                },
                color = if (st.phase == SpeedPhase.Error) Apex.Bad else Apex.TextDim,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val r = st.result
            Metric("Пинг", if (r.pingMs >= 0) "${r.pingMs} мс" else "—", Modifier.weight(1f))
            Metric("Загрузка", if (r.downMbps >= 0) "%.1f".format(Locale.US, r.downMbps) + " Мбит/с" else "—", Modifier.weight(1f))
            Metric("Отдача", if (r.upMbps >= 0) "%.1f".format(Locale.US, r.upMbps) + " Мбит/с" else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (running) viewModel.cancelSpeedTest() else viewModel.runSpeedTest() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Apex.SurfaceHigh else Apex.Accent, contentColor = if (running) Apex.Text else Apex.OnAccent),
        ) {
            Text(if (running) "Остановить" else if (st.phase == SpeedPhase.Idle) "Начать тест" else "Повторить", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Caption("Если VPN включён, измеряется скорость через VPN. Сервер теста — Cloudflare. Тест расходует около 50–100 МБ трафика.")
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.apexCard().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Apex.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ─── 58. Проверка сайта ─────────────────────────────────────────────────

@Composable
private fun SiteCheckPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val result by viewModel.siteCheck.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    PageScaffold("Проверка сайта", onBack) {
        Caption("Введите адрес — APEX покажет, как он откроется при текущих настройках.")
        Spacer(Modifier.height(12.dp))
        InputWithButton(text, "например, youtube.com", Icons.Rounded.Search, { text = it }) { viewModel.checkSite(text) }
        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            val (title, color) = when (r.outbound) {
                "proxy" -> "Через VPN" to Apex.Accent
                "direct" -> "Напрямую" to Apex.Good
                "block" -> "Заблокировано" to Apex.Bad
                else -> "—" to Apex.TextDim
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(20.dp),
            ) {
                if (r.domain.isNotBlank()) Text(r.domain, color = Apex.TextDim, fontSize = 14.sp)
                Text(title, color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(r.reason, color = Apex.Text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun InputWithButton(
    value: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Apex.Surface)
            .border(1.dp, Apex.Outline, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Apex.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(Apex.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Apex.Accent).clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Apex.OnAccent) }
    }
}

// ─── 25. Правила Wi-Fi ──────────────────────────────────────────────────

@Composable
private fun WifiPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(WifiInfoReader.hasPermission(context)) }
    var current by remember { mutableStateOf(WifiInfoReader.currentSsid(context)) }
    var text by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = WifiInfoReader.hasPermission(context)
        current = WifiInfoReader.currentSsid(context)
    }
    PageScaffold("Правила Wi-Fi", onBack) {
        Group {
            ListRow(
                "Пауза в доверенных сетях",
                "Дома VPN выключается сам, в других сетях — включается",
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Wifi, null, tint = Apex.Cyan) } },
                onClick = { viewModel.change(reload = false) { copy(wifiRules = !wifiRules) } },
            ) { ApexSwitch(s.wifiRules, { v -> viewModel.change(reload = false) { copy(wifiRules = v) } }) }
        }
        if (!hasPermission) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Apex.Warn.copy(alpha = 0.12f)).padding(16.dp),
            ) {
                Text("Android показывает имя сети Wi-Fi только приложениям с доступом к геолокации. APEX VPN использует его только для этой функции.", color = Apex.Text, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Разрешить") }
            }
        }
        SectionLabel("Доверенные сети")
        Group {
            current?.let { ssid ->
                if (ssid !in s.trustedWifi) {
                    ListRow(
                        "Добавить «$ssid»",
                        "Вы сейчас подключены к этой сети",
                        leading = { IconTile(Apex.AccentSoft) { Icon(Icons.Rounded.Add, null, tint = Apex.AccentText) } },
                        onClick = { viewModel.change(reload = false) { copy(trustedWifi = trustedWifi + ssid) } },
                    )
                    GroupDivider()
                }
            }
            s.trustedWifi.forEach { ssid ->
                ListRow(ssid, leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Wifi, null, tint = Apex.Good) } }) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { viewModel.change(reload = false) { copy(trustedWifi = trustedWifi - ssid) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
                }
                GroupDivider()
            }
            Row(Modifier.padding(8.dp)) {
                InputWithButton(text, "Имя сети вручную", Icons.Rounded.Add, { text = it }) {
                    val name = text.trim()
                    if (name.isNotEmpty() && name != WifiManager.UNKNOWN_SSID) {
                        viewModel.change(reload = false) { copy(trustedWifi = trustedWifi + name) }
                        text = ""
                    }
                }
            }
        }
        Caption("Работает, пока VPN включён: в доверенной сети он ставится на паузу и сам продолжает работу, когда вы уходите.")
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 26. Раздача на компьютер ───────────────────────────────────────────

@Composable
private fun SharePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val ip = remember { localIp() }
    PageScaffold("Раздача на компьютер", onBack) {
        Group {
            ListRow(
                "Прокси для других устройств",
                "Компьютер в той же сети Wi-Fi или через точку доступа",
                onClick = { viewModel.change { copy(share = !share) } },
            ) { ApexSwitch(s.share, { v -> viewModel.change { copy(share = v) } }) }
        }
        if (s.share) {
            SectionLabel("Настройки для компьютера")
            Group {
                val rows = listOf(
                    "Тип" to "HTTP или SOCKS5",
                    "Адрес" to (ip ?: "IP телефона в сети Wi-Fi"),
                    "Порт" to s.sharePort.toString(),
                    "Логин" to s.shareUser,
                    "Пароль" to s.sharePass,
                )
                rows.forEachIndexed { i, (k, v) ->
                    if (i > 0) GroupDivider()
                    ListRow(k, v) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable {
                                clipboard.setText(AnnotatedString(v))
                                viewModel.showMessage("Скопировано")
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.ContentCopy, "Копировать", tint = Apex.TextDim, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Новый пароль",
                color = Apex.AccentText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.change { copy(sharePass = com.apexvpn.client.data.AppSettings.randomPassword()) } }
                    .padding(8.dp),
            )
        }
        Caption("Укажите эти данные в настройках прокси на компьютере (Windows: Параметры → Сеть → Прокси). Работает, пока VPN на телефоне включён.")
        Spacer(Modifier.height(24.dp))
    }
}

private fun localIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
        .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

// ─── DNS: 27 свой DNS, 50 детский режим ─────────────────────────────────

@Composable
private fun DnsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(s.customDns) }
    var error by remember { mutableStateOf<String?>(null) }
    PageScaffold("DNS", onBack) {
        Group {
            ListRow(
                "Семейный режим",
                "Закрывает сайты 18+, казино и букмекеров, семейный DNS",
                leading = { IconTile(Color(0xFFF59E0B)) { Icon(Icons.Rounded.FamilyRestroom, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(kids = !kids) } },
            ) { ApexSwitch(s.kids, { v -> viewModel.change { copy(kids = v) } }) }
        }
        SectionLabel("DNS для зарубежных сайтов")
        Group {
            DnsChoice.entries.forEachIndexed { i, d ->
                if (i > 0) GroupDivider()
                ListRow(
                    d.title,
                    if (d == DnsChoice.Custom && s.customDns.isNotBlank()) s.customDns else d.subtitle,
                    leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Dns, null, tint = if (d == s.dns) Apex.AccentText else Apex.TextDim) } },
                    onClick = {
                        if (d != DnsChoice.Custom || s.customDns.isNotBlank()) viewModel.change { copy(dns = d) }
                    },
                ) { if (d == s.dns) Icon(Icons.Rounded.Check, "Выбрано", tint = Apex.Accent) }
            }
        }
        if (s.kids) Caption("Семейный режим закрывает сайты 18+, казино и букмекеров, а DNS — семейный AdGuard (выбор DNS не действует).")
        SectionLabel("Свой DNS-over-HTTPS")
        InputWithButton(custom, "https://dns.example.com/dns-query", Icons.Rounded.Check, { custom = it; error = null }) {
            error = viewModel.validateDns(custom)
            if (error == null) {
                viewModel.change { copy(customDns = custom.trim(), dns = DnsChoice.Custom) }
                viewModel.showMessage("Свой DNS включён")
            }
        }
        error?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        Caption("Российские сайты в «Умном» режиме используют DNS вашего провайдера.")
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 24/47/48. Профиль подключения ──────────────────────────────────────

@Composable
private fun ProfilePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    PageScaffold("Профиль подключения", onBack) {
        Group {
            ConnProfile.entries.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                ChoiceRow(p.title, p.subtitle, s.profile == p) { viewModel.change { copy(profile = p) } }
            }
        }
        Caption("«Игры» и «Звонки» не обрывают соединения при смене сервера автопилотом. «Экономия батареи» проверяет серверы раз в 30 минут.")
    }
}

// ─── 15/62. Оформление ──────────────────────────────────────────────────

@Composable
private fun AppearancePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    PageScaffold("Оформление", onBack) {
        SectionLabel("Дизайн 2026")
        val designs = Design.entries
        designs.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    DesignPreview(d, s.theme, s.accent, selected = s.design == d, modifier = Modifier.weight(1f)) {
                        viewModel.change(reload = false) { copy(design = d) }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Caption(s.design.subtitle + when (s.design) {
            Design.Neon, Design.Terminal -> ". Всегда тёмный."
            Design.Pastel -> ". Всегда светлый."
            Design.Expressive -> ". Цвет берётся из обоев (Android 12+)."
            else -> "."
        })

        SectionLabel("Тема")
        Group {
            ThemeMode.entries.forEachIndexed { i, t ->
                if (i > 0) GroupDivider()
                ChoiceRow(t.title, null, s.theme == t) { viewModel.change(reload = false) { copy(theme = t) } }
            }
        }
        // Главный экран: «Кнопка» или «Карта»
        SectionLabel("Главный экран")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("button" to "Кнопка", "map" to "Карта").forEach { (id, title) ->
                val on = s.homeStyle == id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Apex.Surface)
                        .border(2.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(18.dp))
                        .clickable { viewModel.change(reload = false) { copy(homeStyle = id) } }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HomeStylePreview(id == "map")
                    Spacer(Modifier.height(8.dp))
                    Text(title, color = if (on) Apex.Text else Apex.TextDim, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (s.design == Design.Bento) Caption("В дизайне «Бенто» главный экран всегда из плиток.")

        // 17. Фон за щитом
        SectionLabel("Фон за щитом")
        ChipRow(listOf("none" to "Нет", "waves" to "Волны", "particles" to "Частицы", "stars" to "Звёзды"), s.shieldBackdrop) {
            viewModel.change(reload = false) { copy(shieldBackdrop = it) }
        }

        SectionLabel("Цвет")
        Row(Modifier.fillMaxWidth()) {
            AccentColor.entries.forEach { a ->
                val on = s.accent == a
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { viewModel.change(reload = false) { copy(accent = a) } }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(a.argb))
                            .border(3.dp, if (on) Apex.Text else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                    Spacer(Modifier.height(6.dp))
                    Text(a.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp)
                }
            }
        }

        // Ощущения
        SectionLabel("Ощущения")
        Group {
            ListRow("Звук подключения", if (s.connectSound) com.apexvpn.client.data.ConnectFeedback.title(s.sound) + " · на беззвучном телефон молчит" else "Выключен") {
                ApexSwitch(s.connectSound, { v -> viewModel.change(reload = false) { copy(connectSound = v) } })
            }
            if (s.connectSound) {
                com.apexvpn.client.data.ConnectFeedback.sounds.forEach { snd ->
                    GroupDivider()
                    ListRow(
                        snd.title,
                        snd.subtitle,
                        leading = { RadioDot(s.sound == snd.id) },
                        onClick = {
                            viewModel.change(reload = false) { copy(sound = snd.id) }
                            com.apexvpn.client.data.ConnectFeedback.play(context, snd.id, force = true)
                        },
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { com.apexvpn.client.data.ConnectFeedback.play(context, snd.id, force = true) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, "Послушать", tint = Apex.Accent) }
                    }
                }
            }
            GroupDivider()
            ListRow("Вибрация", "Отклик при нажатии и подключении") {
                ApexSwitch(s.vibration, { v ->
                    viewModel.change(reload = false) { copy(vibration = v) }
                    if (v) com.apexvpn.client.data.ConnectFeedback.vibrate(context)
                })
            }
        }

        // Плавность
        SectionLabel("Плавность")
        Group {
            ListRow("Плавный режим", "Меньше анимаций и свечения — для слабых телефонов и экономии батареи") {
                ApexSwitch(s.smoothMode, { v -> viewModel.change(reload = false) { copy(smoothMode = v) } })
            }
            GroupDivider()
            ListRow("Размытие стекла", if (android.os.Build.VERSION.SDK_INT >= 31) "Мягкий размытый фон за карточками" else "Нужен Android 12 или новее") {
                ApexSwitch(s.glassBlur, { v -> viewModel.change(reload = false) { copy(glassBlur = v) } }, enabled = android.os.Build.VERSION.SDK_INT >= 31)
            }
            GroupDivider()
            ListRow("Живая карта", "Дышащие кольца и бегущий по дуге свет") {
                ApexSwitch(s.liveMap, { v -> viewModel.change(reload = false) { copy(liveMap = v) } }, enabled = !s.smoothMode)
            }
        }

        // 43. Живые обои
        SectionLabel("Живые обои")
        Group {
            ListRow("Обои APEX", "Карта мира, дуга до сервера и статус VPN на рабочем столе", onClick = {
                val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(context, com.apexvpn.client.vpn.ApexWallpaper::class.java),
                )
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching { context.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                        .onFailure { viewModel.showMessage("Телефон не поддерживает живые обои") }
                }
            }) { Chevron() }
        }
        Caption("Анимация идёт, только когда обои видны и VPN включён.")

        // 20. Иконка приложения
        SectionLabel("Иконка приложения")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            com.apexvpn.client.data.AppIcons.all.forEach { icon ->
                val on = s.appIcon == icon.id
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (!on) {
                                viewModel.change(reload = false) { copy(appIcon = icon.id) }
                                viewModel.showMessage("Иконка сменится на рабочем столе через несколько секунд")
                            }
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(58.dp).border(3.dp, if (on) Apex.Accent else Color.Transparent, RoundedCornerShape(18.dp)).padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.background), null, Modifier.size(50.dp))
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.foreground), null, Modifier.size(50.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(icon.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            ListRow("Иконка показывает статус", "Зелёная точка на иконке, пока VPN включён") {
                ApexSwitch(s.iconStatus, { v -> viewModel.change(reload = false) { copy(iconStatus = v) } })
            }
        }
        Caption("Некоторые рабочие столы обновляют иконку не сразу, а иконку на рабочем столе после смены иногда приходится добавить заново. Если со «статусом» иконка пропадает с рабочего стола — выключите его.")
        Spacer(Modifier.height(24.dp))
    }
}

/** Мини-экран дизайна: фон, карточка и щит в его цветах. */
@Composable
private fun DesignPreview(
    design: Design,
    theme: ThemeMode,
    accent: AccentColor,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val p = remember(design, theme, accent, systemDark) {
        com.apexvpn.client.ui.theme.paletteFor(design, theme, accent, systemDark)
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Apex.Accent else Apex.Outline, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    when (design) {
                        Design.Glass, Design.Aurora -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.accent.copy(alpha = 0.7f), p.base, Color(0xFF22D3EE).copy(alpha = 0.6f)))
                        Design.Pastel -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFF1E8), Color(0xFFF6EEFF)))
                        Design.Neon -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.brand1.copy(alpha = 0.35f), p.base, p.brand2.copy(alpha = 0.3f)))
                        else -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.base, p.base))
                    },
                )
                .padding(10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.brand1, p.brand2))),
                )
                Spacer(Modifier.height(8.dp))
                if (design == Design.Bento) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(p.surface).border(1.dp, p.cardBorder, RoundedCornerShape(6.dp)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)).background(p.surfaceHigh))
                } else {
                    val r = RoundedCornerShape((p.radius.value / 3).dp)
                    Box(Modifier.fillMaxWidth().height(22.dp).clip(r).background(p.surface).border(1.dp, p.cardBorder, r))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(r).background(p.surfaceHigh).border(1.dp, p.cardBorder, r))
                }
            }
        }
        Text(
            design.title,
            color = if (selected) Apex.Text else Apex.TextDim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (design == Design.Terminal) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().background(Apex.Surface).padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

fun openVpnSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ─── История версий ─────────────────────────────────────────────────────

@Composable
private fun ChangelogPage(onBack: () -> Unit) {
    PageScaffold("Что нового", onBack) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            ApexLogo(44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("APEX VPN ${com.apexvpn.client.BuildConfig.VERSION_NAME}", color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Сборка ${com.apexvpn.client.BuildConfig.VERSION_CODE}", color = Apex.TextDim, fontSize = 14.sp)
            }
        }
        com.apexvpn.client.data.Changelog.releases.forEach { r ->
            ReleaseCard(r, installed = r.code == com.apexvpn.client.BuildConfig.VERSION_CODE)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ReleaseCard(r: com.apexvpn.client.data.Release, installed: Boolean) {
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (installed) Apex.SelectedCard else Apex.Surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Версия ${r.version}", color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (installed) {
                Spacer(Modifier.width(8.dp))
                Tag("Установлена")
            }
            Spacer(Modifier.weight(1f))
            Text(r.date, color = Apex.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        r.changes.forEach { c ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", color = Apex.Accent, fontSize = 15.sp, modifier = Modifier.width(16.dp))
                Text(c, color = Apex.Text, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
    }
}

// ─── Маршрутизация для опытных (как «Правила маршрутизации» в v2rayNG) ──────

private val ruleActions = listOf("proxy" to "VPN", "direct" to "Напрямую", "block" to "Блок")
private val dnsTypes = listOf("https" to "DoH", "tls" to "DoT", "quic" to "DoQ", "udp" to "UDP")
private val strategies = listOf(
    "as_is" to ("Как есть" to "Правила по доменам, IP узнаём только при подключении"),
    "ip_if_non_match" to ("IPIfNonMatch" to "Если домен не подошёл ни к одному правилу — проверить его IP"),
    "ip_on_demand" to ("IPOnDemand" to "Сразу узнавать IP и проверять правила по IP"),
)

@Composable
private fun AdvancedPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updating by viewModel.rulesUpdating.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    PageScaffold("Маршрутизация", onBack) {
        Caption("Для опытных. Неверные настройки могут сломать доступ к сайтам — тогда нажмите «Сбросить» внизу.")

        // ── Базы сайтов (гео-файлы)
        SectionLabel("Базы сайтов")
        var source by rememberSaveable { mutableStateOf(s.rulesSource) }
        Group {
            val dir = remember(updating) { com.apexvpn.client.data.RulesUpdater.activeDir(context)?.let { java.io.File(it) } }
            com.apexvpn.client.data.RulesUpdater.FILES.forEachIndexed { i, name ->
                if (i > 0) GroupDivider()
                val f = dir?.let { java.io.File(it, name) }
                val info = if (f != null && f.exists()) "${formatBytes(f.length())} · ${formatDate(f.lastModified() / 1000)}" else "нет файла"
                ListRow(name.removeSuffix(".srs"), info + "\n" + com.apexvpn.client.data.RulesUpdater.urlFor(name))
            }
        }
        Spacer(Modifier.height(10.dp))
        PlainField(source, "Свой адрес баз (пусто — по умолчанию)") { source = it.trim() }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (source.isNotEmpty() && !source.startsWith("https://")) {
                    viewModel.showMessage("Адрес должен начинаться с https://")
                } else {
                    viewModel.change(reload = false) { copy(rulesSource = source) }
                    viewModel.updateRules()
                }
            },
            enabled = !updating,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) {
            if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.OnAccent)
            else Text("Обновить базы", fontWeight = FontWeight.SemiBold)
        }

        // ── Свои правила
        SectionLabel("Свои правила", trailing = s.userRules.size.takeIf { it > 0 }?.toString())
        var value by rememberSaveable { mutableStateOf("") }
        var action by rememberSaveable { mutableStateOf("proxy") }
        var ruleError by remember { mutableStateOf<String?>(null) }
        if (s.userRules.isNotEmpty()) {
            Group {
                s.userRules.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    ListRow(r.value, ruleActions.firstOrNull { it.first == r.action }?.second ?: r.action) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable {
                                viewModel.change { copy(userRules = userRules.filterIndexed { j, _ -> j != i }) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        ChipRow(ruleActions, action) { action = it }
        Spacer(Modifier.height(8.dp))
        InputWithButton(value, "example.com, 1.2.3.4/24, keyword:…", Icons.Rounded.Add, { value = it; ruleError = null }) {
            val v = value.trim()
            ruleError = if (v.isEmpty()) "Введите домен или IP" else viewModel.validateUserRule(v)
            if (ruleError == null) {
                viewModel.change { copy(userRules = userRules.filterNot { it.value == v } + com.apexvpn.client.data.UserRule(v, action)) }
                value = ""
            }
        }
        ruleError?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        Caption("Домен — вместе с поддоменами. full: — только этот домен, keyword: — по слову, regexp: — выражение, IP или подсеть. Порты: port:443, udp:27000-27100 (игры напрямую), tcp:25565. Свои правила важнее остальных.")

        // ── Настройки доменов
        SectionLabel("Настройки доменов")
        Group {
            ListRow("Поддельный DNS (FakeDNS)", "Быстрее открывает сайты через VPN, адреса 198.18.x.x") {
                ApexSwitch(s.fakeDns, { v -> viewModel.change { copy(fakeDns = v) } })
            }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            strategies.forEachIndexed { i, (id, t) ->
                if (i > 0) GroupDivider()
                ChoiceRow(t.first, t.second, s.domainStrategy == id) { viewModel.change { copy(domainStrategy = id) } }
            }
        }

        // ── 31. WARP-цепочка
        SectionLabel("WARP-цепочка")
        val warpBusy by viewModel.warpBusy.collectAsStateWithLifecycle()
        Group {
            ListRow(
                "Выход через Cloudflare WARP",
                when {
                    warpBusy -> "Регистрирую бесплатный аккаунт…"
                    s.warp -> "Вы → сервер VPN → WARP → сайт"
                    else -> "Сайты увидят IP Cloudflare, а не сервера"
                },
            ) {
                if (warpBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Apex.Accent)
                else ApexSwitch(s.warp, { v -> viewModel.setWarp(v) })
            }
            if (s.warpConfig.isNotBlank()) {
                GroupDivider()
                ListRow("Новый аккаунт WARP", "Если WARP перестал работать", onClick = { viewModel.resetWarp(); viewModel.setWarp(true) })
            }
        }
        Caption("Помогает, когда сайт не открывается с IP сервера (капча, «недоступно в вашей стране»). Скорость может быть ниже. Нужен сервер с поддержкой UDP.")

        // ── DNS
        DnsEditor("Удалённый DNS (через VPN)", "https://dns.google/dns-query", s.remoteDns, viewModel) { d -> viewModel.change { copy(remoteDns = d) } }
        DnsEditor("Домашний DNS (напрямую)", "77.88.8.8", s.directDns, viewModel) { d -> viewModel.change { copy(directDns = d) } }
        Caption("Пусто — DNS из раздела «DNS». IP нужен, чтобы адрес сервера DNS не узнавать через другой DNS.")

        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Apex.Bad),
        ) { Text("Сбросить настройки для опытных", color = Apex.Bad, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(28.dp))
    }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Apex.Dialog,
            title = { Text("Сбросить?", color = Apex.Text) },
            text = { Text("Свои правила, FakeDNS, стратегия и DNS вернутся по умолчанию.", color = Apex.TextDim) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmReset = false
                    val d = com.apexvpn.client.data.Settings()
                    viewModel.change {
                        copy(userRules = d.userRules, domainStrategy = d.domainStrategy, fakeDns = d.fakeDns,
                            remoteDns = d.remoteDns, directDns = d.directDns, rulesSource = d.rulesSource)
                    }
                    viewModel.showMessage("Настройки сброшены")
                }) { Text("Сбросить", color = Apex.Bad) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun DnsEditor(
    title: String,
    hint: String,
    current: com.apexvpn.client.data.DnsSpec,
    viewModel: MainViewModel,
    onSave: (com.apexvpn.client.data.DnsSpec) -> Unit,
) {
    var type by rememberSaveable(current) { mutableStateOf(current.type) }
    var server by rememberSaveable(current) { mutableStateOf(current.server) }
    var ip by rememberSaveable(current) { mutableStateOf(current.ip) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionLabel(title, trailing = if (current.isSet) "вкл" else null)
    ChipRow(dnsTypes, type) { type = it; error = null }
    Spacer(Modifier.height(8.dp))
    PlainField(server, hint) { server = it.trim(); error = null }
    Spacer(Modifier.height(8.dp))
    PlainField(ip, "IP сервера (необязательно)") { ip = it.trim(); error = null }
    error?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                val d = com.apexvpn.client.data.DnsSpec(type, server, ip)
                error = if (d.isSet) viewModel.validateDnsSpec(d) else null
                if (error == null) {
                    onSave(d)
                    viewModel.showMessage(if (d.isSet) "DNS сохранён" else "DNS по умолчанию")
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) { Text("Сохранить") }
        if (current.isSet) {
            androidx.compose.material3.TextButton(onClick = { onSave(com.apexvpn.client.data.DnsSpec()) }) { Text("Очистить", color = Apex.TextDim) }
        }
    }
}

@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Apex.Accent else Apex.Surface)
                    .border(1.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(12.dp))
                    .clickable { onSelect(id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Apex.OnAccent else Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PlainField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Apex.Surface)
            .border(1.dp, Apex.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Apex.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Apex.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Мини-превью главного экрана: щит или карта с дугой. */
@Composable
private fun HomeStylePreview(map: Boolean) {
    val accent = Apex.Accent
    val accent2 = Apex.Cyan
    val dim = Apex.TextDim
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        if (map) {
            val cw = size.width / WorldMap.W
            val ch = size.height / WorldMap.H
            WorldMap.rows.forEachIndexed { j, row ->
                row.forEachIndexed { i, c ->
                    if (c == '#' && i % 2 == 0 && j % 2 == 0) drawCircle(dim.copy(alpha = 0.35f), minOf(cw, ch) * 0.6f, androidx.compose.ui.geometry.Offset((i + 0.5f) * cw, (j + 0.5f) * ch))
                }
            }
            val a = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.32f)
            val b = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.38f)
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); quadraticBezierTo((a.x + b.x) / 2, a.y - 20f, b.x - 30f, b.y) }
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(accent, 3.dp.toPx(), a)
            drawCircle(accent2, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(b.x - 30f, b.y))
        } else {
            val r = size.height * 0.42f
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent), center, r * 1.3f), r * 1.3f)
            drawCircle(accent, r * 0.7f)
        }
    }
}
