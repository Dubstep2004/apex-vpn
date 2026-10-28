package com.apexvpn.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.update.UpdateInfo
import com.apexvpn.client.update.UpdateManager
import com.apexvpn.client.update.UpdateState
import com.apexvpn.client.ui.theme.Apex

/** Диалог обновления: новая версия → страница на GitHub (без загрузки в приложении). */
@Composable
fun UpdateDialog(viewModel: MainViewModel) {
    val state by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Failed -> s.info ?: return
        else -> return
    }
    // «Позже» запоминаем для этой версии, кроме обязательных обновлений
    if (state is UpdateState.Available && !info.mandatory && Prefs.dismissedUpdateCode == info.versionCode) return

    val canDismiss = !info.mandatory

    AlertDialog(
        onDismissRequest = {
            if (canDismiss) {
                Prefs.dismissedUpdateCode = info.versionCode
                UpdateManager.dismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = canDismiss, dismissOnClickOutside = canDismiss),
        containerColor = Apex.Dialog,
        title = { Text("Доступна версия ${info.versionName}", color = Apex.Text) },
        text = {
            Column {
                Text(
                    "Установлена версия ${BuildConfig.VERSION_NAME}" +
                        if (info.mandatory) ". Это обновление обязательно." else "",
                    color = Apex.TextDim,
                    fontSize = 13.sp,
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        info.changelog,
                        color = Apex.Text,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Откроется страница версии на GitHub — скачайте там файл apex-vpn-${info.versionName}.apk и установите.",
                    color = Apex.TextDim,
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    runCatching { UpdateManager.openPage(context, info) }
                        .onFailure { viewModel.showMessage("Не удалось открыть браузер") }
                    if (canDismiss) {
                        Prefs.dismissedUpdateCode = info.versionCode
                        UpdateManager.dismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
            ) { Text("Открыть на GitHub") }
        },
        dismissButton = {
            if (canDismiss) {
                TextButton(onClick = {
                    Prefs.dismissedUpdateCode = info.versionCode
                    UpdateManager.dismiss()
                }) { Text("Позже", color = Apex.TextDim) }
            }
        },
    )
}
