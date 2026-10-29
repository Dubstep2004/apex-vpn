package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.AutoModes
import com.apexvpn.client.ui.theme.Apex

/** Режимы автопилота плитками 3×2 (страница «Профиль подключения»). */
@Composable
fun AutoModeGrid(current: String, onPick: (AutoModes.Mode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AutoModes.Mode.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val on = m.id == current
                    Column(
                        Modifier
                            .weight(1f)
                            .apexCard(if (on) Apex.SelectedCard else Apex.Surface)
                            .border(2.dp, if (on) Apex.Accent else Color.Transparent, RoundedCornerShape(Apex.Radius))
                            .clickable { onPick(m) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(m.emoji, fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(m.title, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(m.subtitle, color = Apex.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp, maxLines = 3)
                    }
                }
            }
        }
    }
}

/** Режимы автопилота строкой (шторка серверов). */
@Composable
fun AutoModeChips(current: String, onPick: (AutoModes.Mode) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoModes.Mode.entries.forEach { m ->
            val on = m.id == current
            Text(
                "${m.emoji} ${m.title}",
                color = if (on) Apex.OnAccent else Apex.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Apex.Accent else Apex.Surface)
                    .clickable { onPick(m) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
