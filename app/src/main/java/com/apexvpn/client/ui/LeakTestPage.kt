package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.LeakTest
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.launch

/** 585. Проверка утечек: IP, DNS, IPv6 и WebRTC. */
@Composable
internal fun LeakTestPage(onBack: () -> Unit) {
    val st by LeakTest.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    PageScaffold("Проверка утечек", onBack) {
        Caption("Проверяем, не видят ли сайты, звонки и WebRTC ваш настоящий адрес. Запросы — к Cloudflare, Google STUN и ip-api, только по нажатию; результат остаётся на телефоне.")
        Spacer(Modifier.height(12.dp))
        if (st.items.isNotEmpty()) {
            Group {
                st.items.forEachIndexed { i, item ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        item.title,
                        listOf(item.value, item.note).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null },
                        leading = {
                            if (st.running && item.mark == LeakTest.Mark.Wait) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Apex.Accent)
                            } else {
                                val (icon, tint) = when (item.mark) {
                                    LeakTest.Mark.Ok -> Icons.Rounded.CheckCircle to Apex.Good
                                    LeakTest.Mark.Warn -> Icons.Rounded.Warning to Apex.Warn
                                    LeakTest.Mark.Bad -> Icons.Rounded.Error to Apex.Bad
                                    LeakTest.Mark.Wait -> Icons.Rounded.RadioButtonUnchecked to Apex.TextDim
                                }
                                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
                            }
                        },
                    )
                }
            }
        }
        st.verdict?.let { v ->
            Spacer(Modifier.height(12.dp))
            val good = st.items.none { it.mark == LeakTest.Mark.Bad }
            Text(
                v, color = Apex.Text, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background((if (good) Apex.Good else Apex.Bad).copy(alpha = 0.12f)).padding(16.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { scope.launch { LeakTest.run() } },
            enabled = !st.running,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) { Text(if (st.running) "Проверяем…" else if (st.items.isEmpty()) "Проверить" else "Проверить ещё раз", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(24.dp))
    }
}
