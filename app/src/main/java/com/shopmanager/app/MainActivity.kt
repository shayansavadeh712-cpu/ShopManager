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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.setup(this)
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
        Box(
            Modifier.fillMaxSize().background(cs.background).drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(cs.primary.copy(alpha = .20f), Color.Transparent),
                        center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(cs.tertiary.copy(alpha = .13f), Color.Transparent),
                        center = Offset(0f, size.height * 0.9f), radius = size.width * 1.05f
                    )
                )
            }
        ) {
            if (!vm.auth.loggedIn) AuthScreen(vm) else Shell(vm)
        }
    }
}

private val tabs = listOf(
    Icons.Rounded.Dashboard to "خانه",
    Icons.Rounded.Inventory2 to "محصولات",
    Icons.Rounded.ReceiptLong to "سفارش‌ها",
    Icons.Rounded.GridView to "بیشتر"
)

private fun barIndex(t: Int) = when (t) { 0 -> 0; 1 -> 1; 3 -> 2; else -> 3 }
private fun barTab(i: Int) = when (i) { 0 -> 0; 1 -> 1; 2 -> 3; else -> 6 }

@Composable
fun FloatingBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier.padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth().height(72.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(cs.surface)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .4f))),
                shape
            )
            .padding(horizontal = 6.dp)
    ) {
        tabs.forEachIndexed { i, (icon, label) ->
            val on = i == sel
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).clickable { onSel(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(42.dp)
                        .then(
                            if (on) Modifier.shadow(12.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary)
                                .clip(CircleShape).background(accentBrush())
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                }
                Text(
                    label, fontSize = 10.sp, maxLines = 1,
                    color = if (on) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SubScreen(woo: WooState, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(50))
                .clickable { woo.tab = 6 }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("بازگشت", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun Shell(vm: AppViewModel) {
    val woo = vm.woo
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = woo.tab != 0) { woo.tab = if (woo.tab == 2 || woo.tab == 4 || woo.tab == 5) 6 else 0 }
    LaunchedEffect(woo.connected) { if (woo.connected) woo.bootstrap() }
    LaunchedEffect(woo.message) {
        if (woo.message != null) {
            delay(4000)
            woo.message = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = woo.tab, modifier = Modifier.fillMaxSize().statusBarsPadding(), label = "screen") { t ->
            if (!woo.connected && t != 5 && t != 6) ConnectScreen(woo)
            else when (t) {
                0 -> DashboardTab(woo)
                1 -> ProductsTab(woo)
                3 -> OrdersTab(woo)
                6 -> MoreTab(woo)
                2 -> SubScreen(woo) { CategoriesTab(woo) }
                4 -> SubScreen(woo) { CustomersTab(woo) }
                else -> SubScreen(woo) { SettingsScreen(vm) }
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
            sel = barIndex(woo.tab),
            onSel = { woo.tab = barTab(it) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}

@Composable
private fun BannerBox(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
            .background(cs.surface).border(1.dp, cs.primary.copy(alpha = .45f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
