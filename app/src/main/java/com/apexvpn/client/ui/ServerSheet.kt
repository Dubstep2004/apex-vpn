package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.CoreMode
import com.apexvpn.client.data.Node
import com.apexvpn.client.data.Pinger
import com.apexvpn.client.data.Protocols
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.ui.theme.Apex

private enum class ServerTab(val title: String) { Fast("Быстрые"), All("Все"), Lte("Только LTE") }

private const val COLLAPSED = 4

/** Серверы с одинаковым названием (разные протоколы) показываем одной строкой. */
private data class ServerGroup(val title: String, val flag: String?, val nodes: List<Node>)


/** Шторка выбора сервера: автопилот, избранное, сортировка по пингу, протоколы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pings by viewModel.pings.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ServerTab.Fast) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.pingAll() }

    val autopilot = sub.selectedTag == AUTO_TAG
    fun ping(n: Node): Int? = pings[n.tag]
    fun key(n: Node) = ping(n)?.takeIf { it >= 0 } ?: Int.MAX_VALUE
    fun groupKey(g: ServerGroup) = g.nodes.minOf { key(it) }

    // Категории протоколов: сколько серверов каждого вида в подписке
    // В режиме «Только sing-box» серверы, которым нужен Xray, не показываем
    val base = sub.nodes.filter { !(settings.core == CoreMode.SingBox && it.xrayOnly) }
    val categories = base.groupingBy { it.category }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    val activeProtocol = settings.protocol.takeIf { p -> categories.any { it.key == p } } ?: ""
    val visible = if (activeProtocol.isEmpty()) base else base.filter { it.category == activeProtocol }

    val groups = visible
        .groupBy { ServerInfo.flag(it.name) to ServerInfo.title(it.name) }
        .map { (k, list) -> ServerGroup(k.second, k.first, list.sortedBy { key(it) }) }
    val favorites = groups.filter { g -> g.nodes.any { it.name in settings.favorites } || g.title in settings.favorites }
    // Серверы с рекламой на YouTube — отдельной категорией внизу
    val withAds = groups.filter { g -> g.nodes.all { it.ads } }
    val clean = groups - withAds.toSet()
    val regular = clean.filter { g -> g.nodes.none { ServerInfo.isSpecial(it.name) } }
    val special = clean.filter { g -> g.nodes.any { ServerInfo.isSpecial(it.name) } }
    val fastest = regular.filter { groupKey(it) != Int.MAX_VALUE }.minByOrNull { groupKey(it) }?.title

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Apex.Bg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Apex.SurfaceHigh) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                ScreenTitle("Сервер", Modifier.weight(1f))
                RoundButton(onClick = { viewModel.pingAll() }) {
                    if (pinging) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Text)
                    } else {
                        Icon(Icons.Rounded.Refresh, "Обновить пинг", tint = Apex.Text)
                    }
                }
                Spacer(Modifier.width(10.dp))
                RoundButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Закрыть", tint = Apex.Text) }
            }
            Spacer(Modifier.height(16.dp))

            val fallback = clean.filter { groupKey(it) != Int.MAX_VALUE }.minByOrNull { groupKey(it) }?.nodes?.first()?.tag
                ?: base.firstOrNull()?.tag
            Group {
                ListRow(
                    title = "Автопилот",
                    subtitle = if (autopilot) "Включён: сам выбирает самый быстрый" else "Выключен: сервер выбран вручную",
                    leading = {
                        IconTile(if (autopilot) Apex.AccentSoft else Apex.SurfaceHigh) {
                            Icon(Icons.Rounded.Bolt, null, tint = if (autopilot) Apex.AccentText else Apex.Text)
                        }
                    },
                    onClick = { toggleAutopilot(viewModel, autopilot, fallback) },
                ) {
                    ApexSwitch(autopilot, { toggleAutopilot(viewModel, autopilot, fallback) })
                }
            }
            Spacer(Modifier.height(14.dp))

            Segmented(ServerTab.entries.map { it.title }, ServerTab.entries.indexOf(tab)) { tab = ServerTab.entries[it] }

            // Категории протоколов
            if (categories.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProtocolChip("Все протоколы", sub.nodes.size, activeProtocol.isEmpty()) {
                        viewModel.change { copy(protocol = "") }
                    }
                    categories.forEach { (name, count) ->
                        ProtocolChip(name, count, activeProtocol == name) {
                            val next = if (activeProtocol == name) "" else name
                            viewModel.change { copy(protocol = next) }
                        }
                    }
                }
                if (activeProtocol.isNotEmpty()) {
                    Text(
                        "Показаны только $activeProtocol. Автопилот тоже выбирает среди них.",
                        color = Apex.TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }

            val pick: (Node) -> Unit = { viewModel.select(it.tag); onDismiss() }
            val row: @Composable (ServerGroup, Boolean) -> Unit = { g, isFastest ->
                GroupRow(
                    group = g,
                    pings = pings,
                    selectedTag = if (autopilot) null else sub.selectedTag,
                    fastest = isFastest,
                    favorite = g.title in settings.favorites || g.nodes.any { it.name in settings.favorites },
                    onPick = pick,
                    onFavorite = { viewModel.toggleFavorite(g.title) },
                )
            }

            if (favorites.isNotEmpty() && tab != ServerTab.Lte) {
                SectionLabel("Избранные")
                Group { favorites.sortedBy { groupKey(it) }.forEach { row(it, false) } }
            }

            when (tab) {
                ServerTab.Fast, ServerTab.All -> {
                    val list = if (tab == ServerTab.Fast) regular.sortedBy { groupKey(it) } else regular
                    if (list.isNotEmpty()) {
                        SectionLabel(if (tab == ServerTab.Fast) "Серверы по скорости" else "Все серверы", "пинг сейчас")
                        Group {
                            val shown = if (expanded || list.size <= COLLAPSED + 1) list else list.take(COLLAPSED)
                            shown.forEach { row(it, it.title == fastest) }
                            if (list.size > COLLAPSED + 1) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (expanded) "Свернуть" else "Все серверы, ещё ${list.size - COLLAPSED}",
                                        color = Apex.AccentText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(
                                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        null,
                                        tint = Apex.AccentText,
                                    )
                                }
                            }
                        }
                    }
                    if (special.isNotEmpty()) {
                        SectionLabel("Особые")
                        Group { special.forEach { row(it, false) } }
                    }
                    if (withAds.isNotEmpty()) {
                        SectionLabel("Реклама на YouTube есть", if (settings.excludeAds) "автопилот не выбирает" else null)
                        Group { withAds.sortedBy { groupKey(it) }.forEach { row(it, false) } }
                    }
                }
                ServerTab.Lte -> {
                    val lte = groups.filter { g -> g.nodes.any { ServerInfo.isLte(it.name) } }.sortedBy { groupKey(it) }
                    SectionLabel("Обход ограничений мобильной сети", "пинг сейчас")
                    Group {
                        if (lte.isEmpty()) {
                            Text("В вашей подписке нет LTE-серверов", color = Apex.TextDim, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
                        } else {
                            lte.forEach { row(it, false) }
                        }
                    }
                }
            }
            Caption("★ — добавить в избранное. Метки протоколов выбирают конкретное подключение. Пинг — настоящая задержка через сервер.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun toggleAutopilot(viewModel: MainViewModel, autopilotOn: Boolean, fallbackTag: String?) {
    if (autopilotOn) {
        fallbackTag?.let { viewModel.select(it) }
    } else {
        viewModel.select(AUTO_TAG)
    }
}

@Composable
private fun GroupRow(
    group: ServerGroup,
    pings: Map<String, Int>,
    selectedTag: String?,
    fastest: Boolean,
    favorite: Boolean,
    onPick: (Node) -> Unit,
    onFavorite: () -> Unit,
) {
    val best = group.nodes.first()
    val selectedNode = group.nodes.firstOrNull { it.tag == selectedTag }
    val ping = (selectedNode ?: best).let { pings[it.tag] }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selectedNode != null) Apex.SurfaceHigh else Apex.Surface)
            .clickable { onPick(best) }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FlagCircle(group.flag)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // Название целиком, до двух строк — без обрезки «Германи…»
                Text(
                    group.title,
                    color = Apex.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val shownNode = selectedNode ?: best
                if (shownNode.description.isNotBlank()) {
                    Text(
                        shownNode.description,
                        color = if (shownNode.ads) Apex.Warn else Apex.TextDim,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val info = listOf(shownNode.category, shownNode.detail).filter { it.isNotBlank() }.joinToString(" · ")
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (fastest) {
                        Tag("Быстрый")
                        Spacer(Modifier.width(6.dp))
                    }
                    if (group.nodes.any { ServerInfo.isLte(it.name) }) {
                        Tag("LTE", color = Apex.Warn, outlined = true)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(info, color = Apex.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            val text = when (ping) {
                null -> "…"
                Pinger.UNREACHABLE -> "нет ответа"
                else -> "$ping мс"
            }
            Text(text, color = pingColor(ping ?: -1), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (favorite) "Убрать из избранного" else "В избранное",
                    tint = if (favorite) Apex.Warn else Apex.TextDim,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // 23. Выбор протокола, если у сервера несколько подключений
        if (group.nodes.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = 54.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val counts = mutableMapOf<String, Int>()
                group.nodes.forEach { n ->
                    val base = Protocols.shortName(n.category)
                    val idx = (counts[base] ?: 0) + 1
                    counts[base] = idx
                    val label = if (group.nodes.count { Protocols.shortName(it.category) == base } > 1) "$base $idx" else base
                    val on = n.tag == selectedTag
                    Text(
                        label,
                        color = if (on) androidx.compose.ui.graphics.Color.White else Apex.AccentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Apex.Accent else Apex.AccentSoft)
                            .clickable { onPick(n) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Apex.Accent else Apex.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (selected) androidx.compose.ui.graphics.Color.White else Apex.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            color = if (selected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f) else Apex.TextDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Apex.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Сегментированный переключатель-«пилюля». */
@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Apex.Surface)
            .padding(5.dp),
    ) {
        items.forEachIndexed { i, title ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Apex.SurfaceHigh else Apex.Surface)
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
