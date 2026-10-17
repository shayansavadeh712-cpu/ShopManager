@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shopmanager.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val AmberColor = Color(0xFFFBBF24)

// ───────────────────────── تصویر از اینترنت (بدون کتابخانهٔ اضافه) ─────────────────────────

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

// ───────────────────────── ابزارهای مشترک ─────────────────────────

@Composable
internal fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (k, label) ->
            FilterChip(selected = k == selected, onClick = { onSelect(k) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    lines: Int = 1
) {
    val s = LocalSettings.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = lines == 1,
        minLines = lines,
        shape = RoundedCornerShape((s.radius.value * 0.7f).dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text)
    )
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = LocalSettings.current
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(s.radius.value.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

// ───────────────────────── صفحهٔ اصلی فروشگاه ─────────────────────────

@Composable
fun ShopScreen(vm: AppViewModel) {
    val w = vm.woo
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<WProduct?>(null) }
    var bulkOpen by remember { mutableStateOf(false) }

    LaunchedEffect(w.connected) { w.bootstrap() }
    LaunchedEffect(w.message) {
        if (w.message != null) {
            delay(4000)
            w.message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!w.connected) {
            ConnectScreen(w)
        } else {
            ShopMain(w, onEdit = { editing = it }, onBulk = { bulkOpen = true })
        }

        val msg = w.message
        val busy = w.busy
        if (busy != null || msg != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(cs.inverseSurface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.inverseOnSurface)
                    Spacer(Modifier.width(10.dp))
                }
                Text(busy ?: msg ?: "", color = cs.inverseOnSurface, modifier = Modifier.weight(1f), fontSize = 13.sp)
                if (busy == null && w.undoList.isNotEmpty()) {
                    TextButton(onClick = { w.undo() }) {
                        Icon(Icons.Rounded.Undo, null, Modifier.size(16.dp), tint = cs.inversePrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("بازگردانی", color = cs.inversePrimary)
                    }
                }
            }
        }
    }

    editing?.let { p -> ProductEditor(w, p) { editing = null } }
    if (bulkOpen) BulkSheet(w) { bulkOpen = false }
}

@Composable
private fun ShopMain(w: WooState, onEdit: (WProduct) -> Unit, onBulk: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("فروشگاه", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    w.siteUrl.removePrefix("https://"), color = cs.onSurfaceVariant,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { w.refreshAll() }) { Icon(Icons.Rounded.Refresh, null) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            Choices(
                listOf(
                    0 to "داشبورد", 1 to "محصولات", 4 to "دسته‌ها", 2 to "سفارش‌ها",
                    5 to "مشتریان", 6 to "مقاله‌ها", 3 to "اتصال"
                ),
                w.shopTab
            ) { w.shopTab = it }
        }
        Spacer(Modifier.height(4.dp))
        when (w.shopTab) {
            0 -> DashboardTab(w, onNew = { onEdit(WProduct()) })
            1 -> ProductsTab(w, onEdit, onBulk)
            2 -> OrdersTab(w)
            4 -> CategoriesTab(w)
            5 -> CustomersTab(w)
            6 -> PostsTab(w)
            else -> ShopSettingsTab(w)
        }
    }
}

// ───────────────────────── اتصال ─────────────────────────

@Composable
private fun ConnectScreen(w: WooState) {
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("تومان") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("فروشگاه", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("ووکامرس سایتت را وصل کن و همه‌چیز را از همین‌جا مدیریت کن.", color = cs.onSurfaceVariant)
        AppCard {
            Text("ساخت کلید API", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "۱) در داشبورد وردپرس: ووکامرس ← تنظیمات ← پیشرفته ← REST API\n" +
                        "۲) «افزودن کلید» را بزن، دسترسی را «خواندن/نوشتن» انتخاب کن\n" +
                        "۳) Consumer Key و Consumer Secret را همین‌جا وارد کن\n" +
                        "سایت باید HTTPS داشته باشد.",
                color = cs.onSurfaceVariant, fontSize = 13.sp, lineHeight = 22.sp
            )
        }
        AuthField(url, { url = it }, "آدرس سایت (مثلاً mysite.com)", Icons.Rounded.Public)
        AuthField(key, { key = it }, "Consumer Key (ck_...)", Icons.Rounded.Key)
        AuthField(secret, { secret = it }, "Consumer Secret (cs_...)", Icons.Rounded.Lock, password = true)
        AuthField(cur, { cur = it }, "واحد پول (مثلاً تومان)", Icons.Rounded.Paid)
        if (err != null) Text(err!!, color = cs.error, fontSize = 13.sp)
        PrimaryButton(if (busy) "در حال تست اتصال..." else "اتصال و تست", enabled = !busy) {
            busy = true
            err = null
            w.connect(url, key, secret, cur) { e ->
                busy = false
                err = e
            }
        }
        Text(
            "کلیدها با Android Keystore رمزنگاری و فقط روی همین گوشی نگه‌داری می‌شوند.",
            color = cs.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

// ───────────────────────── داشبورد ─────────────────────────

@Composable
private fun ShopTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier, onClick = onClick, pad = 14.dp) {
        Icon(icon, null, Modifier.size(20.dp), tint = cs.primary)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun DashboardTab(w: WooState, onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = w.stats
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard {
                Text("فروش این ماه", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text(
                    if (st != null) "${money(st.sales)} ${w.currency}" else "...",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.primary
                )
                if (st != null) {
                    Spacer(Modifier.height(8.dp))
                    Pill("${fa(st.ordersMonth)} سفارش", Icons.Rounded.ReceiptLong)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShopTile(Icons.Rounded.Inventory2, if (st != null) fa(st.products) else "…", "کل محصولات", Modifier.weight(1f)) {
                    w.statusF = "any"; w.stockF = ""; w.catF = 0L; w.query = ""
                    w.shopTab = 1; w.loadProducts()
                }
                ShopTile(Icons.Rounded.Storefront, if (st != null) fa(st.published) else "…", "منتشر شده", Modifier.weight(1f)) {
                    w.statusF = "publish"; w.stockF = ""; w.catF = 0L; w.query = ""
                    w.shopTab = 1; w.loadProducts()
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShopTile(Icons.Rounded.Edit, if (st != null) fa(st.drafts) else "…", "پیش‌نویس", Modifier.weight(1f)) {
                    w.statusF = "draft"; w.stockF = ""; w.catF = 0L; w.query = ""
                    w.shopTab = 1; w.loadProducts()
                }
                ShopTile(Icons.Rounded.Warning, if (st != null) fa(st.outOfStock) else "…", "ناموجود", Modifier.weight(1f)) {
                    w.statusF = "any"; w.stockF = "outofstock"; w.catF = 0L; w.query = ""
                    w.shopTab = 1; w.loadProducts()
                }
            }
        }
        if (st != null && st.orderTotals.isNotEmpty()) {
            item {
                AppCard {
                    SectionTitle("سفارش‌ها بر اساس وضعیت", Icons.Rounded.ReceiptLong)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.orderTotals.forEach { (slug, n) -> Pill("${orderFa(slug)}: ${fa(n)}") }
                    }
                }
            }
        }
        item { PrimaryButton("محصول جدید", onClick = onNew) }
    }
}

// ───────────────────────── محصولات ─────────────────────────

@Composable
private fun ProductsTab(w: WooState, onEdit: (WProduct) -> Unit, onBulk: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(w.query) }
    var catMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val sel = w.selected
    val selMode = sel.isNotEmpty()

    LaunchedEffect(text) {
        if (text != w.query) {
            delay(500)
            w.query = text
            w.loadProducts()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (selMode) 200.dp else 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text("جستجوی نام یا SKU...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (text.isNotEmpty()) {
                            { IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, null) } }
                        } else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)
                    )
                    Choices(
                        listOf("any" to "همه", "publish" to "منتشر شده", "draft" to "پیش‌نویس", "private" to "خصوصی", "pending" to "در انتظار", "trash" to "زباله"),
                        w.statusF
                    ) { w.statusF = it; w.loadProducts() }
                    Choices(
                        listOf("" to "همهٔ موجودی‌ها", "instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"),
                        w.stockF
                    ) { w.stockF = it; w.loadProducts() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(onClick = { catMenu = true }) {
                                Text(w.categories.firstOrNull { it.id == w.catF }?.name ?: "همهٔ دسته‌ها", maxLines = 1)
                            }
                            DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                                DropdownMenuItem(text = { Text("همهٔ دسته‌ها") }, onClick = { catMenu = false; w.catF = 0L; w.loadProducts() })
                                w.categories.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("${c.name} (${fa(c.count)})") },
                                        onClick = { catMenu = false; w.catF = c.id; w.loadProducts() }
                                    )
                                }
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Rounded.SwapVert, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(listOf("جدیدترین", "نام", "ارزان‌ترین", "گران‌ترین", "پرفروش‌ترین")[w.sort])
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf("جدیدترین", "نام", "ارزان‌ترین", "گران‌ترین", "پرفروش‌ترین").forEachIndexed { i, n ->
                                    DropdownMenuItem(text = { Text(n) }, onClick = { sortMenu = false; w.sort = i; w.loadProducts() })
                                }
                            }
                        }
                        Text("${fa(w.total)} محصول", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (!selMode) {
                        TextButton(onClick = { w.products.forEach { if (!sel.contains(it.id)) sel.add(it.id) } }) {
                            Text("انتخاب همه / ویرایش گروهی (یا نگه‌داشتن روی یک محصول)", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (w.error != null) {
                item {
                    AppCard {
                        Text(w.error!!, color = cs.error)
                        TextButton(onClick = { w.loadProducts() }) { Text("تلاش دوباره") }
                    }
                }
            }
            if (w.products.isEmpty() && !w.loading && w.error == null) {
                item { EmptyState("محصولی پیدا نشد") }
            }
            items(w.products, key = { it.id }) { p ->
                ProductRow(
                    p, w.currency, sel.contains(p.id), selMode,
                    onOpen = { onEdit(p) }, onToggle = { w.toggleSelect(p.id) }
                )
            }
            item {
                if (w.loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                } else if (w.page in 1 until w.pages) {
                    LaunchedEffect(w.page, w.loading) { w.loadProducts(false) }
                }
            }
        }

        if (selMode) {
            AppCard(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
                pad = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fa(sel.size)} انتخاب", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { w.products.forEach { if (!sel.contains(it.id)) sel.add(it.id) } }) { Text("همه") }
                    TextButton(onClick = { sel.clear() }) { Text("لغو") }
                    FilledTonalButton(onClick = onBulk) { Text("ویرایش گروهی") }
                }
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = { onEdit(WProduct()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("محصول جدید") },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 100.dp),
                containerColor = cs.primary, contentColor = cs.onPrimary
            )
        }
    }
}

@Composable
private fun ProductRow(
    p: WProduct,
    currency: String,
    selected: Boolean,
    selMode: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val s = LocalSettings.current
    val shape = RoundedCornerShape(s.radius.value.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) cs.primary.copy(alpha = .14f) else cs.surfaceVariant.copy(alpha = .5f))
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .combinedClickable(onClick = { if (selMode) onToggle() else onOpen() }, onLongClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) {
            RemoteImage(p.image, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name.ifBlank { "(بدون نام)" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (p.sku.isNotBlank()) Text("SKU: ${p.sku}", fontSize = 11.sp, color = cs.onSurfaceVariant)
            if (p.type == "variable" || p.type == "grouped") {
                Text("محصول ${if (p.type == "variable") "متغیر" else "گروهی"}", fontSize = 12.sp, color = cs.onSurfaceVariant)
            } else if (p.onSale && p.salePrice.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(priceText(p.regularPrice), fontSize = 12.sp, color = cs.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                    Spacer(Modifier.width(6.dp))
                    Text("${priceText(p.salePrice)} $currency", fontWeight = FontWeight.Bold, color = cs.primary)
                }
            } else {
                Text("${priceText(p.regularPrice.ifBlank { p.price })} $currency", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val sc = when (p.stockStatus) {
                    "instock" -> IncomeColor
                    "outofstock" -> ExpenseColor
                    else -> AmberColor
                }
                Pill(
                    stockFa(p.stockStatus) + if (p.manageStock && p.stockQty != null) " · ${fa(p.stockQty)}" else "",
                    color = sc
                )
                if (p.status != "publish") Pill(statusFa(p.status), color = cs.outline)
                if (p.featured) Pill("ویژه", Icons.Rounded.Star, AmberColor)
            }
        }
    }
}

// ───────────────────────── ویرایشگر محصول ─────────────────────────

@Composable
private fun ProductEditor(w: WooState, initial: WProduct, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var status by remember { mutableStateOf(initial.status) }
    var sku by remember { mutableStateOf(initial.sku) }
    var regular by remember { mutableStateOf(initial.regularPrice) }
    var sale by remember { mutableStateOf(initial.salePrice) }
    var manage by remember { mutableStateOf(initial.manageStock) }
    var qty by remember { mutableStateOf(initial.stockQty?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial.stockStatus) }
    val cats = remember { mutableStateListOf<Long>().also { it.addAll(initial.catIds) } }
    var featured by remember { mutableStateOf(initial.featured) }
    var weight by remember { mutableStateOf(initial.weight) }
    var shortD by remember { mutableStateOf(initial.shortDescription) }
    var desc by remember { mutableStateOf(initial.description) }
    var image by remember { mutableStateOf(initial.image) }
    var type by remember { mutableStateOf(initial.type) }
    val attrs = remember { mutableStateListOf<AttrRow>().also { l -> l.addAll(initial.attrs.map { it.toRow() }) } }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): WProduct = initial.copy(
        name = name.trim(), status = status, sku = sku.trim(),
        regularPrice = en(regular).trim(), salePrice = en(sale).trim(),
        manageStock = manage, stockQty = if (manage) (en(qty).toIntOrNull() ?: 0) else null,
        stockStatus = stock, catIds = cats.toList(), featured = featured,
        weight = en(weight).trim(), shortDescription = shortD, description = desc, image = image.trim(),
        type = type,
        attrs = attrs.map { it.toAttr() }.filter { it.name.isNotBlank() && it.options.isNotEmpty() }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "محصول جدید" else "ویرایش محصول",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isNew) {
                    if (initial.permalink.isNotBlank()) {
                        IconButton(onClick = { uri.openUri(initial.permalink) }) { Icon(Icons.Rounded.OpenInNew, null) }
                    }
                    IconButton(onClick = {
                        w.save(build().copy(id = 0, name = name.trim() + " (کپی)", sku = "", status = "draft"), true) { ok ->
                            if (ok) onDismiss()
                        }
                    }) { Icon(Icons.Rounded.ContentCopy, null) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteForever, null, tint = cs.error)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(image, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)), target = 320)
                Spacer(Modifier.width(12.dp))
                Field(image, { image = it }, "آدرس تصویر (URL)", Modifier.weight(1f))
            }
            Field(name, { name = it }, "نام محصول")
            Text("وضعیت انتشار", fontWeight = FontWeight.Medium)
            Choices(
                listOf("publish" to "منتشر", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"),
                status
            ) { status = it }
            Field(sku, { sku = it }, "SKU (شناسه محصول)")
            if (isNew) {
                Text("نوع محصول", fontWeight = FontWeight.Medium)
                Choices(listOf("simple" to "ساده", "variable" to "متغیر (رنگ، سایز...)"), type) { type = it }
            }
            if (if (isNew) type == "simple" else initial.priceEditable) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(regular, { regular = it }, "قیمت اصلی", Modifier.weight(1f), number = true)
                    Field(sale, { sale = it }, "قیمت حراج", Modifier.weight(1f), number = true)
                }
            } else {
                Text("قیمت این محصول (متغیر/گروهی) از تنوع‌ها تعیین می‌شود.", color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
            if (type == "variable") {
                AttrsEditor(attrs)
                if (isNew) {
                    Text(
                        "بعد از «ساخت محصول»، دوباره روی محصول بزن تا تنوع‌ها (ترکیب رنگ و سایز) را بسازی.",
                        color = cs.onSurfaceVariant, fontSize = 12.sp
                    )
                } else {
                    VariationsSection(w, initial, attrs.map { it.toAttr() }.filter { it.name.isNotBlank() && it.options.isNotEmpty() })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مدیریت تعداد موجودی", Modifier.weight(1f))
                Switch(checked = manage, onCheckedChange = { manage = it })
            }
            if (manage) Field(qty, { qty = it }, "تعداد موجودی", number = true)
            else Choices(
                listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"),
                stock
            ) { stock = it }
            if (w.categories.isNotEmpty()) {
                Text("دسته‌بندی", fontWeight = FontWeight.Medium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.categories.forEach { c ->
                        FilterChip(
                            selected = cats.contains(c.id),
                            onClick = { if (cats.contains(c.id)) cats.remove(c.id) else cats.add(c.id) },
                            label = { Text(c.name) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محصول ویژه", Modifier.weight(1f))
                Switch(checked = featured, onCheckedChange = { featured = it })
            }
            Field(weight, { weight = it }, "وزن", number = true)
            Field(shortD, { shortD = it }, "توضیح کوتاه", lines = 3)
            Field(desc, { desc = it }, "توضیحات کامل (HTML)", lines = 5)
            PrimaryButton(if (isNew) "ساخت محصول" else "ذخیره تغییرات", enabled = name.isNotBlank() && w.busy == null) {
                val p = build()
                val r = p.regularPrice.toDoubleOrNull()
                val sv = p.salePrice.toDoubleOrNull()
                if (r != null && sv != null && sv >= r) {
                    w.message = "قیمت حراج باید کمتر از قیمت اصلی باشد"
                } else {
                    w.save(p, p.image != initial.image) { ok -> if (ok) onDismiss() }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف محصول؟") },
            text = { Text("«${initial.name}» به زباله منتقل می‌شود یا برای همیشه پاک می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    w.deleteProduct(initial, true) { ok -> if (ok) onDismiss() }
                }) { Text("حذف همیشگی", color = cs.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
                    TextButton(onClick = {
                        confirmDelete = false
                        w.deleteProduct(initial, false) { ok -> if (ok) onDismiss() }
                    }) { Text("انتقال به زباله") }
                }
            }
        )
    }
}

// ───────────────────────── ویرایش گروهی ─────────────────────────

@Composable
private fun BulkSheet(w: WooState, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val chosen = w.products.filter { w.selected.contains(it.id) }
    val steps = listOf(0, 10, 100, 1000, 10000)
    val actionNames = listOf("قیمت", "حراج", "موجودی", "انتشار", "دسته‌بندی", "ویژه", "حذف")

    var action by remember { mutableIntStateOf(0) }
    var priceSale by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.PCT_UP) }
    var valueText by remember { mutableStateOf("") }
    var round by remember { mutableIntStateOf(0) }
    var saleKind by remember { mutableIntStateOf(0) }
    var stockKind by remember { mutableIntStateOf(0) }
    var stockStatus by remember { mutableStateOf("instock") }
    var pubStatus by remember { mutableStateOf("publish") }
    val catIds = remember { mutableStateListOf<Long>() }
    var catReplace by remember { mutableStateOf(false) }
    var featuredOn by remember { mutableStateOf(true) }
    var permanent by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val v = en(valueText).toDoubleOrNull()
    val op: BulkOp? = when (action) {
        0 -> if (v != null) BPrice(priceSale, mode, v, steps[round]) else null
        1 -> if (saleKind == 1) BClearSale else if (v != null && v > 0 && v < 100) BSalePct(v, steps[round]) else null
        2 -> when (stockKind) {
            0 -> if (v != null) BQty(false, v.toInt()) else null
            1 -> if (v != null) BQty(true, v.toInt()) else null
            else -> BStockStatus(stockStatus)
        }
        3 -> BStatus(pubStatus)
        4 -> if (catIds.isNotEmpty()) BCats(catIds.toList(), catReplace) else null
        5 -> BFeatured(featuredOn)
        6 -> BDelete(permanent)
        else -> null
    }
    val plans = if (op != null && op !is BDelete) chosen.mapNotNull { planBulk(it, op) } else emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ویرایش گروهی (${fa(chosen.size)} محصول)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Choices(actionNames.mapIndexed { i, n -> i to n }, action) { action = it }

            when (action) {
                0 -> {
                    Choices(listOf(false to "قیمت اصلی", true to "قیمت حراج"), priceSale) { priceSale = it }
                    Choices(
                        listOf(
                            Mode.PCT_UP to "افزایش ٪", Mode.PCT_DOWN to "کاهش ٪",
                            Mode.AMT_UP to "افزایش مبلغ", Mode.AMT_DOWN to "کاهش مبلغ", Mode.SET to "تعیین مقدار"
                        ),
                        mode
                    ) { mode = it }
                    Field(valueText, { valueText = it }, if (mode == Mode.PCT_UP || mode == Mode.PCT_DOWN) "درصد" else "مبلغ", number = true)
                    Text("گرد کردن به", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    Choices(listOf(0 to "بدون", 1 to "۱۰", 2 to "۱۰۰", 3 to "۱٬۰۰۰", 4 to "۱۰٬۰۰۰"), round) { round = it }
                }
                1 -> {
                    Choices(listOf(0 to "اعمال حراج ٪", 1 to "پایان حراج"), saleKind) { saleKind = it }
                    if (saleKind == 0) {
                        Field(valueText, { valueText = it }, "درصد تخفیف از قیمت اصلی", number = true)
                        Choices(listOf(0 to "بدون", 1 to "۱۰", 2 to "۱۰۰", 3 to "۱٬۰۰۰", 4 to "۱۰٬۰۰۰"), round) { round = it }
                    }
                }
                2 -> {
                    Choices(listOf(0 to "تعیین تعداد", 1 to "افزودن به تعداد", 2 to "وضعیت موجودی"), stockKind) { stockKind = it }
                    if (stockKind < 2) Field(valueText, { valueText = it }, "تعداد", number = true)
                    else Choices(listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"), stockStatus) { stockStatus = it }
                }
                3 -> Choices(listOf("publish" to "منتشر", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"), pubStatus) { pubStatus = it }
                4 -> {
                    Choices(listOf(false to "افزودن به دسته‌های فعلی", true to "جایگزینی دسته‌ها"), catReplace) { catReplace = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.categories.forEach { c ->
                            FilterChip(
                                selected = catIds.contains(c.id),
                                onClick = { if (catIds.contains(c.id)) catIds.remove(c.id) else catIds.add(c.id) },
                                label = { Text(c.name) }
                            )
                        }
                    }
                }
                5 -> Choices(listOf(true to "علامت‌گذاری ویژه", false to "حذف از ویژه‌ها"), featuredOn) { featuredOn = it }
                else -> {
                    Choices(listOf(false to "انتقال به زباله", true to "حذف همیشگی"), permanent) { permanent = it }
                    Text("این عملیات روی ${fa(chosen.size)} محصول اعمال می‌شود.", color = cs.error, fontSize = 13.sp)
                }
            }

            if (op != null && op !is BDelete) {
                AppCard(pad = 12.dp) {
                    Text(
                        "پیش‌نمایش: ${fa(plans.size)} محصول تغییر می‌کند" +
                                (if (chosen.size > plans.size) "، ${fa(chosen.size - plans.size)} بدون تغییر/نامرتبط" else ""),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    plans.take(6).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(c.desc, fontSize = 12.sp, color = cs.primary)
                        }
                    }
                    if (plans.size > 6) Text("و ${fa(plans.size - 6)} مورد دیگر...", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Text("بعد از اعمال، می‌توانی با دکمهٔ «بازگردانی» همه را به حالت قبل برگردانی.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }

            PrimaryButton(
                if (op is BDelete) "حذف" else "اعمال روی ${fa(if (op == null) chosen.size else plans.size)} محصول",
                enabled = op != null && (op is BDelete || plans.isNotEmpty()) && w.busy == null
            ) {
                if (op is BDelete) confirm = true
                else if (op != null) {
                    w.runBulk(chosen, op)
                    onDismiss()
                }
            }
        }
    }

    if (confirm && op != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("حذف ${fa(chosen.size)} محصول؟") },
            text = { Text(if (permanent) "این کار برگشت‌ناپذیر است." else "محصولات به زباله منتقل می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    w.runBulk(chosen, op)
                    onDismiss()
                }) { Text("حذف", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } }
        )
    }
}

// ───────────────────────── سفارش‌ها ─────────────────────────

@Composable
private fun OrdersTab(w: WooState) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<WOrder?>(null) }
    LaunchedEffect(Unit) { if (w.orders.isEmpty()) w.loadOrders(true) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Choices(
                listOf(
                    "any" to "همه", "processing" to "در حال انجام", "pending" to "در انتظار پرداخت",
                    "on-hold" to "در انتظار بررسی", "completed" to "تکمیل شده", "cancelled" to "لغو شده",
                    "refunded" to "مسترد شده", "failed" to "ناموفق"
                ),
                w.orderStatusF
            ) { w.orderStatusF = it; w.loadOrders(true) }
        }
        if (w.ordersError != null) {
            item {
                AppCard {
                    Text(w.ordersError!!, color = cs.error)
                    TextButton(onClick = { w.loadOrders(true) }) { Text("تلاش دوباره") }
                }
            }
        }
        if (w.orders.isEmpty() && !w.ordersLoading && w.ordersError == null) item { EmptyState("سفارشی نیست") }
        items(w.orders, key = { it.id }) { o ->
            AppCard(onClick = { open = o }, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${fa(o.number)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Pill(orderFa(o.status), color = when (o.status) {
                        "completed" -> IncomeColor
                        "cancelled", "failed", "refunded" -> ExpenseColor
                        else -> AmberColor
                    })
                }
                Spacer(Modifier.height(4.dp))
                Text(o.customer)
                Text(o.date, fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("${priceText(o.total)} ${w.currency}", fontWeight = FontWeight.Bold, color = cs.primary)
            }
        }
        item {
            if (w.ordersLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else if (w.ordersHasMore) {
                LaunchedEffect(w.orders.size) { w.loadOrders(false) }
            }
        }
    }

    open?.let { o ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("سفارش #${fa(o.number)}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${o.customer}${if (o.phone.isNotBlank()) " · ${fa(o.phone)}" else ""}")
                    Text(o.date, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    o.items.forEach { Text("• ${fa(it)}", fontSize = 13.sp) }
                    Text("جمع: ${priceText(o.total)} ${w.currency}", fontWeight = FontWeight.Bold)
                    if (o.payment.isNotBlank()) Text("پرداخت: ${o.payment}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    if (o.note.isNotBlank()) Text("یادداشت مشتری: ${o.note}", fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("تغییر وضعیت", fontWeight = FontWeight.Medium)
                    Choices(
                        listOf(
                            "processing" to "در حال انجام", "completed" to "تکمیل", "on-hold" to "بررسی",
                            "pending" to "پرداخت", "cancelled" to "لغو", "refunded" to "مسترد"
                        ),
                        o.status
                    ) { st -> w.setOrderStatus(o, st) { no -> if (no != null) open = no } }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("بستن") } }
        )
    }
}

// ───────────────────────── تنظیمات اتصال ─────────────────────────

@Composable
private fun ShopSettingsTab(w: WooState) {
    val cs = MaterialTheme.colorScheme
    var cur by remember { mutableStateOf(w.currency) }
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppCard {
            Text("سایت متصل", color = cs.onSurfaceVariant, fontSize = 12.sp)
            Text(w.siteUrl.removePrefix("https://"), fontWeight = FontWeight.Bold)
        }
        Field(cur, { cur = it }, "واحد پول")
        FilledTonalButton(onClick = { w.saveCurrency(cur); w.message = "ذخیره شد" }) { Text("ذخیرهٔ واحد پول") }
        FilledTonalButton(onClick = { w.refreshAll() }) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("بروزرسانی همهٔ اطلاعات")
        }
        OutlinedButton(onClick = { confirm = true }) {
            Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp), tint = cs.error)
            Spacer(Modifier.width(6.dp))
            Text("قطع اتصال", color = cs.error)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("قطع اتصال؟") },
            text = { Text("کلیدها از روی این گوشی پاک می‌شود. اطلاعات سایت دست‌نخورده می‌ماند.") },
            confirmButton = { TextButton(onClick = { confirm = false; w.disconnect() }) { Text("قطع کن", color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } }
        )
    }
}
