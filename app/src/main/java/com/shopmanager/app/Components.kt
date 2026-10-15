package com.shopmanager.app

import android.icu.util.Calendar
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

@Composable
fun heroBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return SolidColor(cs.primaryContainer)
}

@Composable
fun accentBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return SolidColor(cs.primary)
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    pad: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val s = LocalSettings.current
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(s.radius.value.dp)
    val bg: Brush = when (s.cardStyle.value) {
        0 -> Brush.linearGradient(
            listOf(cs.surfaceVariant.copy(alpha = .6f), cs.surfaceVariant.copy(alpha = .22f))
        )
        1 -> SolidColor(cs.surfaceVariant.copy(alpha = .7f))
        else -> SolidColor(Color.Transparent)
    }
    val bc = if (s.cardStyle.value == 0) cs.primary.copy(alpha = .22f) else cs.outlineVariant
    var m = modifier.clip(shape).background(bg).border(1.dp, bc, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(pad), content = content)
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CheckCircle(checked: Boolean, size: Dp = 26.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (checked) cs.primary else Color.Transparent, label = "chk")
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .border(2.dp, if (checked) cs.primary else cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.65f), tint = cs.onPrimary)
    }
}

private fun DrawScope.drawBar(x: Float, w: Float, h: Float, c: Color) {
    if (h <= 0f) return
    drawRoundRect(
        Brush.verticalGradient(listOf(c, c.copy(alpha = .35f)), startY = size.height - h, endY = size.height),
        Offset(x, size.height - h), Size(w, h), CornerRadius(w / 3f)
    )
}

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    values2: List<Float>? = null,
    c1: Color = MaterialTheme.colorScheme.primary,
    c2: Color = MaterialTheme.colorScheme.tertiary,
    height: Dp = 120.dp
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val max = ((values + (values2 ?: emptyList())).maxOrNull() ?: 1f).coerceAtLeast(1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values, values2) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size
            if (n == 0) return@Canvas
            for (g in 0..2) {
                val y = size.height * g / 2f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val slot = size.width / n
            val two = values2 != null
            val gap = 3.dp.toPx()
            val bw = if (two) slot * .26f else slot * .5f
            val total = if (two) bw * 2 + gap else bw
            for (i in 0 until n) {
                val idx = if (rtl) n - 1 - i else i
                val x0 = slot * i + (slot - total) / 2
                val h1 = size.height * (values[idx] / max) * anim.value
                drawBar(x0, bw, h1, c1)
                if (values2 != null) {
                    val h2 = size.height * (values2[idx] / max) * anim.value
                    drawBar(x0 + bw + gap, bw, h2, c2)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 10.sp, color = labelColor, maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 140.dp
) {
    if (values.size < 2) {
        EmptyState("برای نمودار حداقل دو ثبت لازم است")
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900))
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val mn = values.min()
        val mx = values.max()
        val span = (mx - mn).coerceAtLeast(0.001f)
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { j, v ->
            Offset(
                if (rtl) size.width - j * step else j * step,
                size.height * 0.1f + size.height * 0.8f * (1f - (v - mn) / span)
            )
        }
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (k in 1 until pts.size) {
            val p0 = pts[k - 1]
            val p1 = pts[k]
            val cx = (p0.x + p1.x) / 2f
            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
        val fill = Path()
        fill.addPath(path)
        fill.lineTo(pts.last().x, size.height)
        fill.lineTo(pts.first().x, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .35f * anim.value), Color.Transparent)))
        drawPath(path, color.copy(alpha = anim.value), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { drawCircle(color.copy(alpha = anim.value), 4.dp.toPx(), it) }
    }
}

@Composable
fun RingProgress(
    progress: Float,
    dia: Dp = 220.dp,
    stroke: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val a by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "ring")
    val cs = MaterialTheme.colorScheme
    val track = cs.outlineVariant.copy(alpha = .4f)
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
            drawArc(
                Brush.sweepGradient(listOf(cs.primary, cs.tertiary, cs.primary)),
                -90f, 360f * a, false, Offset(inset, inset), sz,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun MonthHeader(offset: Int, onChange: (Int) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(offset - 1) }) {
            Icon(if (rtl) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft, null)
        }
        Text(
            monthTitle(offset), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onChange(offset + 1) }) {
            Icon(if (rtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
fun MonthGrid(offset: Int, selected: Long?, marks: Set<Long>, onSelect: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val base = remember(offset) { monthBase(offset) }
    val first = remember(offset) { dayOf(base) }
    val dim = base.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = weekIdx(first)
    val t = today()
    Column {
        Row(Modifier.fillMaxWidth()) {
            faWeekShort.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium
                )
            }
        }
        val rows = (lead + dim + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dn = r * 7 + c - lead + 1
                    if (dn in 1..dim) {
                        val d = first + dn - 1
                        val isSel = d == selected
                        val isToday = d == t
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(
                                    when {
                                        isSel -> cs.primary
                                        isToday -> cs.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelect(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                fa(dn), fontSize = 14.sp,
                                color = if (isSel) cs.onPrimary else cs.onSurface,
                                fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal
                            )
                            if (marks.contains(d)) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                                        .size(5.dp).clip(CircleShape)
                                        .background(if (isSel) cs.onPrimary else cs.tertiary)
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun PersianDatePicker(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = initial ?: today()
    var offset by remember { mutableIntStateOf(monthKey(start) - monthKey(today())) }
    var sel by remember { mutableLongStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(sel) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        text = {
            Column {
                MonthHeader(offset) { offset = it }
                MonthGrid(offset, sel, emptySet()) { sel = it }
                Spacer(Modifier.height(6.dp))
                Text(longDay(sel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
