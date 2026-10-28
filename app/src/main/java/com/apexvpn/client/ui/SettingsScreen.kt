package com.apexvpn.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Block
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AppsMode
import com.apexvpn.client.data.CoreMode
import com.apexvpn.client.data.DnsChoice
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.update.UpdateState

private val refreshOptions = listOf(6, 12, 24)

@Composable
fun SettingsScreen(viewModel: MainViewModel, onOpenPage: (Page) -> Unit, onScanQr: () -> Unit) {
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val rulesUpdating by viewModel.rulesUpdating.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var coreDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ScreenTitle("Настройки", Modifier.padding(start = 4.dp))

        // ── Подписка ──
        SectionLabel("Подписка")
        Group {
            ListRow("Ссылка", maskUrl(sub.url), leading = { tile(Icons.Rounded.Link) })
            GroupDivider()
            ListRow(
                "Обновить сейчас",
                "Обновлено ${formatAgo(sub.lastUpdated)}",
                leading = { tile(Icons.Rounded.Refresh) },
                onClick = { if (!refreshing) viewModel.refresh() },
            ) { if (refreshing) Spinner() }
            GroupDivider()
            ListRow("Изменить ссылку", leading = { tile(Icons.Rounded.Edit) }, onClick = { editing = true }) { Chevron() }
            GroupDivider()
            ListRow("Сканировать QR-код", "Добавить подписку с экрана другого устройства", leading = { tile(Icons.Rounded.QrCodeScanner) }, onClick = onScanQr) { Chevron() }
            GroupDivider()
            ListRow("Удалить подписку", leading = { tile(Icons.Rounded.Delete, Apex.Bad) }, titleColor = Apex.Bad, onClick = { confirmRemove = true })
        }
        SectionLabel("Автообновление подписки")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, null, tint = Apex.TextDim, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
            Column(Modifier.weight(1f)) {
                Segmented(refreshOptions.map { "$it ч" }, refreshOptions.indexOf(s.refreshHours).coerceAtLeast(0)) {
                    viewModel.change(reload = false) { copy(refreshHours = refreshOptions[it]) }
                }
            }
        }

        // ── Подключение ──
        SectionLabel("Подключение")
        Group {
            ListRow("Профиль", "${s.profile.title} — ${s.profile.subtitle}", leading = { tile(Icons.Rounded.SportsEsports) }, onClick = { onOpenPage(Page.Profile) }) { Chevron() }
            GroupDivider()
            ListRow(
                "Автопереключение при сбое",
                "Если сервер перестал отвечать — включить автопилот",
                leading = { tile(Icons.Rounded.AutoMode) },
                onClick = { viewModel.change(reload = false) { copy(failover = !failover) } },
            ) { ApexSwitch(s.failover, { v -> viewModel.change(reload = false) { copy(failover = v) } }) }
            GroupDivider()
            ListRow(
                "Приложения",
                when (s.appsMode) {
                    AppsMode.AllApps -> "Все приложения через VPN"
                    AppsMode.Exclude -> "Кроме выбранных: ${s.apps.size}"
                    AppsMode.Include -> "Только выбранные: ${s.apps.size}"
                },
                leading = { tile(Icons.Rounded.Apps) },
                onClick = { onOpenPage(Page.Apps) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "DNS",
                if (s.kids) "Семейный режим" else if (s.dns == DnsChoice.Custom) s.customDns else s.dns.title,
                leading = { tile(Icons.Rounded.Dns) },
                onClick = { onOpenPage(Page.Dns) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Маршрутизация",
                "Для опытных: свои правила, FakeDNS, DNS" + if (s.userRules.isNotEmpty()) " · правил: ${s.userRules.size}" else "",
                leading = { tile(Icons.Rounded.Route) },
                onClick = { onOpenPage(Page.Advanced) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Ядро",
                "${s.core.title} — ${s.core.subtitle}",
                leading = { tile(Icons.Rounded.Memory) },
                onClick = { coreDialog = true },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Без серверов с рекламой",
                "Автопилот не выбирает серверы с пометкой «реклама на YouTube»",
                leading = { tile(Icons.Rounded.Block) },
                onClick = { viewModel.change { copy(excludeAds = !excludeAds) } },
            ) { ApexSwitch(s.excludeAds, { v -> viewModel.change { copy(excludeAds = v) } }) }
            GroupDivider()
            ListRow(
                "IPv6",
                if (s.ipv6) "Сайты могут открываться по IPv6" else "Только IPv4 — стабильнее",
                leading = { tile(Icons.Rounded.SettingsEthernet) },
                onClick = { viewModel.change { copy(ipv6 = !ipv6) } },
            ) { ApexSwitch(s.ipv6, { v -> viewModel.change { copy(ipv6 = v) } }) }
            GroupDivider()
            ListRow(
                "Аварийный выключатель",
                if (s.killSwitch) "Если VPN упадёт — интернет закроется, пока он не вернётся" else "Выключен",
                leading = { tile(Icons.Rounded.GppMaybe) },
                onClick = { viewModel.change(reload = false) { copy(killSwitch = !killSwitch) } },
            ) { ApexSwitch(s.killSwitch, { v -> viewModel.change(reload = false) { copy(killSwitch = v) } }) }
            GroupDivider()
            ListRow("Постоянный VPN", "Для полной защиты включите «Постоянная VPN» и «Блокировать соединения без VPN» в Android", leading = { tile(Icons.Rounded.Lock) }, onClick = { openVpnSettings(context) }) { Chevron() }
        }

        // ── Сеть ──
        SectionLabel("Сеть")
        Group {
            ListRow(
                "Правила Wi-Fi",
                if (s.wifiRules) "Доверенных сетей: ${s.trustedWifi.size}" else "Выключено",
                leading = { tile(Icons.Rounded.Wifi) },
                onClick = { onOpenPage(Page.Wifi) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Раздача на компьютер",
                if (s.share) "Включено, порт ${s.sharePort}" else "Выключено",
                leading = { tile(Icons.Rounded.Computer) },
                onClick = { onOpenPage(Page.Share) },
            ) { Chevron() }
        }

        // ── Инструменты ──
        SectionLabel("Инструменты")
        Group {
            ListRow("Тест скорости", "Пинг, загрузка и отдача", leading = { tile(Icons.Rounded.Speed) }, onClick = { onOpenPage(Page.SpeedTest) }) { Chevron() }
            GroupDivider()
            ListRow("Проверить сайт", "Через VPN или напрямую", leading = { tile(Icons.Rounded.Search) }, onClick = { onOpenPage(Page.SiteCheck) }) { Chevron() }
            GroupDivider()
            val at = viewModel.rulesUpdatedAt()
            ListRow(
                "Обновить базы сайтов",
                if (at > 0) "Обновлено ${formatAgo(at)}" else "Используются встроенные базы",
                leading = { tile(Icons.Rounded.Storage) },
                onClick = { viewModel.updateRules() },
            ) { if (rulesUpdating) Spinner() }
        }

        // ── Уведомления ──
        SectionLabel("Уведомления")
        Group {
            ListRow(
                "Скорость в уведомлении",
                "↓ загрузка ↑ отдача в шторке",
                leading = { tile(Icons.Rounded.Speed) },
                onClick = { viewModel.change(reload = false) { copy(speedInNotification = !speedInNotification) } },
            ) { ApexSwitch(s.speedInNotification, { v -> viewModel.change(reload = false) { copy(speedInNotification = v) } }) }
            GroupDivider()
            ListRow(
                "Конец подписки",
                "Предупредить за 3 дня и при остатке трафика < 10%",
                leading = { tile(Icons.Rounded.NotificationsActive) },
                onClick = { viewModel.change(reload = false) { copy(expiryAlerts = !expiryAlerts) } },
            ) { ApexSwitch(s.expiryAlerts, { v -> viewModel.change(reload = false) { copy(expiryAlerts = v) } }) }
        }

        // ── Оформление ──
        SectionLabel("Оформление")
        Group {
            ListRow("Дизайн, тема и иконка", "${s.design.title} · ${s.theme.title}", leading = { tile(Icons.Rounded.Palette) }, onClick = { onOpenPage(Page.Appearance) }) { Chevron() }
        }

        // ── Обновления ──
        SectionLabel("Обновления приложения")
        Group {
            ListRow(
                "Скачивать заранее по Wi-Fi",
                "Новая версия скачается в фоне — останется нажать «Установить»",
                leading = { tile(Icons.Rounded.Download) },
                onClick = { viewModel.change(reload = false) { copy(autoDownloadUpdates = !autoDownloadUpdates) } },
            ) { ApexSwitch(s.autoDownloadUpdates, { v -> viewModel.change(reload = false) { copy(autoDownloadUpdates = v) } }) }
            GroupDivider()
            ListRow(
                "Проверять автоматически",
                "При каждом запуске",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { viewModel.change(reload = false) { copy(autoCheckUpdates = !autoCheckUpdates) } },
            ) { ApexSwitch(s.autoCheckUpdates, { v -> viewModel.change(reload = false) { copy(autoCheckUpdates = v) } }) }
            GroupDivider()
            val (subtitle, busy) = when (val u = update) {
                UpdateState.Checking -> "Проверяем…" to true
                UpdateState.UpToDate -> "У вас последняя версия" to false
                is UpdateState.Available -> "Доступна версия ${u.info.versionName}" to false
                is UpdateState.Downloading -> "Загрузка ${(u.progress * 100).toInt()}%" to true
                is UpdateState.ReadyToInstall -> "Версия ${u.info.versionName} готова к установке" to false
                is UpdateState.Failed -> u.message to false
                UpdateState.Idle -> "Версия ${BuildConfig.VERSION_NAME}" to false
            }
            ListRow("Проверить сейчас", subtitle, leading = { tile(Icons.Rounded.SystemUpdate) }, onClick = { if (!busy) viewModel.checkUpdate() }) {
                if (busy) Spinner()
            }
            GroupDivider()
            ListRow(
                "Отчёты о сбоях",
                "Анонимно: модель телефона и ошибка, без ссылок и сайтов",
                leading = { tile(Icons.Rounded.BugReport) },
                onClick = { viewModel.change(reload = false) { copy(crashReports = !crashReports) } },
            ) { ApexSwitch(s.crashReports, { v -> viewModel.change(reload = false) { copy(crashReports = v) } }) }
        }

        // ── О приложении ──
        SectionLabel("О приложении")
        Group {
            Row(
                Modifier.fillMaxWidth().clickable { onOpenPage(Page.Changelog) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ApexLogo(52.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("APEX VPN", color = Apex.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Версия ${BuildConfig.VERSION_NAME}", color = Apex.TextDim, fontSize = 14.sp)
                    Text("Ядро sing-box ${singBoxVersion()}", color = Apex.TextDim, fontSize = 13.sp)
                    Text("Ядро Xray ${BuildConfig.XRAY_VERSION}", color = Apex.TextDim, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Chevron()
            }
            GroupDivider()
            ListRow(
                "Что нового",
                "История версий APEX VPN",
                leading = { tile(Icons.Rounded.NewReleases) },
                onClick = { onOpenPage(Page.Changelog) },
            ) { Chevron() }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Apex.Dialog,
            title = { Text("Новая ссылка", color = Apex.Text) },
            text = {
                Column {
                    SubscriptionInput(viewModel, buttonText = "Сохранить") {
                        editing = false
                        viewModel.showMessage("Подписка обновлена")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }

    if (coreDialog) {
        AlertDialog(
            onDismissRequest = { coreDialog = false },
            containerColor = Apex.Dialog,
            title = { Text("Ядро", color = Apex.Text) },
            text = {
                Column {
                    CoreMode.entries.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (m == s.core) Apex.AccentSoft else Color.Transparent)
                                .clickable {
                                    // Без Xray выбранный XHTTP/mKCP-сервер не подключить — переходим на автопилот
                                    if (m == CoreMode.SingBox && sub.nodes.any { it.tag == sub.selectedTag && it.xrayOnly }) {
                                        viewModel.select(com.apexvpn.client.data.AUTO_TAG)
                                    }
                                    viewModel.change { copy(core = m) }
                                    coreDialog = false
                                }
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(m.title, color = Apex.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.subtitle, color = Apex.TextDim, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "sing-box ${singBoxVersion()} · Xray ${BuildConfig.XRAY_VERSION}",
                        color = Apex.TextDim,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { coreDialog = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Apex.Dialog,
            title = { Text("Удалить подписку?", color = Apex.Text) },
            text = { Text("VPN будет отключён, список серверов очищен.", color = Apex.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removeSubscription()
                }) { Text("Удалить", color = Apex.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun tile(icon: ImageVector, tint: Color = Apex.Text) {
    IconTile(Apex.SurfaceHigh) { Icon(icon, null, tint = tint) }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim)
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Accent)
}

fun singBoxVersion(): String = runCatching { io.nekohasekai.libbox.Libbox.version() }.getOrDefault("")

private fun maskUrl(url: String): String {
    val idx = url.indexOf('/', "https://".length)
    if (idx < 0) return url
    val path = url.substring(idx + 1)
    val masked = if (path.length > 6) path.take(3) + "•••" + path.takeLast(3) else "•••"
    return url.substring(0, idx + 1) + masked
}
