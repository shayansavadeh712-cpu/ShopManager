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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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

@Composable
fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) cs.primary else cs.surfaceVariant.copy(alpha = .5f))
                    .border(1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(50))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
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
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
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
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(shape)
            .background(cs.surfaceVariant.copy(alpha = .55f))
            .border(1.dp, cs.outlineVariant, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontSize = 14.sp)
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
    val (bg, fg) = when (kind) {
        BtnKind.PRIMARY -> cs.primary to cs.onPrimary
        BtnKind.TONAL -> cs.primary.copy(alpha = .16f) to cs.primary
        BtnKind.DANGER -> cs.error to cs.onError
        BtnKind.GHOST -> Color.Transparent to cs.onSurface
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.heightIn(min = 46.dp).clip(shape)
            .background(if (enabled) bg else cs.surfaceVariant)
            .then(if (kind == BtnKind.GHOST) Modifier.border(1.dp, cs.outlineVariant, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) fg else cs.outline)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) fg else cs.outline, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun KpiTile(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier = modifier, onClick = onClick, pad = 14.dp) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
        title = { Text(title, fontWeight = FontWeight.Bold) },
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
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) cs.error else cs.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
