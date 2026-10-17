package com.shopmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalSettings provides vm.settings) {
                AppTheme(vm.settings) { AppRoot(vm) }
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides cs.onBackground) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            if (!vm.auth.loggedIn) AuthScreen(vm) else Shell(vm)
        }
    }
}

private val tabs = listOf(
    Icons.Rounded.Dashboard to "خانه",
    Icons.Rounded.Inventory2 to "محصولات",
    Icons.Rounded.Category to "دسته‌ها",
    Icons.Rounded.ReceiptLong to "سفارش",
    Icons.Rounded.People to "مشتری",
    Icons.Rounded.Settings to "تنظیمات"
)

@Composable
fun FloatingBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(62.dp)
            .shadow(16.dp, shape).clip(shape)
            .background(cs.surface.copy(alpha = .97f))
            .border(1.dp, cs.outlineVariant, shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { i, (icon, label) ->
            val on = i == sel
            val w by animateFloatAsState(if (on) 1.9f else 1f, label = "w")
            Box(
                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.background(accentBrush()) else Modifier)
                    .clickable { onSel(i) },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                    AnimatedVisibility(visible = on) {
                        Text(" $label", color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Shell(vm: AppViewModel) {
    val woo = vm.woo
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = woo.tab != 0) { woo.tab = 0 }
    LaunchedEffect(woo.connected) { if (woo.connected) woo.bootstrap() }
    LaunchedEffect(woo.message) {
        if (woo.message != null) {
            delay(4000)
            woo.message = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = woo.tab, modifier = Modifier.fillMaxSize().statusBarsPadding(), label = "screen") { t ->
            if (t == 5) SettingsScreen(vm)
            else if (!woo.connected) ConnectScreen(woo)
            else when (t) {
                0 -> DashboardTab(woo)
                1 -> ProductsTab(woo)
                2 -> CategoriesTab(woo)
                3 -> OrdersTab(woo)
                else -> CustomersTab(woo)
            }
        }
        Column(
            Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            woo.busy?.let { b ->
                BannerBox {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(b, fontSize = 13.sp)
                }
            }
            woo.message?.let { m ->
                BannerBox(onClick = { woo.message = null }) { Text(m, fontSize = 13.sp, modifier = Modifier.weight(1f)) }
            }
            if (woo.undoList.isNotEmpty() && woo.busy == null) {
                BannerBox {
                    Text("تغییرات اعمال شد", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "بازگردانی", color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                        modifier = Modifier.clickable { woo.undo() }.padding(6.dp)
                    )
                }
            }
        }
        FloatingBar(
            sel = woo.tab,
            onSel = { woo.tab = it },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

@Composable
private fun BannerBox(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().shadow(10.dp, shape).clip(shape)
            .background(cs.surfaceVariant).border(1.dp, cs.primary.copy(alpha = .4f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content
    )
}
