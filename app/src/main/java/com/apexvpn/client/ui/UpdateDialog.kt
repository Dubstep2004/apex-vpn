package com.apexvpn.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Диалог обновления: доступно → загрузка → установка. */
@Composable
fun UpdateDialog(viewModel: MainViewModel) {
    val state by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        is UpdateState.Failed -> s.info ?: return
        else -> return
    }
    // «Позже» запоминаем для этой версии, кроме обязательных обновлений
    if (state is UpdateState.Available && !info.mandatory && Prefs.dismissedUpdateCode == info.versionCode) return

    val canDismiss = !info.mandatory && state !is UpdateState.Downloading

    AlertDialog(
        onDismissRequest = {
            if (canDismiss) {
                Prefs.dismissedUpdateCode = info.versionCode
                UpdateManager.dismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = canDismiss, dismissOnClickOutside = canDismiss),
        containerColor = Apex.Surface,
        title = { Text("Обновление ${info.versionName}", color = Apex.Text) },
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
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Apex.Accent,
                            trackColor = Apex.Outline,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Загрузка ${(s.progress * 100).toInt()}%", color = Apex.TextDim, fontSize = 12.sp)
                    }
                    is UpdateState.Failed -> {
                        Spacer(Modifier.height(10.dp))
                        Text(s.message, color = Apex.Bad, fontSize = 13.sp)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            val colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = androidx.compose.ui.graphics.Color.White)
            when (val s = state) {
                is UpdateState.Available, is UpdateState.Failed ->
                    Button(onClick = { viewModel.downloadUpdate(info) }, colors = colors) {
                        Text(if (s is UpdateState.Failed) "Повторить" else "Обновить")
                    }
                is UpdateState.ReadyToInstall ->
                    Button(
                        onClick = {
                            if (UpdateManager.needsInstallPermission(context)) {
                                viewModel.showMessage("Разрешите установку из APEX VPN и нажмите «Установить» снова")
                                UpdateManager.openInstallPermissionSettings(context)
                            } else {
                                UpdateManager.install(context, s.file)
                            }
                        },
                        colors = colors,
                    ) { Text("Установить") }
                else -> Unit
            }
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
