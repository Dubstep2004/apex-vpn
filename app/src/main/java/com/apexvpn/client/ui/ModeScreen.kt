package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.BankApps
import com.apexvpn.client.data.RouteMode
import com.apexvpn.client.data.ServiceCatalog
import com.apexvpn.client.ui.theme.Apex

/** «Режим»: как маршрутизировать трафик, какие сервисы пускать через VPN. */
@Composable
fun ModeScreen(viewModel: MainViewModel, onOpenPage: (Page) -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ScreenTitle("Режим", Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(18.dp))

        RouteMode.entries.forEach { mode ->
            ModeCard(mode, selected = s.mode == mode) { viewModel.change { copy(mode = mode) } }
            Spacer(Modifier.height(10.dp))
        }

        SectionLabel("Через VPN")
        Group {
            ServiceCatalog.all.forEachIndexed { i, item ->
                if (i > 0) GroupDivider()
                val on = item.id in s.services
                ListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    leading = { GlyphTile(item.glyph, Color(item.color)) },
                    onClick = { viewModel.change { copy(services = if (on) services - item.id else services + item.id) } },
                ) {
                    ApexSwitch(on, { v -> viewModel.change { copy(services = if (v) services + item.id else services - item.id) } })
                }
            }
        }
        Caption("Выключенные идут напрямую, без VPN")

        SectionLabel("Свои сайты")
        SiteLists(s.customDomains, s.directDomains, viewModel)

        Spacer(Modifier.height(22.dp))
        Group {
            ListRow(
                title = "Блокировка рекламы",
                subtitle = "Реклама и трекеры не загружаются",
                leading = { IconTile(Color(0xFFE5484D)) { Icon(Icons.Rounded.Block, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(adBlock = !adBlock) } },
            ) { ApexSwitch(s.adBlock, { v -> viewModel.change { copy(adBlock = v) } }) }
            GroupDivider()
            ListRow(
                title = "Банки не увидят VPN",
                subtitle = BankApps.SUMMARY,
                leading = { IconTile(Color(0xFF1F8A5B)) { Icon(Icons.Rounded.AccountBalance, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(hideFromBanks = !hideFromBanks) } },
            ) { ApexSwitch(s.hideFromBanks, { v -> viewModel.change { copy(hideFromBanks = v) } }) }
            GroupDivider()
            ListRow(
                title = "Проверить сайт",
                subtitle = "Узнать, пойдёт ли сайт через VPN",
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Search, null, tint = Apex.Cyan) } },
                onClick = { onOpenPage(Page.SiteCheck) },
            )
        }
        Caption("Режим меняется сразу, без переподключения.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeCard(mode: RouteMode, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Apex.SelectedCard else Apex.Surface)
            .border(1.5.dp, if (selected) Apex.Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.title, color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(mode.subtitle, color = Apex.TextDim, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Apex.Accent else Apex.TextDim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Apex.Accent), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

/** Свои сайты: «всегда через VPN» и «всегда напрямую» (8). */
@Composable
private fun SiteLists(viaVpn: List<String>, direct: List<String>, viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val toDirect = tab == 1
    val submit = {
        error = viewModel.addDomain(text, toDirect)
        if (error == null) text = ""
    }
    Segmented(listOf("Через VPN", "Напрямую"), tab) { tab = it; error = null }
    Spacer(Modifier.height(10.dp))
    Group {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, if (error != null) Apex.Bad else Apex.Outline, RoundedCornerShape(20.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(if (toDirect) "например, kinopoisk.ru" else "например, twitch.tv", color = Apex.TextDim, fontSize = 17.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    textStyle = TextStyle(color = Apex.Text, fontSize = 17.sp),
                    cursorBrush = SolidColor(Apex.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Apex.Accent).clickable { submit() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Добавить", tint = Color.White) }
        }
        error?.let {
            Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        }
        (if (toDirect) direct else viaVpn).forEach { d ->
            GroupDivider()
            ListRow(
                title = d,
                leading = {
                    IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Language, null, tint = if (toDirect) Apex.Good else Apex.Cyan) }
                },
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { viewModel.removeDomain(d) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
            }
        }
    }
    Caption(
        if (toDirect) {
            "Эти сайты всегда открываются напрямую, без VPN — например, российские сервисы, которые не попали в базу."
        } else {
            "Добавленные сайты всегда идут через VPN, вместе с их поддоменами."
        },
    )
}
