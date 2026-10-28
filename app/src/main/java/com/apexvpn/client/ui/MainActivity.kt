package com.apexvpn.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.Changelog
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.ui.theme.ApexTheme
import com.apexvpn.client.ui.theme.applyPalette
import com.apexvpn.client.ui.theme.paletteFor
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.vpn.VpnToggle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Tab(val title: String, val icon: ImageVector) {
    Home("VPN", Icons.Rounded.PowerSettingsNew),
    Mode("Режим", Icons.Rounded.Public),
    Settings("Настройки", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {

    companion object {
        /** Из уведомления «обновление готово» — сразу открыть установщик */
        const val EXTRA_INSTALL_UPDATE = "install_update"
    }

    private val viewModel: MainViewModel by viewModels()

    /** Ссылка подписки из QR-кода, «Поделиться» или apexvpn:// — ждёт подтверждения. */
    private val pendingImport = MutableStateFlow<String?>(null)

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ApexVpnService.start(this)
        } else {
            viewModel.showMessage("Без разрешения на VPN подключиться нельзя")
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { connectNow() }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        val link = Subscription.extractLink(text)
        if (link != null) pendingImport.value = link else viewModel.showMessage("В QR-коде нет ссылки на sub.knownhosts.ru")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тема: палитра и цвет значков строки состояния меняются вместе с настройками
        lifecycleScope.launch {
            AppSettings.state.collect { s ->
                val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                // Material Expressive: акцент из обоев (Android 12+)
                val wallpaper = if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_400)) }.getOrNull()
                } else null
                val p = paletteFor(s.design, s.theme, s.accent, systemDark, wallpaper)
                applyPalette(p)
                com.apexvpn.client.ui.theme.applyFeel(
                    com.apexvpn.client.ui.theme.Feel(s.vibration, s.smoothMode, s.glassBlur, s.liveMap),
                )
                val style = if (p.dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
        }
        handleIntent(intent)

        setContent {
            ApexTheme {
                Surface(Modifier.fillMaxSize(), color = Apex.Base) {
                    com.apexvpn.client.ui.theme.DesignBackground()
                    val sub by viewModel.subscription.collectAsStateWithLifecycle()
                    val message by viewModel.message.collectAsStateWithLifecycle()
                    val importLink by pendingImport.collectAsStateWithLifecycle()
                    val snackbar = remember { SnackbarHostState() }
                    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                    var page by rememberSaveable { mutableStateOf<Page?>(null) }
                    var serversOpen by rememberSaveable { mutableStateOf(false) }
                    var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }

                    LaunchedEffect(message) {
                        message?.let {
                            snackbar.showSnackbar(it)
                            viewModel.consumeMessage()
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        if (sub.url.isEmpty()) {
                            SetupScreen(viewModel, onScanQr = { scanQr() })
                        } else {
                            BackHandler(enabled = page == null && tab != Tab.Home) { tab = Tab.Home }
                            Column(Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    modifier = Modifier.weight(1f),
                                    label = "tab",
                                ) { current ->
                                    when (current) {
                                        Tab.Home -> HomeScreen(
                                            viewModel = viewModel,
                                            onToggle = { toggleVpn() },
                                            onOpenServers = { serversOpen = true },
                                            onOpenMode = { tab = Tab.Mode },
                                            onOpenPage = { page = it },
                                        )
                                        Tab.Mode -> ModeScreen(viewModel, onOpenPage = { page = it })
                                        Tab.Settings -> SettingsScreen(viewModel, onOpenPage = { page = it }, onScanQr = { scanQr() })
                                    }
                                }
                                BottomBar(tab) { tab = it }
                            }
                            // Страницы поверх вкладок
                            AnimatedVisibility(
                                visible = page != null,
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                val current = page
                                if (current != null) {
                                    BackHandler { page = null }
                                    PageHost(current, viewModel) { page = null }
                                }
                            }
                            if (serversOpen) {
                                ServerSheet(viewModel) { serversOpen = false }
                            }
                        }
                        SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
                        )
                        // 63. Анимированная заставка
                        AnimatedVisibility(visible = splash, exit = fadeOut()) {
                            SplashScreen { splash = false }
                        }
                    }

                    importLink?.let { link ->
                        AlertDialog(
                            onDismissRequest = { pendingImport.value = null },
                            containerColor = Apex.Dialog,
                            title = { Text("Добавить подписку?", color = Apex.Text) },
                            text = { Text(maskLink(link), color = Apex.TextDim) },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingImport.value = null
                                    viewModel.setSubscription(link) { err -> viewModel.showMessage(err ?: "Подписка добавлена") }
                                }) { Text("Добавить", color = Apex.Accent) }
                            },
                            dismissButton = { TextButton(onClick = { pendingImport.value = null }) { Text("Отмена", color = Apex.TextDim) } },
                        )
                    }
                    // «Что нового» один раз после обновления приложения
                    var whatsNew by remember { mutableStateOf(Changelog.unseen(BuildConfig.VERSION_CODE)) }
                    if (whatsNew.isNotEmpty() && !splash && sub.url.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { Changelog.markSeen(BuildConfig.VERSION_CODE); whatsNew = emptyList() },
                            containerColor = Apex.Dialog,
                            title = { Text("Обновлено до ${BuildConfig.VERSION_NAME}", color = Apex.Text) },
                            text = {
                                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    whatsNew.forEach { r ->
                                        Text("Версия ${r.version}", color = Apex.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        com.apexvpn.client.ui.ChangeList(r.changes, textColor = Apex.TextDim)
                                        Spacer(Modifier.height(10.dp))
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { Changelog.markSeen(BuildConfig.VERSION_CODE); whatsNew = emptyList() }) {
                                    Text("Отлично", color = Apex.Accent)
                                }
                            },
                        )
                    }
                    UpdateDialog(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 32. apexvpn://import?url=…, «Поделиться» ссылкой, запуск из плитки/виджета. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) {
            intent.removeExtra(EXTRA_INSTALL_UPDATE)
            lifecycleScope.launch {
                com.apexvpn.client.update.UpdateManager.check(silent = true)
                val s = com.apexvpn.client.update.UpdateManager.state.value
                runCatching {
                    com.apexvpn.client.update.UpdateManager.openPage(
                        this@MainActivity,
                        (s as? com.apexvpn.client.update.UpdateState.Available)?.info,
                    )
                }
            }
        }
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val link = Subscription.extractLink(text)
        if (link != null) {
            pendingImport.value = link
        } else if (text != null) {
            viewModel.showMessage("Поддерживаются только ссылки sub.knownhosts.ru")
        }
        // 9/16. Ярлыки и голосовые команды
        when (intent.action) {
            com.apexvpn.client.vpn.Shortcuts.ACTION_CONNECT -> {
                connectFromShortcut(intent.getStringExtra(com.apexvpn.client.vpn.Shortcuts.EXTRA_SERVER))
                intent.action = null
                return
            }
            com.apexvpn.client.vpn.Shortcuts.ACTION_DISCONNECT -> {
                ApexVpnService.stop(this)
                intent.action = null
                return
            }
            com.apexvpn.client.vpn.Shortcuts.ACTION_FEATURE -> {
                val feature = intent.getStringExtra("feature").orEmpty().lowercase()
                if (listOf("выкл", "откл", "disconnect", "стоп").any { it in feature }) ApexVpnService.stop(this) else connectFromShortcut(null)
                intent.action = null
                return
            }
        }
        if (intent.getBooleanExtra(VpnToggle.EXTRA_CONNECT, false) && VpnState.status.value == VpnStatus.Stopped &&
            Subscription.state.value.nodes.isNotEmpty()
        ) {
            intent.removeExtra(VpnToggle.EXTRA_CONNECT)
            toggleVpn()
        }
    }

    /** Подключиться к серверу по названию из ярлыка ("auto" — автопилот, null — текущий). */
    private fun connectFromShortcut(server: String?) {
        val nodes = Subscription.state.value.nodes
        if (nodes.isEmpty()) return
        when {
            server == null -> Unit
            server == com.apexvpn.client.data.AUTO_TAG -> viewModel.select(com.apexvpn.client.data.AUTO_TAG)
            else -> nodes.firstOrNull { com.apexvpn.client.data.ServerInfo.title(it.name) == server }
                ?.let { viewModel.select(it.tag) }
                ?: viewModel.showMessage("Сервера «$server» больше нет в подписке — подключаю автопилот")
        }
        if (VpnState.status.value == VpnStatus.Stopped) toggleVpn()
    }

    private fun scanQr() {
        qrScanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код подписки")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }

    private fun toggleVpn() {
        when (VpnState.status.value) {
            VpnStatus.Started, VpnStatus.Starting, VpnStatus.Paused, VpnStatus.Blocked -> ApexVpnService.stop(this)
            VpnStatus.Stopping -> Unit
            VpnStatus.Stopped -> {
                VpnState.clearError()
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    connectNow()
                }
            }
        }
    }

    private fun connectNow() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else ApexVpnService.start(this)
    }
}

private fun maskLink(url: String): String {
    val idx = url.indexOf('/', "https://".length)
    if (idx < 0) return url
    val path = url.substring(idx + 1)
    return url.substring(0, idx + 1) + if (path.length > 6) path.take(3) + "•••" + path.takeLast(3) else "•••"
}

/** Нижняя навигация: плавающая «пилюля» с тремя вкладками. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Apex.Bg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Apex.Surface)
                .border(1.dp, Apex.CardBorder, RoundedCornerShape(28.dp))
                .padding(6.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) Apex.SurfaceHigh else Apex.Surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(t) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, tint = if (selected) Apex.Accent else Apex.TextDim, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t.title,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Apex.Text else Apex.TextDim,
                    )
                }
            }
        }
    }
}
