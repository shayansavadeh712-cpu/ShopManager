package com.shopmanager.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ───────────────────────── تصویر از اینترنت ─────────────────────────

private object ImgCache {
    val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
}

private fun loadBitmap(url: String, target: Int): Bitmap? {
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        val bytes = c.inputStream.use { it.readBytes() }
        c.disconnect()
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, target: Int = 240) {
    val cs = MaterialTheme.colorScheme
    var bmp by remember(url) { mutableStateOf<Bitmap?>(ImgCache.cache.get(url)) }
    LaunchedEffect(url) {
        if (url.isNotBlank() && bmp == null) {
            val b = withContext(Dispatchers.IO) { loadBitmap(url, target) }
            if (b != null) {
                ImgCache.cache.put(url, b)
                bmp = b
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(), contentDescription = null,
            modifier = modifier, contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier.background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ImageIcon, null, tint = cs.outline)
        }
    }
}

// ───────────────────────── اجزای عمومی ─────────────────────────

/** رنگ فرورفتهٔ پشت فیلدها و چیپ‌های غیرفعال */
@Composable
fun insetColor(): Color {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return lerp(cs.surfaceVariant, Color.Black, if (dark) 0.26f else 0.05f)
}

@Composable
fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val inset = insetColor()
    val shape = RoundedCornerShape(50)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier
                    .then(if (on) Modifier.shadow(8.dp, shape, ambientColor = cs.primary, spotColor = cs.primary) else Modifier)
                    .clip(shape)
                    .background(if (on) accentBrush() else SolidColor(inset))
                    .border(1.dp, if (on) Color.Transparent else cs.outlineVariant.copy(alpha = .7f), shape)
                    .clickable { onSelect(v) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    label, fontSize = 13.sp, maxLines = 1,
                    color = if (on) cs.onPrimary else cs.onSurface,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    password: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val inset = insetColor()
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine, minLines = minLines,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = inset, unfocusedContainerColor = inset,
            focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outlineVariant.copy(alpha = .6f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(shape)
            .background(insetColor())
            .border(1.dp, cs.outlineVariant.copy(alpha = .7f), shape)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(cs.primary.copy(alpha = .16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Search, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, null, tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable { onChange(""); onSearch() }
            )
        }
    }
}

enum class BtnKind { PRIMARY, TONAL, DANGER, GHOST }

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.PRIMARY,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val fg = when (kind) {
        BtnKind.PRIMARY -> cs.onPrimary
        BtnKind.TONAL -> cs.primary
        BtnKind.DANGER -> cs.onError
        BtnKind.GHOST -> cs.onSurface
    }
    var m: Modifier = modifier.heightIn(min = 48.dp)
    m = when (kind) {
        BtnKind.PRIMARY ->
            if (enabled) m.shadow(10.dp, shape, ambientColor = cs.primary, spotColor = cs.primary).clip(shape).background(accentBrush())
            else m.clip(shape).background(cs.surfaceVariant)
        BtnKind.TONAL -> m.raised(shape)
        BtnKind.DANGER -> m.clip(shape).background(if (enabled) cs.error else cs.surfaceVariant)
        BtnKind.GHOST -> m.clip(shape).border(1.dp, cs.outlineVariant, shape)
    }
    Row(
        m.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp), tint = if (enabled) fg else cs.outline)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = if (enabled) fg else cs.outline, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun KpiTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier = modifier, onClick = onClick, pad = 16.dp) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .16f))
                .border(1.dp, color.copy(alpha = .30f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "sk")
    val a by tr.animateFloat(
        .25f, .6f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a)))
}

@Composable
fun SkeletonList(count: Int = 6) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { Skeleton(Modifier.fillMaxWidth().height(78.dp)) }
    }
}

/** پنجرهٔ تأیید؛ تغییر فقط پس از زدن دکمهٔ تأیید اعمال می‌شود. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String = "",
    confirmText: String = "تأیید و اعمال",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (text.isNotBlank()) Text(text, color = cs.onSurfaceVariant)
                content?.invoke(this)
                Text("آیا مطمئنی؟", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            ActionButton(confirmText, onConfirm, kind = if (danger) BtnKind.DANGER else BtnKind.PRIMARY)
        },
        dismissButton = { ActionButton("انصراف", onDismiss, kind = BtnKind.GHOST) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 6.dp).width(44.dp).height(5.dp)
                    .clip(RoundedCornerShape(50)).background(cs.outlineVariant)
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<WCat>,
    selected: List<Long>,
    onChange: (List<Long>) -> Unit,
    single: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    SearchField(q, { q = it }, "جستجوی دسته...")
    val list = if (q.isBlank()) categories else categories.filter { it.name.contains(q.trim(), true) }
    if (categories.isEmpty()) {
        Text("دسته‌ای پیدا نشد", color = cs.onSurfaceVariant)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { c ->
            val on = selected.contains(c.id)
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (on) cs.primary else cs.surfaceVariant.copy(alpha = .5f))
                    .clickable {
                        if (single) onChange(if (on) emptyList() else listOf(c.id))
                        else onChange(if (on) selected - c.id else selected + c.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    (if (q.isBlank() && c.depth > 0) "↳ " else "") + c.name,
                    fontSize = 13.sp, color = if (on) cs.onPrimary else cs.onSurface
                )
            }
        }
    }
}

/** بخش جمع‌شونده: عنوان + خلاصه، با لمس باز می‌شود. */
@Composable
fun Section(title: String, summary: String = "", startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(startOpen) }
    AppCard(pad = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                if (summary.isNotBlank()) Text(summary, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant)
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}
