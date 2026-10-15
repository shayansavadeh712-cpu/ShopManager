package com.shopmanager.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    password: Boolean = false
) {
    val s = LocalSettings.current
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                }
            }
        } else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        singleLine = true,
        shape = RoundedCornerShape((s.radius.value * 0.7f).dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun AuthScreen(vm: AppViewModel) {
    val auth = vm.auth
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val register = !auth.hasAccount
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val sc by pulse.animateFloat(
        1f, 1.07f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "sc"
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).imePadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(104.dp).scale(sc).clip(CircleShape).background(accentBrush()),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Storefront, null, Modifier.size(58.dp), tint = cs.onPrimary)
        }
        Spacer(Modifier.height(20.dp))
        Text("مدیریت فروشگاه", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            if (register) "حساب خودت را بساز و شروع کن" else "خوش برگشتی! وارد شو",
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (register) AuthField(name, { name = it }, "نام نمایشی", Icons.Rounded.Person)
            AuthField(user, { user = it }, "نام کاربری", Icons.Rounded.AlternateEmail)
            AuthField(pass, { pass = it }, "رمز عبور", Icons.Rounded.Lock, password = true)
            if (register) AuthField(pass2, { pass2 = it }, "تکرار رمز عبور", Icons.Rounded.Lock, password = true)
        }
        if (err != null) {
            Spacer(Modifier.height(10.dp))
            Text(err!!, color = cs.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(s.radius.value.dp))
                .background(accentBrush())
                .clickable {
                    err = if (register) {
                        if (pass != pass2) "رمزها یکسان نیستند" else auth.register(name, user, pass)
                    } else auth.login(user, pass)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (register) "ساخت حساب" else "ورود",
                color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
        }
        if (!register) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text("رمز را فراموش کردم", color = cs.onSurfaceVariant)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("بازنشانی کامل؟") },
            text = {
                Text("چون همه‌چیز فقط روی همین گوشی ذخیره می‌شود، بازیابی رمز ممکن نیست. با ادامه، حساب و اتصال فروشگاه از این گوشی پاک می‌شود (اطلاعات خود سایت دست‌نخورده می‌ماند).")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.woo.disconnect()
                    auth.deleteAccount()
                    confirmDelete = false
                }) { Text("پاک کن", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
        )
    }
}
