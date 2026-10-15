package com.shopmanager.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val presets = listOf(
    "اخگر" to 14f, "بنفش" to 268f, "اقیانوس" to 212f, "فیروزه" to 186f, "زمرد" to 156f,
    "طلایی" to 45f, "غروب" to 22f, "مرجانی" to 6f, "رز" to 338f, "سرخابی" to 305f
)

@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text(display, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val auth = vm.auth
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var pwDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("تنظیمات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(accentBrush()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(auth.name.take(1), color = cs.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(auth.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("@${auth.username}", color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pwDialog = true }) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تغییر رمز")
                    }
                    Button(
                        onClick = { logoutDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = cs.error)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("خروج", color = Color.White)
                    }
                }
            }
        }
        item {
            AppCard {
                SectionTitle("ظاهر برنامه", Icons.Rounded.Palette)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("حالت نمایش")
                    ChoiceRow(listOf("سیستم", "روشن", "تیره", "آمولد (مشکی)"), s.themeMode.value) { s.themeMode.set(it) }
                    Label("رنگ اصلی")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        presets.forEach { (_, h) ->
                            val on = kotlin.math.abs(s.hue.value - h) < 1f
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(hsv(h, .8f, 1f), hsv(h + 30, .8f, .85f))))
                                    .border(if (on) 3.dp else 0.dp, if (on) cs.onSurface else Color.Transparent, CircleShape)
                                    .clickable { s.hue.set(h) }
                            )
                        }
                    }
                    Label("رنگ دلخواه")
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(
                                Brush.horizontalGradient((0..6).map { hsv(it * 60f, .75f, .95f) })
                            )
                        )
                        Slider(
                            value = s.hue.value, onValueChange = { s.hue.set(it) }, valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                                thumbColor = cs.onSurface
                            )
                        )
                    }
                    SliderRow("گردی گوشه‌ها", s.radius.value.toFloat(), 4f..32f, fa(s.radius.value)) { s.radius.set(it.toInt()) }
                    SliderRow(
                        "اندازهٔ متن", s.fontScale.value, 0.85f..1.3f,
                        fa(String.format(java.util.Locale.US, "%.2f", s.fontScale.value))
                    ) { s.fontScale.set(it) }
                    Label("سبک کارت‌ها")
                    ChoiceRow(listOf("شیشه‌ای", "توپر", "خطی"), s.cardStyle.value) { s.cardStyle.set(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("راست‌چین (فارسی)", Modifier.weight(1f))
                        Switch(checked = s.rtl.value, onCheckedChange = { s.rtl.set(it) })
                    }
                }
            }
        }
        item {
            Text(
                "مدیریت فروشگاه · نسخهٔ ۱٫۰", Modifier.fillMaxWidth(),
                color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }
    }

    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            title = { Text("خروج از حساب؟") },
            text = { Text("اتصال فروشگاه می‌ماند و با ورود دوباره در دسترس است.") },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialog = false
                    auth.logout()
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { logoutDialog = false }) { Text("انصراف") } }
        )
    }
    if (pwDialog) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pwDialog = false },
            title = { Text("تغییر رمز عبور") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthField(old, { old = it }, "رمز فعلی", Icons.Rounded.Lock, password = true)
                    AuthField(new, { new = it }, "رمز جدید", Icons.Rounded.Lock, password = true)
                    if (err != null) Text(err!!, color = cs.error, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    err = auth.changePassword(old, new)
                    if (err == null) {
                        pwDialog = false
                        Toast.makeText(ctx, "رمز تغییر کرد", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { pwDialog = false }) { Text("انصراف") } }
        )
    }
}
