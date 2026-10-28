package com.apexvpn.client.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ExpiryAlerts
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.vpn.formatSpeed
import kotlinx.coroutines.delay

private val timerOptions = listOf(30, 60, 120, 240)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenPage: (Page) -> Unit,
) {
    val status by VpnState.status.collectAsStateWithLifecycle()
    val error by VpnState.error.collectAsStateWithLifecycle()
    val connectedAt by VpnState.connectedAt.collectAsStateWithLifecycle()
    val speed by VpnState.speed.collectAsStateWithLifecycle()
    val autoOffAt by VpnState.autoOffAt.collectAsStateWithLifecycle()
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val livePing by viewModel.livePing.collectAsStateWithLifecycle()
    var timerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.startLivePing()
        onDispose { viewModel.stopLivePing() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ApexWordmark()
            Spacer(Modifier.width(8.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = Apex.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Apex.Surface)
                    .clickable { onOpenPage(Page.Changelog) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            StatusPill(status)
        }

        // Объявление из Remnawave (заголовок announce) — баннером наверху, закрывается до нового текста
        var closedAnnounce by remember { mutableStateOf(com.apexvpn.client.data.Prefs.dismissedAnnounce) }
        if (sub.announce.isNotBlank() && sub.announce != closedAnnounce) {
            Spacer(Modifier.height(14.dp))
            Banner(
                icon = Icons.Rounded.Campaign,
                color = Apex.Accent,
                title = sub.title.ifBlank { null },
                text = sub.announce,
                onClick = null,
                onClose = {
                    com.apexvpn.client.data.Prefs.dismissedAnnounce = sub.announce
                    closedAnnounce = sub.announce
                },
            )
        }
        // 13. Подписка заканчивается
        ExpiryAlerts.warning(sub)?.let { w ->
            Spacer(Modifier.height(10.dp))
            Banner(Icons.Rounded.WarningAmber, Apex.Warn, null, w, onClick = null, onClose = null)
        }

        val selected = sub.nodes.find { it.tag == sub.selectedTag }
        val auto = sub.selectedTag == AUTO_TAG || selected == null
        // 12. Свайп по щиту: избранные серверы (если их 2+), иначе все без рекламы
        val cycle = remember(sub.nodes, settings.favorites) {
            val fav = sub.nodes.filter { it.name in settings.favorites }
            listOf(AUTO_TAG) + (if (fav.size >= 2) fav else sub.nodes.filter { !it.ads }).map { it.tag }
        }
        val onSwipe: (Int) -> Unit = { dir ->
            if (cycle.size > 1) {
                val i = cycle.indexOf(if (auto) AUTO_TAG else sub.selectedTag).coerceAtLeast(0)
                val next = cycle[(i + dir + cycle.size) % cycle.size]
                viewModel.select(next)
                val n = sub.nodes.find { it.tag == next }
                viewModel.showMessage(if (n == null) "Автопилот" else ServerInfo.title(n.name))
            }
        }
        if (Apex.palette.bento) {
            // Д6. Бенто: главный экран из плиток
            Spacer(Modifier.height(16.dp))
            BentoGrid(
                status = status,
                connectedAt = connectedAt,
                speed = speed,
                livePing = livePing,
                serverTitle = if (auto) "Автопилот" else ServerInfo.title(selected!!.name),
                serverFlag = if (auto) null else ServerInfo.flag(selected!!.name),
                modeTitle = settings.mode.title,
                timerText = if (autoOffAt > 0) timerLeft(autoOffAt) else "Таймер",
                timerOn = autoOffAt > 0,
                onToggle = onToggle,
                onServers = onOpenServers,
                onMode = onOpenMode,
                onTimer = { timerDialog = true },
                onSpeedTest = { onOpenPage(Page.SpeedTest) },
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Apex.Bad, fontSize = 14.sp, modifier = Modifier.apexCard(Apex.Bad.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 9.dp))
            }
        } else {
        if (settings.homeStyle == "map") {
            // Главный экран «Карта»: дуга от вас до сервера
            val pings by viewModel.pings.collectAsStateWithLifecycle()
            val target = if (auto) {
                sub.nodes.filter { !it.ads }.mapNotNull { n -> pings[n.tag]?.takeIf { it > 0 }?.let { n to it } }.minByOrNull { it.second }?.first
            } else selected
            Spacer(Modifier.height(16.dp))
            WorldMapCard(
                status = status,
                serverCountry = target?.let { flagCode(ServerInfo.flag(it.name)) },
                serverTitle = when {
                    !auto -> ServerInfo.title(selected!!.name)
                    target != null -> "Автопилот · ${ServerInfo.title(target.name)}"
                    else -> "Автопилот"
                },
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().swipeServers(onSwipe), contentAlignment = Alignment.Center) {
                ShieldAmbient(settings.shieldBackdrop, status == VpnStatus.Started, Modifier.matchParentSize())
                ShieldButton(status, onToggle, diameter = 150.dp)
            }
        } else {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().swipeServers(onSwipe), contentAlignment = Alignment.Center) {
                ShieldAmbient(settings.shieldBackdrop, status == VpnStatus.Started, Modifier.matchParentSize())
                ShieldButton(status, onToggle)
            }
        }

        val label = when (status) {
            VpnStatus.Started -> "Защищено"
            VpnStatus.Starting -> "Подключение…"
            VpnStatus.Stopping -> "Отключение…"
            VpnStatus.Paused -> "Пауза"
            VpnStatus.Blocked -> "Интернет заблокирован"
            VpnStatus.Stopped -> "Не подключено"
        }
        Text(label, color = Apex.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        when {
            status == VpnStatus.Started && connectedAt > 0 -> ConnectionTimer(connectedAt)
            status == VpnStatus.Paused -> Text("Доверенная сеть Wi-Fi — VPN не нужен", color = Apex.TextDim, fontSize = 15.sp)
            status == VpnStatus.Blocked -> Text("VPN переподключается, без него интернет закрыт", color = Apex.Warn, fontSize = 15.sp)
            else -> Text(
                if (status == VpnStatus.Stopped) "Нажмите на щит · свайп — другой сервер" else " ",
                color = Apex.TextDim,
                fontSize = 15.sp,
            )
        }

        // 4. Скорость
        if (status == VpnStatus.Started) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("↓ ${formatSpeed(speed.first)}", color = Apex.Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("↑ ${formatSpeed(speed.second)}", color = Apex.AccentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Apex.Bad,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Apex.Bad.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        // Быстрые действия: таймер (51) и тест скорости (5)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val timerText = if (autoOffAt > 0) timerLeft(autoOffAt) else "Таймер"
            Chip(Icons.Rounded.Timer, timerText, active = autoOffAt > 0) { timerDialog = true }
            Chip(Icons.Rounded.Speed, "Тест скорости") { onOpenPage(Page.SpeedTest) }
        }

        // 49. Живой пинг
        if (status == VpnStatus.Started && livePing.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PingGraph(livePing)
        }

        Spacer(Modifier.height(16.dp))

        // Сервер и режим
        Group {
            ListRow(
                title = if (auto) "Автопилот" else ServerInfo.title(selected!!.name),
                subtitle = if (auto) "Самый быстрый из ${sub.nodes.size} серверов" else "Сервер выбран вручную",
                leading = {
                    if (auto) {
                        IconTile(Apex.AccentSoft) { Icon(Icons.Rounded.Bolt, null, tint = Apex.AccentText) }
                    } else {
                        FlagCircle(ServerInfo.flag(selected!!.name), 44.dp)
                    }
                },
                onClick = onOpenServers,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim) }
            GroupDivider()
            ListRow(
                title = settings.mode.title,
                subtitle = settings.mode.subtitle,
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Public, null, tint = Apex.Cyan) } },
                onClick = onOpenMode,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim) }
        }

        }
        Spacer(Modifier.height(12.dp))
        SubscriptionCard(
            sub = sub,
            refreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            onPing = onOpenServers,
            onOpen = { openUrl(context, it) },
        )
        Spacer(Modifier.height(20.dp))
    }

    if (timerDialog) {
        AlertDialog(
            onDismissRequest = { timerDialog = false },
            containerColor = Apex.Dialog,
            title = { Text("Выключить VPN через…", color = Apex.Text) },
            text = {
                Column {
                    timerOptions.forEach { m ->
                        Text(
                            if (m < 60) "$m минут" else "${m / 60} ${if (m == 60) "час" else "часа"}",
                            color = Apex.Text,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { VpnState.setTimer(m); timerDialog = false }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                if (autoOffAt > 0) {
                    TextButton(onClick = { VpnState.setTimer(0); timerDialog = false }) { Text("Отменить таймер", color = Apex.Bad) }
                }
            },
            dismissButton = { TextButton(onClick = { timerDialog = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun timerLeft(offAt: Long): String {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(offAt) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val left = ((offAt - now) / 1000).coerceAtLeast(0)
    return if (left >= 3600) "${left / 3600} ч ${(left % 3600) / 60} мин" else "${left / 60}:${"%02d".format(left % 60)}"
}

@Composable
private fun Chip(icon: ImageVector, text: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Apex.AccentSoft else Apex.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) Apex.AccentText else Apex.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (active) Apex.AccentText else Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String?,
    text: String,
    onClick: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = if (title != null) Apex.TextDim else Apex.Text, fontSize = 14.sp)
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Скрыть", tint = Apex.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** График задержки за последние ~80 секунд. */
@Composable
private fun PingGraph(values: List<Int>) {
    val ok = values.filter { it >= 0 }
    val last = values.lastOrNull() ?: -1
    Column(
        Modifier
            .fillMaxWidth()
            .apexCard()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пинг через VPN", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (last >= 0) "$last мс" else "нет ответа", color = pingColor(last), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        val line = Apex.Accent
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            if (ok.size < 2) return@Canvas
            val max = (ok.maxOrNull() ?: 1).coerceAtLeast(50).toFloat() * 1.15f
            val step = size.width / 39f
            val offset = (40 - values.size) * step
            val path = Path()
            val fill = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v < 0) return@forEachIndexed
                val x = offset + i * step
                val y = size.height - (v / max) * size.height
                if (!started) {
                    path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y); started = true
                } else {
                    path.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(offset + (values.size - 1) * step, size.height)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lx = offset + (values.size - 1) * step
            val lv = values.last()
            if (lv >= 0) drawCircle(line, 4.dp.toPx(), Offset(lx, size.height - (lv / max) * size.height))
        }
        if (ok.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "мин ${ok.min()} · средн ${ok.average().toInt()} · макс ${ok.max()} мс",
                color = Apex.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatusPill(status: VpnStatus) {
    val on = status == VpnStatus.Started
    val paused = status == VpnStatus.Paused || status == VpnStatus.Blocked
    val color = when {
        on -> Apex.Good
        paused -> Apex.Warn
        else -> Apex.TextDim
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on || paused) color.copy(alpha = 0.14f) else Apex.Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(
            when {
                on -> "VPN включён"
                status == VpnStatus.Blocked -> "Блокировка"
                paused -> "Пауза"
                else -> "VPN выключен"
            },
            fontSize = 13.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Карточка подписки в стиле Happ: название, обновление, трафик, объявление, поддержка. */
@Composable
private fun SubscriptionCard(
    sub: com.apexvpn.client.data.SubscriptionState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .apexCard(Apex.HeroCard)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title.ifBlank { "Подписка" },
                    color = Apex.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val updated = if (sub.lastUpdated > 0) {
                    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sub.lastUpdated))
                } else {
                    "ещё не обновлялась"
                }
                Text("$updated | Автообновление - ${sub.updateHours} ч.", color = Apex.TextDim, fontSize = 13.sp)
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Accent)
                } else {
                    Icon(Icons.Rounded.Refresh, "Обновить подписку", tint = Apex.Text)
                }
            }
            IconButton(onClick = onPing) { Icon(Icons.Rounded.Speed, "Проверить пинг", tint = Apex.Text) }
        }
        Spacer(Modifier.height(12.dp))

        // Трафик: «11,3 ГБ / ∞» в полосе, по краям — сайт и поддержка
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpen(sub.webPageUrl) }, enabled = sub.webPageUrl.isNotEmpty()) {
                Icon(Icons.Outlined.Info, "Сайт", tint = if (sub.webPageUrl.isNotEmpty()) Apex.AccentText else Apex.TextDim.copy(alpha = 0.4f))
            }
            val used = sub.used.coerceAtLeast(0)
            val fraction = if (sub.total > 0) (used.toFloat() / sub.total).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Apex.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Apex.Accent.copy(alpha = 0.45f)),
                )
                Text(
                    "${formatBytes(used)} / ${if (sub.total > 0) formatBytes(sub.total) else "∞"}",
                    color = Apex.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { onOpen(sub.supportUrl) }, enabled = sub.supportUrl.isNotEmpty()) {
                val telegram = "t.me" in sub.supportUrl || sub.supportUrl.startsWith("tg:")
                Icon(
                    if (telegram) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Link,
                    "Поддержка",
                    tint = if (sub.supportUrl.isNotEmpty()) Apex.AccentText else Apex.TextDim.copy(alpha = 0.4f),
                )
            }
        }

        val expire = when {
            sub.expireAt > 0 -> "Действует до ${formatDate(sub.expireAt)}"
            sub.expireAt == 0L -> "Бессрочно"
            else -> null
        }
        expire?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Apex.TextDim, fontSize = 13.sp)
        }

    }
}

/** Д6. Плитки главного экрана: щит, скорость, пинг, сервер, режим, таймер. */
@Composable
private fun BentoGrid(
    status: VpnStatus,
    connectedAt: Long,
    speed: Pair<Long, Long>,
    livePing: List<Int>,
    serverTitle: String,
    serverFlag: String?,
    modeTitle: String,
    timerText: String,
    timerOn: Boolean,
    onToggle: () -> Unit,
    onServers: () -> Unit,
    onMode: () -> Unit,
    onTimer: () -> Unit,
    onSpeedTest: () -> Unit,
) {
    val gap = 10.dp
    val label = when (status) {
        VpnStatus.Started -> "Защищено"
        VpnStatus.Starting -> "Подключение…"
        VpnStatus.Stopping -> "Отключение…"
        VpnStatus.Paused -> "Пауза"
        VpnStatus.Blocked -> "Интернет заблокирован"
        VpnStatus.Stopped -> "Не подключено"
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(IntrinsicSize.Min)) {
            // Большая плитка: щит и состояние
            Column(
                Modifier.weight(1.25f).fillMaxHeight().apexCard(Apex.HeroCard).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShieldButton(status, onToggle, 150.dp)
                Text(label, color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                if (status == VpnStatus.Started && connectedAt > 0) ConnectionTimer(connectedAt)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                BentoTile("Скорость", Modifier.weight(1f)) {
                    Text("↓ ${formatSpeed(speed.first)}", color = Apex.Good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("↑ ${formatSpeed(speed.second)}", color = Apex.AccentText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                BentoTile("Пинг", Modifier.weight(1f)) {
                    val last = livePing.lastOrNull { it >= 0 }
                    Text(if (status == VpnStatus.Started && last != null) "$last мс" else "—", color = pingColor(last ?: -1), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile("Сервер", Modifier.weight(1.4f), onClick = onServers) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (serverFlag != null) {
                        FlagCircle(serverFlag, 26.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(serverTitle, color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            BentoTile("Режим", Modifier.weight(1f), onClick = onMode) {
                Text(modeTitle, color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile("Таймер", Modifier.weight(1f), onClick = onTimer) {
                Text(timerText, color = if (timerOn) Apex.AccentText else Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            BentoTile("Тест", Modifier.weight(1f), onClick = onSpeedTest) {
                Text("Скорость сети", color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BentoTile(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .apexCard()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(title, color = Apex.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ConnectionTimer(connectedAt: Long) {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(connectedAt) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    Text(formatDuration(now - connectedAt), color = Apex.TextDim, fontSize = 16.sp, letterSpacing = 1.sp)
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
