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
enum class Page { Apps, SpeedTest, SiteCheck, Wifi, Share, Dns, Profile, Appearance, Changelog }

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
    }
}

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Apex.Bg).statusBarsPadding()) {
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
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Apex.SurfaceHigh else Apex.Accent, contentColor = if (running) Apex.Text else Color.White),
        ) {
            Text(if (running) "Остановить" else if (st.phase == SpeedPhase.Idle) "Начать тест" else "Повторить", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Caption("Если VPN включён, измеряется скорость через VPN. Сервер теста — Cloudflare. Тест расходует около 50–100 МБ трафика.")
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Apex.Surface).padding(14.dp),
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
        ) { Icon(icon, null, tint = Color.White) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Color.White),
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
                "Детский режим",
                "Семейный DNS для всех сайтов: блокирует сайты для взрослых",
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
        if (s.kids) Caption("В детском режиме выбор DNS не действует — используется семейный AdGuard.")
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
    PageScaffold("Оформление", onBack) {
        SectionLabel("Тема")
        Group {
            ThemeMode.entries.forEachIndexed { i, t ->
                if (i > 0) GroupDivider()
                ChoiceRow(t.title, null, s.theme == t) { viewModel.change(reload = false) { copy(theme = t) } }
            }
        }
        SectionLabel("Цвет акцента")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AccentColor.entries.forEach { a ->
                val on = s.accent == a
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { viewModel.change(reload = false) { copy(accent = a) } }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
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
        Spacer(Modifier.height(24.dp))
    }
}

/** Открыть системные настройки VPN (постоянный VPN, блокировка без VPN). */
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
