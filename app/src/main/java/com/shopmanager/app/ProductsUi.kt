package com.shopmanager.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val StockOk = IncomeColor
private val StockNo = ExpenseColor

private fun num(s: String): Double? = en(s).trim().toDoubleOrNull()

private fun splitOpts(s: String): List<String> =
    s.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private fun parseAttrs(s: String): List<Pair<String, String>> = splitOpts(s).mapNotNull { t ->
    val i = t.indexOfFirst { it == '=' || it == ':' }
    if (i <= 0) null else {
        val n = t.substring(0, i).trim()
        val v = t.substring(i + 1).trim()
        if (n.isEmpty() || v.isEmpty()) null else n to v
    }
}

private fun comboCount(attrs: List<Pair<String, List<String>>>): Int =
    if (attrs.isEmpty()) 0 else attrs.fold(1) { a, p -> a * p.second.size }

// ───────────────────────── تب محصولات ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductsTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<WProduct?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf("simple") }
    var chooser by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showBulk by remember { mutableStateOf(false) }
    var showVars by remember { mutableStateOf<WProduct?>(null) }
    val selMode = woo.sel.isNotEmpty()

    var first by remember { mutableStateOf(true) }
    LaunchedEffect(woo.query) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        delay(600)
        woo.loadProducts(true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(
            "محصولات",
            if (woo.total > 0) "${fa(woo.total)} محصول" else null
        ) {
            IconBox(Icons.Rounded.Add, filled = true) { chooser = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(woo.query, { woo.query = it }, "جستجوی محصول...", Modifier.weight(1f)) { woo.loadProducts(true) }
            Box {
                IconBox(Icons.Rounded.Tune) { showFilters = true }
                if (woo.activeFilterCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) { Text(fa(woo.activeFilterCount), color = cs.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            IconBox(if (woo.viewGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView) { woo.viewGrid = !woo.viewGrid }
        }
        val preset = when {
            woo.activeFilterCount == 0 -> "all"
            woo.activeFilterCount == 1 && woo.statusF == "publish" -> "pub"
            woo.activeFilterCount == 1 && woo.statusF == "draft" -> "draft"
            woo.activeFilterCount == 1 && woo.stockF == "outofstock" -> "oos"
            woo.activeFilterCount == 1 && woo.onSaleF -> "sale"
            else -> "custom"
        }
        Choices(
            listOf("all" to "همه", "pub" to "منتشر شده", "oos" to "ناموجود", "sale" to "حراج", "draft" to "پیش‌نویس")
                .let { if (preset == "custom") it + ("custom" to "فیلتر سفارشی") else it },
            preset
        ) { pick ->
            if (pick != "custom") {
                val keep = woo.query
                woo.resetFilters()
                woo.query = keep
                when (pick) {
                    "pub" -> woo.statusF = "publish"
                    "draft" -> woo.statusF = "draft"
                    "oos" -> woo.stockF = "outofstock"
                    "sale" -> woo.onSaleF = true
                }
                woo.loadProducts(true)
            }
        }

        if (selMode) {
            AppCard(pad = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${fa(woo.sel.size)} انتخاب", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "همهٔ نتایج", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clickable { woo.selectAllMatching() }.padding(6.dp)
                    )
                    ActionButton("ویرایش گروهی", { showBulk = true }, icon = Icons.Rounded.AutoFixHigh)
                    IconBox(Icons.Rounded.Close) { woo.sel.clear() }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            val err = woo.error
            when {
                woo.products.isEmpty() && woo.loading -> SkeletonList()
                woo.products.isEmpty() && err != null -> Column(
                    Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(err, color = cs.error)
                    ActionButton("تلاش دوباره", { woo.loadProducts(true) }, icon = Icons.Rounded.Refresh)
                }
                woo.products.isEmpty() -> EmptyState("محصولی پیدا نشد")
                else -> {
                    val cols = if (woo.viewGrid) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(bottom = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(woo.products, key = { it.id }) { p ->
                            val selected = woo.sel.containsKey(p.id)
                            val open: () -> Unit = {
                                if (selMode) {
                                    woo.toggleSelect(p)
                                } else {
                                    editing = p
                                }
                            }
                            if (woo.viewGrid) ProductCard(p, woo.currency, selected, open) { woo.toggleSelect(p) }
                            else ProductRow(p, woo.currency, selected, open) { woo.toggleSelect(p) }
                        }
                        if (woo.page < woo.pages) {
                            item(span = { GridItemSpan(cols) }) {
                                LaunchedEffect(woo.page) { woo.loadProducts(false) }
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) ProductEditor(woo, null, startType = createType, onVariations = { showVars = it }) { creating = false }
    editing?.let { p ->
        ProductEditor(woo, p, onVariations = { showVars = it }) { editing = null }
    }
    showVars?.let { p -> VariationsSheet(woo, p) { showVars = null } }
    if (showFilters) FilterSheet(woo) { showFilters = false }
    if (chooser) {
        AppSheet({ chooser = false }, "چه محصولی می‌سازی؟") {
            ActionButton("محصول ساده", { createType = "simple"; creating = true; chooser = false }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            Text("یک قیمت و یک موجودی دارد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton("محصول متغیر (رنگ، سایز...)", { createType = "variable"; creating = true; chooser = false }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Layers, kind = BtnKind.TONAL)
            Text("برای هر رنگ یا سایز قیمت، موجودی و عکس جدا دارد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showBulk) BulkSheet(woo) { showBulk = false }
}

@Composable
fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val base = Modifier.size(46.dp)
    Box(
        (if (filled) base.shadow(12.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary)
            .clip(CircleShape).background(accentBrush())
        else base.raised(CircleShape)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (filled) cs.onPrimary else cs.onSurface, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun StockPill(p: WProduct) {
    val c = when (p.stockStatus) {
        "instock" -> StockOk
        "outofstock" -> StockNo
        else -> MaterialTheme.colorScheme.tertiary
    }
    val q = if (p.manageStock && p.stockQty != null) " (${fa(p.stockQty)})" else ""
    Pill(stockFa(p.stockStatus) + q, color = c)
}

@Composable
private fun PriceLine(p: WProduct, currency: String) {
    val cs = MaterialTheme.colorScheme
    if (p.type == "variable" || p.type == "grouped") {
        Text(
            "${typeFa(p.type)}" + (if (p.varCount > 0) " · ${fa(p.varCount)} تنوع" else "") +
                    (if (p.price.isNotBlank()) " · از ${priceText(p.price)}" else ""),
            fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1
        )
    } else if (p.onSale && p.salePrice.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(priceText(p.regularPrice), fontSize = 12.sp, color = cs.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            Spacer(Modifier.width(6.dp))
            Text("${priceText(p.salePrice)} $currency", fontWeight = FontWeight.Bold, color = cs.primary)
        }
    } else {
        Text("${priceText(p.regularPrice.ifBlank { p.price })} $currency", fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductRow(p: WProduct, currency: String, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().raised(shape, selected)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp))) {
            RemoteImage(p.image, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name.ifBlank { "(بدون نام)" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            PriceLine(p, currency)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StockPill(p)
                if (p.status == "future") Pill("انتشار ${orderDate(gmtLocal(p.dateGmt)?.toString() ?: p.date)}", color = cs.tertiary)
                else if (p.status != "publish") Pill(statusFa(p.status), color = cs.outline)
                if (p.featured) Pill("ویژه", color = cs.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCard(p: WProduct, currency: String, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().raised(shape, selected)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(18.dp))) {
            RemoteImage(p.image, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                }
            }
        }
        Text(p.name.ifBlank { "(بدون نام)" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp, minLines = 2)
        PriceLine(p, currency)
        StockPill(p)
    }
}

// ───────────────────────── فیلتر پیشرفته ─────────────────────────

@Composable
private fun FilterSheet(woo: WooState, onClose: () -> Unit) {
    AppSheet(onClose, "فیلتر و مرتب‌سازی") {
        Text("وضعیت انتشار", fontWeight = FontWeight.SemiBold)
        Choices(
            listOf("any" to "همه", "publish" to "منتشر شده", "future" to "زمان‌بندی‌شده", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"),
            woo.statusF
        ) { woo.statusF = it }
        Text("مرتب‌سازی", fontWeight = FontWeight.SemiBold)
        Choices(
            listOf(0 to "جدیدترین", 1 to "نام", 2 to "ارزان‌ترین", 3 to "گران‌ترین", 4 to "پرفروش"),
            woo.sort
        ) { woo.sort = it }
        Text("موجودی", fontWeight = FontWeight.SemiBold)
        Choices(
            listOf("" to "همه", "instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"),
            woo.stockF
        ) { woo.stockF = it }
        Text("نوع محصول", fontWeight = FontWeight.SemiBold)
        Choices(
            listOf("" to "همه", "simple" to "ساده", "variable" to "متغیر", "grouped" to "گروهی", "external" to "خارجی"),
            woo.typeF
        ) { woo.typeF = it }
        Text("ویژگی‌ها", fontWeight = FontWeight.SemiBold)
        Choices(
            listOf("a" to "همه", "sale" to "فقط حراج", "feat" to "فقط ویژه"),
            if (woo.onSaleF) "sale" else if (woo.featuredF) "feat" else "a"
        ) {
            woo.onSaleF = it == "sale"
            woo.featuredF = it == "feat"
        }
        Text("بازهٔ قیمت (${woo.currency})", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(woo.minPrice, { woo.minPrice = it }, "از", Modifier.weight(1f), number = true)
            Field(woo.maxPrice, { woo.maxPrice = it }, "تا", Modifier.weight(1f), number = true)
        }
        Text("دسته", fontWeight = FontWeight.SemiBold)
        CategoryPicker(woo.categories, if (woo.catF == 0L) emptyList() else listOf(woo.catF), { woo.catF = it.firstOrNull() ?: 0L }, single = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("پاک کردن", { woo.resetFilters(); woo.sort = 0; woo.loadProducts(true); onClose() }, Modifier.weight(1f), kind = BtnKind.GHOST)
            ActionButton("اعمال فیلتر", { woo.loadProducts(true); onClose() }, Modifier.weight(1f), icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun MiniChip(text: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape).background(if (on) cs.primary else insetColor())
            .border(1.dp, if (on) Color.Transparent else cs.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text, fontSize = 13.sp, maxLines = 1,
            color = if (on) cs.onPrimary else cs.onSurface,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun GalleryRow(items: List<String>, onAdd: () -> Unit, onRemove: (Int) -> Unit, onMakeMain: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, url ->
            Box(Modifier.size(82.dp).clip(shape)) {
                RemoteImage(url, Modifier.fillMaxSize(), 240)
                Box(
                    Modifier.align(Alignment.TopStart).padding(4.dp).size(26.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = .6f)).clickable { onRemove(i) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Box(
                    Modifier.align(Alignment.BottomStart).padding(4.dp).size(26.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = .6f)).clickable { onMakeMain(i) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp)) }
            }
        }
        Box(
            Modifier.size(82.dp).clip(shape).background(insetColor()).border(1.dp, cs.outlineVariant, shape).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = cs.primary, modifier = Modifier.size(28.dp)) }
    }
    if (items.isNotEmpty()) {
        Text("ستاره = تبدیل به تصویر شاخص، ضربدر = حذف از گالری", fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

// ───────────────────────── ویرایشگر محصول ─────────────────────────

@Composable
fun ProductEditor(woo: WooState, initial: WProduct?, startType: String = "simple", onVariations: (WProduct) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isNew = initial == null
    val orig = initial ?: WProduct()
    LaunchedEffect(Unit) { if (woo.brands.isEmpty()) woo.loadBrands() }
    var name by remember { mutableStateOf(orig.name) }
    var sku by remember { mutableStateOf(orig.sku) }
    var regular by remember { mutableStateOf(orig.regularPrice) }
    var sale by remember { mutableStateOf(orig.salePrice) }
    var status by remember { mutableStateOf(orig.status) }
    var stockStatus by remember { mutableStateOf(orig.stockStatus) }
    var manage by remember { mutableStateOf(orig.manageStock) }
    var qty by remember { mutableStateOf(orig.stockQty?.toString() ?: "") }
    var backorders by remember { mutableStateOf(orig.backorders) }
    var featured by remember { mutableStateOf(orig.featured) }
    var cats by remember { mutableStateOf(orig.catIds) }
    var image by remember { mutableStateOf(orig.image) }
    var gallery by remember { mutableStateOf(orig.gallery) }
    var desc by remember { mutableStateOf(orig.description) }
    var shortDesc by remember { mutableStateOf(orig.shortDescription) }
    var weight by remember { mutableStateOf(orig.weight) }
    var lenF by remember { mutableStateOf(orig.length) }
    var widF by remember { mutableStateOf(orig.width) }
    var heiF by remember { mutableStateOf(orig.height) }
    var saleFromDay by remember { mutableStateOf(orig.saleFrom.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() }) }
    var saleToDay by remember { mutableStateOf(orig.saleTo.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() }) }
    var showSaleFrom by remember { mutableStateOf(false) }
    var showSaleTo by remember { mutableStateOf(false) }
    var taxStatus by remember { mutableStateOf(orig.taxStatus) }
    var taxClass by remember { mutableStateOf(orig.taxClass) }
    var lowStock by remember { mutableStateOf(orig.lowStock?.toString() ?: "") }
    var soldInd by remember { mutableStateOf(orig.soldIndividually) }
    var shipClass by remember { mutableStateOf(orig.shippingClass) }
    var tagNames by remember { mutableStateOf(orig.tagNames) }
    var newTags by remember { mutableStateOf("") }
    var purchaseNote by remember { mutableStateOf(orig.purchaseNote) }
    var menuOrder by remember { mutableStateOf(if (orig.menuOrder != 0) orig.menuOrder.toString() else "") }
    var reviews by remember { mutableStateOf(orig.reviewsAllowed) }
    var visibility by remember { mutableStateOf(orig.catalogVisibility) }
    var virt by remember { mutableStateOf(orig.virtual) }
    var downl by remember { mutableStateOf(orig.downloadable) }
    var extUrl by remember { mutableStateOf(orig.externalUrl) }
    var btnText by remember { mutableStateOf(orig.buttonText) }
    var upsells by remember { mutableStateOf(orig.upsellIds.joinToString("، ")) }
    var crosses by remember { mutableStateOf(orig.crossSellIds.joinToString("، ")) }
    var grouped by remember { mutableStateOf(orig.groupedIds.joinToString("، ")) }
    var slug by remember { mutableStateOf(orig.slug) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var pType by remember { mutableStateOf(startType) }
    val attrNames = remember { mutableStateListOf("") }
    val attrVals = remember { mutableStateListOf("") }
    val attrIds = remember { mutableStateListOf(0L) }
    var varQty by remember { mutableStateOf("") }
    val origLocal = remember { if (orig.status == "future") gmtLocal(orig.dateGmt) else null }
    var schedDay by remember { mutableStateOf<Long?>(origLocal?.toLocalDate()?.toEpochDay()) }
    var schedH by remember { mutableStateOf(origLocal?.let { String.format(java.util.Locale.US, "%02d", it.hour) } ?: "12") }
    var schedM by remember { mutableStateOf(origLocal?.let { String.format(java.util.Locale.US, "%02d", it.minute) } ?: "00") }
    var showDate by remember { mutableStateOf(false) }
    var brandIds by remember { mutableStateOf(orig.brandIds) }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) woo.uploadImage(ctx, uri) { url -> if (url != null) image = url }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) woo.uploadImages(ctx, uris) { urls -> gallery = gallery + urls }
    }
    fun addGlobal(d: WAttrDef) {
        val last = attrNames.size - 1
        if (last >= 0 && attrNames[last].isBlank() && attrVals[last].isBlank() && attrIds[last] == 0L) {
            attrNames[last] = d.name
            attrIds[last] = d.id
        } else {
            attrNames.add(d.name)
            attrVals.add("")
            attrIds.add(d.id)
        }
        woo.loadAttrTerms(d.id)
    }
    fun attrIdMap(): Map<String, Long> =
        attrNames.indices.filter { attrIds[it] > 0L }.associate { attrNames[it].trim() to attrIds[it] }
    fun schedIso(): String {
        val d = schedDay ?: return ""
        val h = (en(schedH).toIntOrNull() ?: 0).coerceIn(0, 23)
        val m = (en(schedM).toIntOrNull() ?: 0).coerceIn(0, 59)
        return "${java.time.LocalDate.ofEpochDay(d)}T" + String.format(java.util.Locale.US, "%02d:%02d:00", h, m)
    }
    fun attrs(): List<Pair<String, List<String>>> =
        attrNames.indices.map { attrNames[it].trim() to splitOpts(attrVals[it]) }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

    fun build(): WProduct = orig.copy(
        type = if (isNew) pType else orig.type,
        schedDate = if (status == "future") deviceToSite(schedIso(), woo.siteOffsetSec).first
        else if (orig.status == "future") deviceToSite(java.time.LocalDateTime.now().withNano(0).toString(), woo.siteOffsetSec).first
        else "",
        schedGmt = if (status == "future") deviceToSite(schedIso(), woo.siteOffsetSec).second
        else if (orig.status == "future") deviceToSite(java.time.LocalDateTime.now().withNano(0).toString(), woo.siteOffsetSec).second
        else "",
        brandIds = brandIds,
        brandNames = woo.brands.filter { brandIds.contains(it.id) }.map { it.name },
        name = name.trim(), sku = sku.trim(),
        regularPrice = en(regular).trim(), salePrice = en(sale).trim(),
        status = status, stockStatus = stockStatus, manageStock = manage,
        stockQty = if (manage) (en(qty).trim().toIntOrNull() ?: 0) else null,
        backorders = backorders, featured = featured, catIds = cats,
        catNames = woo.categories.filter { cats.contains(it.id) }.map { it.name },
        image = image.trim(), gallery = gallery.filter { it.isNotBlank() }, description = desc, shortDescription = shortDesc, weight = en(weight).trim(),
        length = en(lenF).trim(), width = en(widF).trim(), height = en(heiF).trim(),
        saleFrom = saleFromDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "",
        saleTo = saleToDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "",
        taxStatus = taxStatus, taxClass = taxClass.trim(),
        lowStock = if (manage) en(lowStock).trim().toIntOrNull() else null,
        soldIndividually = soldInd, shippingClass = shipClass.trim(),
        tagNames = (tagNames + newTags.replace("،", ",").split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct(),
        purchaseNote = purchaseNote, menuOrder = en(menuOrder).trim().toIntOrNull() ?: 0,
        reviewsAllowed = reviews, catalogVisibility = visibility, virtual = virt, downloadable = downl,
        externalUrl = extUrl.trim(), buttonText = btnText.trim(),
        upsellIds = parseIds(en(upsells)), crossSellIds = parseIds(en(crosses)), groupedIds = parseIds(en(grouped)),
        slug = slug.trim()
    )

    fun diff(n: WProduct): List<String> {
        val d = ArrayList<String>()
        fun ch(label: String, a: String, b: String) { if (a != b) d.add("$label: ${a.ifBlank { "—" }} ← ${b.ifBlank { "—" }}") }
        ch("نام", orig.name, n.name)
        ch("SKU", orig.sku, n.sku)
        if (n.priceEditable) {
            ch("قیمت", orig.regularPrice, n.regularPrice)
            ch("قیمت حراج", orig.salePrice, n.salePrice)
        }
        ch("وضعیت", statusFa(orig.status), statusFa(n.status))
        ch("موجودی", stockFa(orig.stockStatus), stockFa(n.stockStatus))
        ch("مدیریت موجودی", if (orig.manageStock) "بله" else "خیر", if (n.manageStock) "بله" else "خیر")
        ch("تعداد", orig.stockQty?.toString() ?: "", n.stockQty?.toString() ?: "")
        ch("پیش‌سفارش", orig.backorders, n.backorders)
        ch("ویژه", if (orig.featured) "بله" else "خیر", if (n.featured) "بله" else "خیر")
        if (orig.catIds != n.catIds) d.add("دسته‌ها: ${n.catNames.joinToString("، ").ifBlank { "—" }}")
        if (orig.image != n.image) d.add("تصویر شاخص تغییر کرد")
        if (orig.gallery != n.gallery) d.add("گالری: ${fa(n.gallery.size)} تصویر")
        if (orig.description != n.description) d.add("توضیحات تغییر کرد")
        if (orig.shortDescription != n.shortDescription) d.add("توضیح کوتاه تغییر کرد")
        ch("وزن", orig.weight, n.weight)
        ch("ابعاد", "${orig.length}×${orig.width}×${orig.height}", "${n.length}×${n.width}×${n.height}")
        ch("شروع حراج", orig.saleFrom, n.saleFrom)
        ch("پایان حراج", orig.saleTo, n.saleTo)
        ch("مالیات", orig.taxStatus, n.taxStatus)
        ch("کلاس مالیات", orig.taxClass, n.taxClass)
        ch("حد کمبود موجودی", orig.lowStock?.toString() ?: "", n.lowStock?.toString() ?: "")
        ch("فروش تکی", if (orig.soldIndividually) "بله" else "خیر", if (n.soldIndividually) "بله" else "خیر")
        ch("کلاس ارسال", orig.shippingClass, n.shippingClass)
        ch("برچسب‌ها", orig.tagNames.joinToString("، "), n.tagNames.joinToString("، "))
        ch("یادداشت خرید", orig.purchaseNote, n.purchaseNote)
        ch("ترتیب منو", orig.menuOrder.toString(), n.menuOrder.toString())
        ch("دیدگاه‌ها", if (orig.reviewsAllowed) "فعال" else "غیرفعال", if (n.reviewsAllowed) "فعال" else "غیرفعال")
        ch("نمایش در فروشگاه", orig.catalogVisibility, n.catalogVisibility)
        ch("مجازی", if (orig.virtual) "بله" else "خیر", if (n.virtual) "بله" else "خیر")
        ch("دانلودی", if (orig.downloadable) "بله" else "خیر", if (n.downloadable) "بله" else "خیر")
        ch("لینک خارجی", orig.externalUrl, n.externalUrl)
        ch("متن دکمه", orig.buttonText, n.buttonText)
        ch("آپ‌سل", orig.upsellIds.joinToString("،"), n.upsellIds.joinToString("،"))
        ch("کراس‌سل", orig.crossSellIds.joinToString("،"), n.crossSellIds.joinToString("،"))
        ch("محصولات گروهی", orig.groupedIds.joinToString("،"), n.groupedIds.joinToString("،"))
        ch("نامک", orig.slug, n.slug)
        if (n.status == "future") d.add("زمان انتشار: ${fmtDay(schedDay ?: 0L)} ساعت ${fa(schedIso().takeLast(8).take(5))}")
        if (orig.brandIds != n.brandIds) d.add("برند: ${n.brandNames.joinToString("، ").ifBlank { "—" }}")
        return d
    }

    val baseStatuses = listOf("publish" to "منتشر", "future" to "زمان‌بندی", "draft" to "پیش‌نویس")
    val statusOptions = if (baseStatuses.any { it.first == status }) baseStatuses else baseStatuses + (status to statusFa(status))
    val catSummary = woo.categories.filter { cats.contains(it.id) }.joinToString("، ") { it.name }.ifBlank { "بدون دسته" }

    AppSheet(onClose, if (isNew) (if (pType == "variable") "محصول متغیر جدید" else "محصول جدید") else "ویرایش محصول") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).clickable { picker.launch("image/*") }) {
                RemoteImage(image, Modifier.fillMaxSize(), 360)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تصویر شاخص", fontWeight = FontWeight.SemiBold)
                ActionButton("عکس از گالری", { picker.launch("image/*") }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PhotoLibrary, kind = BtnKind.TONAL)
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(typeFa(orig.type))
                        if (orig.totalSales > 0) Pill("${fa(orig.totalSales)} فروش", color = cs.outline)
                    }
                }
            }
        }
        Text("گالری تصاویر" + (if (gallery.isNotEmpty()) " (${fa(gallery.size)})" else ""), fontWeight = FontWeight.SemiBold)
        GalleryRow(
            gallery,
            onAdd = { galleryPicker.launch("image/*") },
            onRemove = { i -> gallery = gallery.filterIndexed { k, _ -> k != i } },
            onMakeMain = { i ->
                val old = image
                val pick = gallery[i]
                image = pick
                gallery = if (old.isBlank()) gallery.filterIndexed { k, _ -> k != i }
                else gallery.mapIndexed { k, u -> if (k == i) old else u }
            }
        )
        Field(name, { name = it }, "نام محصول")
        if (isNew) Choices(listOf("simple" to "ساده", "variable" to "متغیر", "external" to "خارجی", "grouped" to "گروهی"), pType) { pType = it }

        if (orig.priceEditable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(regular, { regular = it }, if (isNew && pType == "variable") "قیمت پایه (بعداً برای هر تنوع جدا)" else "قیمت (${woo.currency})", Modifier.weight(1f), number = true)
                Field(sale, { sale = it }, "قیمت حراج", Modifier.weight(1f), number = true)
            }
            Section("حراج زمان‌دار", if (saleFromDay != null || saleToDay != null) "فعال" else "بدون زمان") {
                Text("اگر تاریخ بزنی، حراج خودکار شروع و تمام می‌شود.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(saleFromDay?.let { "از " + fmtDay(it) } ?: "شروع حراج", { showSaleFrom = true }, Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, kind = BtnKind.TONAL)
                    ActionButton(saleToDay?.let { "تا " + fmtDay(it) } ?: "پایان حراج", { showSaleTo = true }, Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, kind = BtnKind.TONAL)
                }
                if (saleFromDay != null || saleToDay != null) {
                    TextButton(onClick = { saleFromDay = null; saleToDay = null }) { Text("حذف زمان‌بندی حراج") }
                }
            }
        } else if (orig.type == "variable") {
            AppCard(onClick = { onVariations(orig) }, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Layers, null, tint = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("قیمت و حراج هر تنوع (${fa(orig.varCount)})", fontWeight = FontWeight.Bold)
                        Text("قیمت عادی، حراج، موجودی و عکس هر رنگ/سایز", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        } else {
            Text("قیمت این نوع محصول از خود محصول قابل ویرایش نیست.", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }

        if (isNew && pType == "variable") {
            AppCard(pad = 12.dp) {
                Text("ویژگی‌های متغیر", fontWeight = FontWeight.Bold)
                Text(
                    "نام ویژگی (مثلاً رنگ) و مقادیرش (قرمز، آبی، سبز). برای هر ترکیب یک تنوع ساخته می‌شود.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val freeDefs = woo.attrDefs.filter { d -> !attrIds.contains(d.id) }
                if (freeDefs.isNotEmpty()) {
                    Text("از ویژگی‌های سایت انتخاب کن", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        freeDefs.forEach { d -> MiniChip(d.name, false) { addGlobal(d) } }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                attrNames.indices.forEach { i ->
                    if (attrIds[i] > 0L) {
                        val terms = woo.attrTerms[attrIds[i]]
                        val chosen = splitOpts(attrVals[i])
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(attrNames[i], Icons.Rounded.Layers)
                            Spacer(Modifier.weight(1f))
                            if (terms != null && terms.isNotEmpty()) {
                                TextButton(onClick = { attrVals[i] = if (chosen.size == terms.size) "" else terms.joinToString("، ") }) {
                                    Text(if (chosen.size == terms.size) "هیچ‌کدام" else "انتخاب همه")
                                }
                            }
                            IconBox(Icons.Rounded.Close) { attrNames.removeAt(i); attrVals.removeAt(i); attrIds.removeAt(i) }
                        }
                        if (terms == null) {
                            Text("در حال خواندن مقدارها از سایت...", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        } else if (terms.isEmpty()) {
                            Text("برای این ویژگی در سایت مقداری تعریف نشده.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                terms.forEach { t ->
                                    MiniChip(t, chosen.contains(t)) {
                                        val nv = if (chosen.contains(t)) chosen - t else chosen + t
                                        attrVals[i] = nv.joinToString("، ")
                                    }
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Field(attrNames[i], { attrNames[i] = it }, "ویژگی دلخواه", Modifier.weight(1f))
                            Field(attrVals[i], { attrVals[i] = it }, "مقادیر (با ، جدا کن)", Modifier.weight(1.6f))
                            if (attrNames.size > 1) IconBox(Icons.Rounded.Close) { attrNames.removeAt(i); attrVals.removeAt(i); attrIds.removeAt(i) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton("ویژگی دلخواه", { attrNames.add(""); attrVals.add(""); attrIds.add(0L) }, kind = BtnKind.TONAL, icon = Icons.Rounded.Add)
                    Field(varQty, { varQty = it }, "موجودی هر تنوع", Modifier.weight(1f), number = true)
                }
                Spacer(Modifier.height(6.dp))
                val cc = comboCount(attrs())
                Text(
                    if (cc == 0) "حداقل یک ویژگی با مقدار وارد کن" else "${fa(cc)} تنوع ساخته می‌شود" + (if (cc > 100) " (حداکثر ۱۰۰)" else ""),
                    fontWeight = FontWeight.SemiBold, color = if (cc == 0 || cc > 100) cs.error else cs.primary
                )
            }
        }

        Section(
            "انتشار",
            statusFa(status) + (if (status == "future" && schedDay != null) " · ${fmtDay(schedDay!!)} ${fa(schedH)}:${fa(schedM)}" else "")
        ) {
            Choices(statusOptions, status) { status = it }
            if (status == "future") {
                Text("سایت وردپرس خودش سر همین ساعت منتشر می‌کند (به وقت گوشی). گوشی لازم نیست روشن باشد.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(
                        schedDay?.let { fmtDay(it) } ?: "انتخاب تاریخ", { showDate = true },
                        Modifier.weight(1.4f), icon = Icons.Rounded.CalendarMonth, kind = BtnKind.TONAL
                    )
                    Field(schedH, { schedH = it }, "ساعت", Modifier.weight(1f), number = true)
                    Field(schedM, { schedM = it }, "دقیقه", Modifier.weight(1f), number = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محصول ویژه", Modifier.weight(1f))
                Switch(featured, { featured = it })
            }
        }

        Section("موجودی", stockFa(stockStatus) + (if (manage && qty.isNotBlank()) " · ${fa(qty)} عدد" else "")) {
            Choices(listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"), stockStatus) { stockStatus = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مدیریت تعداد موجودی", Modifier.weight(1f))
                Switch(manage, { manage = it })
            }
            if (manage) {
                Field(qty, { qty = it }, "تعداد", number = true)
                Text("پیش‌سفارش در صورت اتمام", fontWeight = FontWeight.SemiBold)
                Choices(listOf("no" to "غیرمجاز", "notify" to "با اطلاع", "yes" to "مجاز"), backorders) { backorders = it }
                Field(lowStock, { lowStock = it }, "هشدار کمبود موجودی (تعداد)", number = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("فروش تکی (هر سفارش فقط ۱ عدد)", Modifier.weight(1f))
                Switch(soldInd, { soldInd = it })
            }
        }

        Section("ارسال و مالیات", listOf(weight.takeIf { it.isNotBlank() }?.let { "وزن $it" }, if (virt) "مجازی" else null).filterNotNull().joinToString(" · ")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(lenF, { lenF = it }, "طول", Modifier.weight(1f), number = true)
                Field(widF, { widF = it }, "عرض", Modifier.weight(1f), number = true)
                Field(heiF, { heiF = it }, "ارتفاع", Modifier.weight(1f), number = true)
            }
            Field(weight, { weight = it }, "وزن", number = true)
            Field(shipClass, { shipClass = it }, "کلاس ارسال (نامک، مثلاً heavy)")
            Text("وضعیت مالیات", fontWeight = FontWeight.SemiBold)
            Choices(listOf("taxable" to "مشمول", "shipping" to "فقط ارسال", "none" to "بدون مالیات"), taxStatus) { taxStatus = it }
            Field(taxClass, { taxClass = it }, "کلاس مالیات (خالی = استاندارد)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محصول مجازی (بدون ارسال)", Modifier.weight(1f))
                Switch(virt, { virt = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("محصول دانلودی", Modifier.weight(1f))
                Switch(downl, { downl = it })
            }
        }

        Section("برچسب‌ها", tagNames.joinToString("، ").ifBlank { "بدون برچسب" }) {
            if (woo.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    woo.tags.forEach { t ->
                        MiniChip(t.name, tagNames.any { it.equals(t.name, true) }) {
                            tagNames = if (tagNames.any { it.equals(t.name, true) }) tagNames.filter { !it.equals(t.name, true) } else tagNames + t.name
                        }
                    }
                }
            }
            tagNames.filter { n -> woo.tags.none { it.name.equals(n, true) } }.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f))
                    IconBox(Icons.Rounded.Close) { tagNames = tagNames.filter { it != n } }
                }
            }
            Field(newTags, { newTags = it }, "برچسب جدید (با ، جدا کن)")
        }

        Section("دسته و برند", catSummary + (if (brandIds.isNotEmpty()) " · " + woo.brands.filter { brandIds.contains(it.id) }.joinToString("، ") { it.name } else "")) {
            Text("دسته‌ها", fontWeight = FontWeight.SemiBold)
            CategoryPicker(woo.categories, cats, { cats = it })
            Text("برند", fontWeight = FontWeight.SemiBold)
            if (woo.brandsSupported && woo.brands.isNotEmpty()) {
                CategoryPicker(woo.brands, brandIds, { brandIds = it })
            } else if (!woo.brandsSupported) {
                Text("این سایت برند ووکامرس را پشتیبانی نمی‌کند (به ووکامرس ۹٫۶ یا بالاتر نیاز دارد)" + (if (woo.brandsError.isNotBlank()) " — ${woo.brandsError}" else ""), fontSize = 12.sp, color = cs.onSurfaceVariant)
                TextButton(onClick = { woo.loadBrands() }) { Text("تلاش دوباره") }
            } else {
                Text("هنوز برندی ساخته نشده. از «بیشتر ← دسته‌ها و برندها ← برندها» اضافه کن.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                TextButton(onClick = { woo.loadBrands() }) { Text("بارگذاری دوباره") }
            }
        }

        Section("اطلاعات بیشتر", "SKU، توضیحات، لینک") {
            Field(sku, { sku = it }, "SKU (شناسهٔ کالا)")
            Field(image, { image = it }, "آدرس تصویر (URL)")
            Field(shortDesc, { shortDesc = it }, "توضیح کوتاه", singleLine = false, minLines = 2)
            Field(desc, { desc = it }, "توضیحات", singleLine = false, minLines = 4)
            if (!isNew && orig.permalink.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("باز کردن در سایت", {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(orig.permalink)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.weight(1f), icon = Icons.Rounded.OpenInNew, kind = BtnKind.TONAL)
                    ActionButton("کپی لینک", {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", orig.permalink))
                        Toast.makeText(ctx, "لینک کپی شد", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, kind = BtnKind.TONAL)
                }
            }
        }
        Section("نمایش و پیشرفته", "نامک، دیدگاه، محصولات مرتبط") {
            Field(slug, { slug = it }, "نامک (آدرس کوتاه)")
            Text("نمایش در فروشگاه", fontWeight = FontWeight.SemiBold)
            Choices(listOf("visible" to "همه‌جا", "catalog" to "فقط فروشگاه", "search" to "فقط جستجو", "hidden" to "مخفی"), visibility) { visibility = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("امکان ثبت دیدگاه", Modifier.weight(1f))
                Switch(reviews, { reviews = it })
            }
            Field(menuOrder, { menuOrder = it }, "ترتیب نمایش (عدد)", number = true)
            Field(purchaseNote, { purchaseNote = it }, "یادداشت بعد از خرید", singleLine = false, minLines = 2)
            if (orig.type == "external" || (isNew && pType == "external")) {
                Field(extUrl, { extUrl = it }, "لینک محصول خارجی")
                Field(btnText, { btnText = it }, "متن دکمه")
            }
            if (orig.type == "grouped") Field(grouped, { grouped = it }, "شناسهٔ محصولات گروه (با ، جدا کن)")
            Field(upsells, { upsells = it }, "آپ‌سل: شناسهٔ محصولات (با ، جدا کن)")
            Field(crosses, { crosses = it }, "کراس‌سل: شناسهٔ محصولات (با ، جدا کن)")
            val relNames = (parseIds(en(upsells)) + parseIds(en(crosses))).distinct().mapNotNull { id -> woo.products.firstOrNull { it.id == id }?.name }
            if (relNames.isNotEmpty()) Text(relNames.joinToString("، "), fontSize = 12.sp, color = cs.onSurfaceVariant)
            if (!isNew) {
                Text("شناسهٔ این محصول: ${fa(orig.id)}" + (if (orig.ratingCount > 0) " · امتیاز ${fa(orig.avgRating)} از ${fa(orig.ratingCount)} نظر" else ""), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
        err?.let { Text(it, color = cs.error, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isNew) {
                ActionButton("حذف", { confirmDelete = true }, kind = BtnKind.DANGER, icon = Icons.Rounded.DeleteForever)
            }
            ActionButton("ذخیره تغییرات", {
                if (name.isBlank()) {
                    err = "نام محصول را وارد کن"
                } else if (orig.priceEditable && regular.isNotBlank() && num(regular) == null) {
                    err = "قیمت معتبر نیست"
                } else if (status == "future" && (schedDay == null || java.time.LocalDateTime.parse(schedIso()).isBefore(java.time.LocalDateTime.now()))) {
                    err = "برای زمان‌بندی، تاریخ و ساعتی در آینده انتخاب کن"
                } else if (isNew && pType == "variable" && (attrs().isEmpty() || comboCount(attrs()) > 100)) {
                    err = "ویژگی‌ها را کامل وارد کن (حداکثر ۱۰۰ تنوع)"
                } else if (isNew && pType == "variable" && num(regular) == null) {
                    err = "قیمت تنوع‌ها را وارد کن"
                } else if (!isNew && diff(build()).isEmpty()) {
                    err = "تغییری انجام نشده"
                } else {
                    err = null
                    confirmSave = true
                }
            }, Modifier.weight(1f), icon = Icons.Rounded.Check)
        }
    }

    if (showSaleFrom) PersianDatePicker(saleFromDay, { showSaleFrom = false }) { saleFromDay = it; showSaleFrom = false }
    if (showSaleTo) PersianDatePicker(saleToDay, { showSaleTo = false }) { saleToDay = it; showSaleTo = false }
    if (showDate) PersianDatePicker(schedDay, { showDate = false }) { schedDay = it; showDate = false }
    if (confirmSave) {
        val n = build()
        val changes = if (isNew) listOf("محصول جدید «${n.name}» ساخته می‌شود") + (if (pType == "variable") listOf("نوع متغیر با ${fa(comboCount(attrs()))} تنوع، قیمت ${priceText(n.regularPrice)}") else emptyList()) else diff(n)
        ConfirmDialog(
            title = if (isNew) "ساخت محصول؟" else "اعمال این تغییرات؟",
            onConfirm = {
                confirmSave = false
                if (isNew && pType == "variable") {
                    woo.createVariable(n, attrs(), n.regularPrice, n.salePrice, en(varQty).trim().toIntOrNull(), attrIdMap()) { ok ->
                        if (ok) {
                            val nid = woo.lastCreatedId
                            onClose()
                            if (nid > 0L) onVariations(n.copy(id = nid))
                        }
                    }
                } else {
                    woo.save(n, n.image != orig.image || n.gallery != orig.gallery) { if (it) onClose() }
                }
            },
            onDismiss = { confirmSave = false }
        ) {
            changes.forEach { Text("• $it", fontSize = 13.sp) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف «${orig.name}»؟",
            text = "محصول به زباله منتقل می‌شود و از آنجا قابل بازیابی است.",
            confirmText = "حذف", danger = true,
            onConfirm = {
                confirmDelete = false
                woo.deleteProduct(orig, false) { if (it) onClose() }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ───────────────────────── تنوع‌ها ─────────────────────────

@Composable
fun VariationsSheet(woo: WooState, product: WProduct, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var loaded by remember { mutableStateOf<List<WVariation>?>(null) }
    val vars = remember { mutableStateListOf<WVariation>() }
    var failed by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var newAttrs by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var newSale by remember { mutableStateOf("") }
    var newQty by remember { mutableStateOf("") }
    var confirmAdd by remember { mutableStateOf(false) }
    var pickId by remember { mutableStateOf(0L) }
    val moreOpen = remember { mutableStateListOf<Long>() }
    var dateFor by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = pickId
        if (uri != null && id != 0L) {
            woo.uploadImage(ctx, uri) { url ->
                if (url != null) {
                    val i = vars.indexOfFirst { it.id == id }
                    if (i >= 0) vars[i] = vars[i].copy(newImage = url)
                }
            }
        }
    }

    LaunchedEffect(product.id, reloadKey) {
        loaded = null
        woo.loadVariations(product.id) {
            if (it == null) failed = true else {
                loaded = it
                vars.clear()
                vars.addAll(it)
            }
        }
    }

    fun changed(): List<WVariation> = vars.filter { v -> loaded?.firstOrNull { it.id == v.id } != v }

    AppSheet(onClose, "تنوع‌های ${product.name}") {
        when {
            failed -> Text("خواندن تنوع‌ها ناموفق بود", color = cs.error)
            loaded == null -> SkeletonList(4)
            vars.isEmpty() -> EmptyState("این محصول تنوعی ندارد")
            else -> {
                SearchField(q, { q = it }, "جستجوی تنوع یا SKU...")
                AppCard(pad = 12.dp) {
                    Text("تغییر درصدی قیمت همهٔ تنوع‌های نمایش‌داده‌شده", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(pct, { pct = it }, "درصد", Modifier.weight(1f), number = true)
                        ActionButton("+", { adjustAll(vars, q, num(pct) ?: 0.0) }, kind = BtnKind.TONAL)
                        ActionButton("−", { adjustAll(vars, q, -(num(pct) ?: 0.0)) }, kind = BtnKind.TONAL)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("حراج با این درصد تخفیف", { saleAll(vars, q, num(pct) ?: 0.0) }, Modifier.weight(1f), kind = BtnKind.TONAL)
                        ActionButton("حذف همهٔ حراج‌ها", { saleAll(vars, q, 0.0) }, Modifier.weight(1f), kind = BtnKind.TONAL)
                    }
                }
                val shown = vars.filter { q.isBlank() || it.attrs.contains(q.trim(), true) || it.sku.contains(q.trim(), true) }
                shown.forEach { v ->
                    val idx = vars.indexOfFirst { it.id == v.id }
                    val dirty = loaded?.firstOrNull { it.id == v.id } != v
                    AppCard(pad = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.attrs, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (dirty) Pill("ویرایش‌شده", color = cs.tertiary)
                        }
                        if (v.sku.isNotBlank()) Text("SKU: ${v.sku}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                                RemoteImage(v.newImage ?: v.image, Modifier.fillMaxSize())
                            }
                            ActionButton(
                                "عکس این تنوع از گالری", { pickId = v.id; picker.launch("image/*") },
                                Modifier.weight(1f), icon = Icons.Rounded.PhotoLibrary, kind = BtnKind.TONAL
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(v.regularPrice, { vars[idx] = vars[idx].copy(regularPrice = en(it)) }, "قیمت عادی (${woo.currency})", Modifier.weight(1f), number = true)
                            Field(v.salePrice, { vars[idx] = vars[idx].copy(salePrice = en(it)) }, "قیمت حراج", Modifier.weight(1f), number = true)
                        }
                        val rp = num(v.regularPrice)
                        val sp = num(v.salePrice)
                        if (rp != null && sp != null && sp >= rp) {
                            Text("قیمت حراج باید کمتر از قیمت عادی باشد", fontSize = 12.sp, color = cs.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Field(
                                v.stockQty?.toString() ?: "",
                                { vars[idx] = vars[idx].copy(manageStock = true, stockQty = en(it).toIntOrNull()) },
                                "تعداد", Modifier.weight(1f), number = true
                            )
                            val inStock = v.stockStatus == "instock"
                            ActionButton(
                                if (inStock) "موجود" else "ناموجود",
                                { vars[idx] = vars[idx].copy(stockStatus = if (inStock) "outofstock" else "instock") },
                                kind = if (inStock) BtnKind.TONAL else BtnKind.DANGER
                            )
                        }
                        val isOpen = moreOpen.contains(v.id)
                        TextButton(onClick = { if (isOpen) moreOpen.remove(v.id) else moreOpen.add(v.id) }) {
                            Text(if (isOpen) "بستن جزئیات" else "جزئیات بیشتر (SKU، حراج زمان‌دار، وزن، ابعاد...)")
                        }
                        if (isOpen) {
                            Field(v.sku, { vars[idx] = vars[idx].copy(sku = it) }, "SKU")
                            Spacer(Modifier.height(6.dp))
                            Field(v.description, { vars[idx] = vars[idx].copy(description = it) }, "توضیحات این تنوع", singleLine = false, minLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(v.saleFrom.takeIf { it.isNotBlank() }?.let { "حراج از " + fmtDay(java.time.LocalDate.parse(it).toEpochDay()) } ?: "شروع حراج",
                                    { dateFor = v.id to true }, Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, kind = BtnKind.TONAL)
                                ActionButton(v.saleTo.takeIf { it.isNotBlank() }?.let { "تا " + fmtDay(java.time.LocalDate.parse(it).toEpochDay()) } ?: "پایان حراج",
                                    { dateFor = v.id to false }, Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, kind = BtnKind.TONAL)
                            }
                            if (v.saleFrom.isNotBlank() || v.saleTo.isNotBlank()) {
                                TextButton(onClick = { vars[idx] = vars[idx].copy(saleFrom = "", saleTo = "") }) { Text("حذف زمان‌بندی حراج") }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(v.weight, { vars[idx] = vars[idx].copy(weight = en(it)) }, "وزن", Modifier.weight(1f), number = true)
                                Field(v.length, { vars[idx] = vars[idx].copy(length = en(it)) }, "طول", Modifier.weight(1f), number = true)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(v.width, { vars[idx] = vars[idx].copy(width = en(it)) }, "عرض", Modifier.weight(1f), number = true)
                                Field(v.height, { vars[idx] = vars[idx].copy(height = en(it)) }, "ارتفاع", Modifier.weight(1f), number = true)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("وضعیت انتشار", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Choices(listOf("publish" to "فعال", "private" to "خصوصی"), v.status) { vars[idx] = vars[idx].copy(status = it) }
                            Text("پیش‌سفارش", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Choices(listOf("no" to "غیرمجاز", "notify" to "با اطلاع", "yes" to "مجاز"), v.backorders) { vars[idx] = vars[idx].copy(backorders = it) }
                            Text("مالیات", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Choices(listOf("taxable" to "مشمول", "shipping" to "فقط ارسال", "none" to "بدون"), v.taxStatus) { vars[idx] = vars[idx].copy(taxStatus = it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("مجازی", Modifier.weight(1f))
                                Switch(v.virtual, { vars[idx] = vars[idx].copy(virtual = it) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("دانلودی", Modifier.weight(1f))
                                Switch(v.downloadable, { vars[idx] = vars[idx].copy(downloadable = it) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("مدیریت موجودی", Modifier.weight(1f))
                                Switch(v.manageStock, { vars[idx] = vars[idx].copy(manageStock = it) })
                            }
                        }
                    }
                }
                ActionButton(
                    "ذخیرهٔ تغییرات (${fa(changed().size)})",
                    { confirm = true }, Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Check, enabled = changed().isNotEmpty()
                )
            }
        }
        AppCard(pad = 12.dp) {
            Text("افزودن تنوع جدید", fontWeight = FontWeight.Bold)
            Text(
                "ویژگی‌ها را به‌صورت «نام=مقدار» بنویس و با ، جدا کن. مثال: رنگ=قرمز، سایز=L",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Field(newAttrs, { newAttrs = it }, "ویژگی‌ها (رنگ=قرمز، سایز=L)")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(newPrice, { newPrice = it }, "قیمت", Modifier.weight(1f), number = true)
                Field(newSale, { newSale = it }, "حراج", Modifier.weight(1f), number = true)
                Field(newQty, { newQty = it }, "تعداد", Modifier.weight(1f), number = true)
            }
            Spacer(Modifier.height(8.dp))
            ActionButton("افزودن تنوع", {
                if (parseAttrs(newAttrs).isEmpty()) woo.message = "ویژگی‌ها را به‌صورت نام=مقدار وارد کن"
                else if (num(newPrice) == null) woo.message = "قیمت را وارد کن"
                else confirmAdd = true
            }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }

    dateFor?.let { (vid, isFrom) ->
        val i = vars.indexOfFirst { it.id == vid }
        val cur = if (i < 0) null else (if (isFrom) vars[i].saleFrom else vars[i].saleTo).takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it).toEpochDay() }
        PersianDatePicker(cur, { dateFor = null }) { day ->
            if (i >= 0) {
                val iso = java.time.LocalDate.ofEpochDay(day).toString()
                vars[i] = if (isFrom) vars[i].copy(saleFrom = iso) else vars[i].copy(saleTo = iso)
            }
            dateFor = null
        }
    }
    if (confirmAdd) {
        ConfirmDialog(
            title = "افزودن تنوع جدید؟",
            text = parseAttrs(newAttrs).joinToString(" · ") { "${it.first}: ${it.second}" } + "\nقیمت ${priceText(newPrice)}",
            onConfirm = {
                confirmAdd = false
                woo.addVariation(
                    product.id, parseAttrs(newAttrs), en(newPrice).trim(), en(newSale).trim(),
                    en(newQty).trim().toIntOrNull()
                ) { ok -> if (ok) { newAttrs = ""; reloadKey++ } }
            },
            onDismiss = { confirmAdd = false }
        )
    }
    if (confirm) {
        val ch = changed()
        ConfirmDialog(
            title = "اعمال تغییر روی ${fa(ch.size)} تنوع؟",
            onConfirm = {
                confirm = false
                woo.saveVariations(product.id, ch) { if (it) onClose() }
            },
            onDismiss = { confirm = false }
        ) {
            ch.take(10).forEach { v ->
                val o = loaded?.firstOrNull { it.id == v.id }
                val parts = ArrayList<String>()
                if (o?.regularPrice != v.regularPrice) parts.add("قیمت ${priceText(o?.regularPrice ?: "")} ← ${priceText(v.regularPrice)}")
                if (o?.salePrice != v.salePrice) parts.add("حراج ${priceText(o?.salePrice ?: "")} ← ${priceText(v.salePrice)}")
                if (o?.stockQty != v.stockQty || o?.stockStatus != v.stockStatus) parts.add("موجودی")
                if (v.newImage != null) parts.add("عکس جدید")
                if (parts.isEmpty()) parts.add("سایر مشخصات")
                Text("• ${v.attrs}: ${parts.joinToString("، ")}", fontSize = 12.sp)
            }
            if (ch.size > 10) Text("و ${fa(ch.size - 10)} مورد دیگر...", fontSize = 12.sp)
        }
    }
}

private fun adjustAll(vars: MutableList<WVariation>, q: String, pct: Double) {
    if (pct == 0.0) return
    for (i in vars.indices) {
        val v = vars[i]
        if (q.isNotBlank() && !v.attrs.contains(q.trim(), true) && !v.sku.contains(q.trim(), true)) continue
        val r = v.regularPrice.toDoubleOrNull() ?: continue
        val nv = roundStep(r * (1 + pct / 100.0), 1)
        if (nv < 0) continue
        vars[i] = v.copy(regularPrice = fmtPrice(nv))
    }
}

private fun saleAll(vars: MutableList<WVariation>, q: String, pct: Double) {
    for (i in vars.indices) {
        val v = vars[i]
        if (q.isNotBlank() && !v.attrs.contains(q.trim(), true) && !v.sku.contains(q.trim(), true)) continue
        if (pct <= 0.0) { vars[i] = v.copy(salePrice = ""); continue }
        if (pct >= 100.0) continue
        val r = v.regularPrice.toDoubleOrNull() ?: continue
        vars[i] = v.copy(salePrice = fmtPrice(roundStep(r * (1 - pct / 100.0), 1)))
    }
}

// ───────────────────────── ویرایش گروهی ─────────────────────────

@Composable
fun BulkSheet(woo: WooState, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val chosen = woo.sel.values.toList()
    var kind by remember { mutableStateOf("price") }
    var mode by remember { mutableStateOf(Mode.PCT_UP) }
    var value by remember { mutableStateOf("") }
    var round by remember { mutableStateOf(0) }
    var saleKind by remember { mutableStateOf(0) }
    var stockKind by remember { mutableStateOf(0) }
    var stockStatus by remember { mutableStateOf("instock") }
    var qtyAdd by remember { mutableStateOf(true) }
    var backorders by remember { mutableStateOf("no") }
    var status by remember { mutableStateOf("publish") }
    var catIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var catMode by remember { mutableStateOf(CatMode.ADD) }
    var find by remember { mutableStateOf("") }
    var repl by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var featuredOn by remember { mutableStateOf(true) }
    var permanent by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    fun op(): BulkOp? {
        val v = num(value)
        return when (kind) {
            "price" -> if (v == null || v < 0) null else BPrice(false, mode, v, round)
            "sale" -> when (saleKind) {
                0 -> if (v == null || v <= 0 || v >= 100) null else BSalePct(v, round)
                1 -> if (v == null || v <= 0) null else BPrice(true, Mode.SET, v, round)
                else -> BClearSale
            }
            "stock" -> when (stockKind) {
                0 -> BStockStatus(stockStatus)
                1 -> en(value).trim().toIntOrNull()?.let { BQty(qtyAdd, it) }
                else -> BBackorders(backorders)
            }
            "status" -> BStatus(status)
            "cats" -> if (catIds.isEmpty()) null else BCats(catIds, catMode)
            "name" -> if (find.isBlank() && prefix.isBlank() && suffix.isBlank()) null else BName(find, repl, prefix, suffix)
            "featured" -> BFeatured(featuredOn)
            "delete" -> BDelete(permanent)
            else -> null
        }
    }

    val current = op()
    val plans = if (current != null && current !is BDelete) chosen.mapNotNull { planBulk(it, current) } else emptyList()

    AppSheet(onClose, "ویرایش گروهی · ${fa(chosen.size)} محصول") {
        Choices(
            listOf(
                "price" to "قیمت", "sale" to "حراج", "stock" to "موجودی", "status" to "وضعیت",
                "cats" to "دسته", "name" to "نام", "featured" to "ویژه", "delete" to "حذف"
            ),
            kind
        ) { kind = it }

        when (kind) {
            "price", "sale" -> {
                if (kind == "price") {
                    Choices(
                        listOf(
                            Mode.PCT_UP to "افزایش ٪", Mode.PCT_DOWN to "کاهش ٪", Mode.AMT_UP to "افزایش مبلغ",
                            Mode.AMT_DOWN to "کاهش مبلغ", Mode.SET to "قیمت ثابت"
                        ),
                        mode
                    ) { mode = it }
                } else {
                    Choices(listOf(0 to "درصد تخفیف", 1 to "قیمت حراج ثابت", 2 to "پایان حراج"), saleKind) { saleKind = it }
                }
                if (!(kind == "sale" && saleKind == 2)) {
                    Field(
                        value, { value = it },
                        if (kind == "price" && (mode == Mode.PCT_UP || mode == Mode.PCT_DOWN) || kind == "sale" && saleKind == 0) "درصد" else "مبلغ (${woo.currency})",
                        number = true
                    )
                    Text("رند کردن", fontWeight = FontWeight.SemiBold)
                    Choices(listOf(0 to "دقیق", 1 to "عدد صحیح", 1000 to "هزار", 10000 to "ده‌هزار"), round) { round = it }
                }
                Text("برای محصولات متغیر، روی همهٔ تنوع‌ها اعمال می‌شود.", fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            "stock" -> {
                Choices(listOf(0 to "وضعیت موجودی", 1 to "تعداد", 2 to "پیش‌سفارش"), stockKind) { stockKind = it }
                when (stockKind) {
                    0 -> Choices(listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"), stockStatus) { stockStatus = it }
                    1 -> {
                        Choices(listOf(true to "افزودن به موجودی", false to "تعیین مقدار"), qtyAdd) { qtyAdd = it }
                        Field(value, { value = it }, "تعداد", number = true)
                    }
                    else -> Choices(listOf("no" to "غیرمجاز", "notify" to "با اطلاع", "yes" to "مجاز"), backorders) { backorders = it }
                }
            }
            "status" -> Choices(
                listOf("publish" to "منتشر", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"), status
            ) { status = it }
            "cats" -> {
                Choices(listOf(CatMode.ADD to "افزودن", CatMode.REMOVE to "حذف از", CatMode.REPLACE to "جایگزینی"), catMode) { catMode = it }
                CategoryPicker(woo.categories, catIds, { catIds = it })
            }
            "name" -> {
                Field(find, { find = it }, "متن برای جایگزینی (اختیاری)")
                Field(repl, { repl = it }, "جایگزین")
                Field(prefix, { prefix = it }, "پیشوند")
                Field(suffix, { suffix = it }, "پسوند")
            }
            "featured" -> Choices(listOf(true to "ویژه شود", false to "از ویژه‌ها حذف شود"), featuredOn) { featuredOn = it }
            "delete" -> {
                Text("حذف دسته‌جمعی ${fa(chosen.size)} محصول.", color = cs.error, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("حذف برای همیشه (بدون زباله)", Modifier.weight(1f))
                    Switch(permanent, { permanent = it })
                }
            }
        }

        if (current != null && current !is BDelete) {
            AppCard(pad = 12.dp) {
                Text("پیش‌نمایش: ${fa(plans.size)} از ${fa(chosen.size)} محصول تغییر می‌کند", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                plans.take(5).forEach {
                    Text("• ${it.name.take(32)} — ${it.desc}", fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ActionButton(
            if (kind == "delete") "حذف…" else "اعمال روی ${fa(chosen.size)} محصول",
            { confirm = true }, Modifier.fillMaxWidth(),
            icon = if (kind == "delete") Icons.Rounded.DeleteForever else Icons.Rounded.Check,
            kind = if (kind == "delete") BtnKind.DANGER else BtnKind.PRIMARY,
            enabled = current != null && (current is BDelete || plans.isNotEmpty())
        )
    }

    if (confirm && current != null) {
        val isDel = current is BDelete
        ConfirmDialog(
            title = if (isDel) "حذف ${fa(chosen.size)} محصول؟" else "اعمال تغییر روی ${fa(plans.size)} محصول؟",
            text = if (isDel) (if ((current as BDelete).permanent) "حذف برای همیشه و بدون بازگشت است!" else "محصولات به زباله می‌روند.")
            else "بعد از اعمال می‌توانی با «بازگردانی» برگردانی.",
            confirmText = if (isDel) "حذف" else "تأیید و اعمال",
            danger = isDel,
            onConfirm = {
                confirm = false
                woo.runBulk(chosen, current)
                onClose()
            },
            onDismiss = { confirm = false }
        ) {
            if (!isDel) plans.take(6).forEach { Text("• ${it.name.take(30)}: ${it.desc}", fontSize = 12.sp) }
            if (plans.size > 6) Text("و ${fa(plans.size - 6)} مورد دیگر...", fontSize = 12.sp)
        }
    }
}
