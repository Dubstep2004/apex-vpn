package com.apexvpn.client.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.R
import com.apexvpn.client.ui.theme.Apex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Бренд ──────────────────────────────────────────────────────────────

/** Значок приложения (адаптивную иконку Compose не рисует, собираем из слоёв). */
@Composable
fun ApexLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(size))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size))
    }
}

@Composable
fun ApexWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ApexLogo(32.dp)
        Spacer(Modifier.width(10.dp))
        Text("APEX", fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 2.sp, color = Apex.Text)
        Text(" VPN", fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = 2.sp, color = Apex.Cyan)
    }
}

// ─── Строительные блоки экранов ─────────────────────────────────────────

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = Apex.Text, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp)) {
        Text(text, color = Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Apex.TextDim, fontSize = 14.sp)
    }
}

@Composable
fun Caption(text: String) {
    Text(text, color = Apex.TextDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

/** Скруглённая группа строк, как в настройках iOS/Android 16. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Apex.Surface),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 1.dp, color = Apex.Divider)
}

/** Строка: плашка-значок, заголовок, подзаголовок, справа — что угодно. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Apex.Text,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Apex.TextDim, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Квадратная плашка со значком или символом. */
@Composable
fun IconTile(color: Color, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun GlyphTile(glyph: String, color: Color) {
    IconTile(color) {
        Text(glyph, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ApexSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Apex.Accent,
            checkedBorderColor = Apex.Accent,
            uncheckedThumbColor = Apex.TextDim,
            uncheckedTrackColor = Apex.SurfaceHigh,
            uncheckedBorderColor = Apex.SurfaceHigh,
        ),
    )
}

/** Маленькая плашка-метка: «Быстрый», «LTE», «VLESS». */
@Composable
fun Tag(text: String, color: Color = Apex.AccentText, background: Color = Apex.AccentSoft, outlined: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .then(
                if (outlined) Modifier.background(color.copy(alpha = 0.12f)) else Modifier.background(background),
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Круглый флаг страны: картинка из assets/flags, иначе эмодзи. */
@Composable
fun FlagCircle(flag: String?, size: Dp = 40.dp) {
    val context = LocalContext.current
    val code = remember(flag) { flagCode(flag) }
    val bitmap = remember(code) { code?.let { FlagCache.get(context, it) } }
    Box(
        Modifier.size(size).clip(CircleShape).background(Apex.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.size(size).clip(CircleShape))
        } else {
            Text(flag ?: "🌐", fontSize = (size.value * 0.55f).sp)
        }
    }
}

/** «🇬🇧» → «gb». */
fun flagCode(flag: String?): String? {
    if (flag == null) return null
    val cps = flag.codePoints().toArray()
    if (cps.size != 2 || cps.any { it !in 0x1F1E6..0x1F1FF }) return null
    return cps.joinToString("") { ('a' + (it - 0x1F1E6)).toString() }
}

private object FlagCache {
    private val cache = HashMap<String, ImageBitmap?>()

    @Synchronized
    fun get(context: Context, code: String): ImageBitmap? = cache.getOrPut(code) {
        runCatching {
            context.assets.open("flags/$code.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
}

// ─── Форматирование ─────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return if (i == 0) "$bytes Б" else String.format(Locale("ru"), "%.1f %s", v, units[i])
}

fun formatDate(unixSeconds: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(unixSeconds * 1000))

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun formatAgo(time: Long): String {
    if (time <= 0) return "никогда"
    val min = (System.currentTimeMillis() - time) / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 60 * 24 -> "${min / 60} ч назад"
        else -> "${min / (60 * 24)} дн назад"
    }
}

fun pingColor(ms: Int) = when {
    ms < 0 -> Apex.TextDim
    ms < 150 -> Apex.Good
    ms < 400 -> Apex.Warn
    else -> Apex.Bad
}
