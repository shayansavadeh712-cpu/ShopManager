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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
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
    Icons.Rounded.Storefront to "فروشگاه",
    Icons.Rounded.Settings to "تنظیمات"
)

@Composable
fun FloatingBar(sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(64.dp)
            .shadow(16.dp, shape).clip(shape)
            .background(cs.surface.copy(alpha = .96f))
            .border(1.dp, cs.outlineVariant, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { i, (icon, label) ->
            val on = i == sel
            val w by animateFloatAsState(if (on) 1.6f else 1f, label = "w")
            Box(
                Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .then(if (on) Modifier.background(accentBrush()) else Modifier)
                    .clickable { onSel(i) },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                    AnimatedVisibility(visible = on) {
                        Text(" $label", color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Shell(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = tab, modifier = Modifier.fillMaxSize().statusBarsPadding(), label = "screen") { t ->
            if (t == 0) ShopScreen(vm) else SettingsScreen(vm)
        }
        FloatingBar(
            sel = tab,
            onSel = { tab = it },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding()
        )
    }
}
