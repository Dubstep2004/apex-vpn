package com.apexvpn.client.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.theme.Apex

/** Подпись для строки настроек: какой User-Agent используется. */
fun userAgentSummary(): String {
    val custom = Prefs.customUserAgent.trim()
    if (custom.isNotEmpty()) return "Свой: $custom"
    val working = Prefs.workingUserAgent
    return if (working.isNotEmpty()) "Авто (сработал $working)" else "Авто — подбирается сам"
}

/** Диалог «Свой User-Agent» для запроса подписки. */
@Composable
fun UserAgentDialog(onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    var value by remember { mutableStateOf(Prefs.customUserAgent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apex.Surface,
        title = { Text("User-Agent подписки", color = Apex.Text) },
        text = {
            Column {
                Text(
                    "Как приложение представляется серверу подписки. Если сервер не отдаёт серверы, " +
                        "впишите User-Agent приложения, в котором подписка работает. Пусто — подбирается автоматически.",
                    color = Apex.TextDim,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.replace("\n", "") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("User-Agent") },
                    placeholder = { Text("например, Happ/3.7.0") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Apex.Accent,
                        unfocusedBorderColor = Apex.Outline,
                        cursorColor = Apex.Accent,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Subscription.builtInUserAgents.forEach { ua ->
                        Text(
                            ua.substringBefore('/').substringBefore(' '),
                            color = if (value == ua) Apex.Accent else Apex.Text,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Apex.SurfaceHigh)
                                .clickable { value = ua }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.customUserAgent = value.trim()
                onSaved()
                onDismiss()
            }) { Text("Сохранить", color = Apex.Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    Prefs.customUserAgent = ""
                    onSaved()
                    onDismiss()
                }) { Text("Сбросить", color = Apex.TextDim) }
                TextButton(onClick = onDismiss) { Text("Отмена", color = Apex.TextDim) }
            }
        },
    )
}
