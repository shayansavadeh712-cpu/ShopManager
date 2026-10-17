package com.shopmanager.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val StockOk = IncomeColor
private val StockNo = ExpenseColor

private fun num(s: String): Double? = en(s).trim().toDoubleOrNull()

// ───────────────────────── تب محصولات ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductsTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<WProduct?>(null) }
    var creating by remember { mutableStateOf(false) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBox(if (woo.viewGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView) { woo.viewGrid = !woo.viewGrid }
                IconBox(Icons.Rounded.Add, filled = true) { creating = true }
            }
        }
        SearchField(woo.query, { woo.query = it }, "جستجوی نام، SKU یا توضیحات...") { woo.loadProducts(true) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Choices(
                    listOf("any" to "همه", "publish" to "منتشر شده", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"),
                    woo.statusF
                ) { woo.statusF = it; woo.loadProducts(true) }
            }
            Box {
                IconBox(Icons.Rounded.Tune) { showFilters = true }
                if (woo.activeFilterCount > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) { Text(fa(woo.activeFilterCount), color = cs.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
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

    if (creating) ProductEditor(woo, null, onVariations = { }) { creating = false }
    editing?.let { p ->
        ProductEditor(woo, p, onVariations = { showVars = it }) { editing = null }
    }
    showVars?.let { p -> VariationsSheet(woo, p) { showVars = null } }
    if (showFilters) FilterSheet(woo) { showFilters = false }
    if (showBulk) BulkSheet(woo) { showBulk = false }
}

@Composable
fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(if (filled) cs.primary else cs.surfaceVariant.copy(alpha = .55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (filled) cs.onPrimary else cs.onSurface, modifier = Modifier.size(20.dp)) }
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) cs.primary.copy(alpha = .16f) else cs.surfaceVariant.copy(alpha = .4f))
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(14.dp))) {
            RemoteImage(p.image, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name.ifBlank { "(بدون نام)" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (p.sku.isNotBlank()) Text("SKU: ${p.sku}", fontSize = 11.sp, color = cs.onSurfaceVariant)
            PriceLine(p, currency)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StockPill(p)
                if (p.status != "publish") Pill(statusFa(p.status), color = cs.outline)
                if (p.featured) Pill("ویژه", color = cs.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductCard(p: WProduct, currency: String, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) cs.primary.copy(alpha = .16f) else cs.surfaceVariant.copy(alpha = .4f))
            .border(1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))) {
            RemoteImage(p.image, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary)
                }
            }
        }
        Text(p.name.ifBlank { "(بدون نام)" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        PriceLine(p, currency)
        StockPill(p)
    }
}

// ───────────────────────── فیلتر پیشرفته ─────────────────────────

@Composable
private fun FilterSheet(woo: WooState, onClose: () -> Unit) {
    AppSheet(onClose, "فیلتر و مرتب‌سازی") {
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

// ───────────────────────── ویرایشگر محصول ─────────────────────────

@Composable
fun ProductEditor(woo: WooState, initial: WProduct?, onVariations: (WProduct) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isNew = initial == null
    val orig = initial ?: WProduct()
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
    var desc by remember { mutableStateOf(orig.description) }
    var shortDesc by remember { mutableStateOf(orig.shortDescription) }
    var weight by remember { mutableStateOf(orig.weight) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun build(): WProduct = orig.copy(
        name = name.trim(), sku = sku.trim(),
        regularPrice = en(regular).trim(), salePrice = en(sale).trim(),
        status = status, stockStatus = stockStatus, manageStock = manage,
        stockQty = if (manage) (en(qty).trim().toIntOrNull() ?: 0) else null,
        backorders = backorders, featured = featured, catIds = cats,
        catNames = woo.categories.filter { cats.contains(it.id) }.map { it.name },
        image = image.trim(), description = desc, shortDescription = shortDesc, weight = en(weight).trim()
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
        if (orig.image != n.image) d.add("تصویر تغییر کرد")
        if (orig.description != n.description) d.add("توضیحات تغییر کرد")
        if (orig.shortDescription != n.shortDescription) d.add("توضیح کوتاه تغییر کرد")
        ch("وزن", orig.weight, n.weight)
        return d
    }

    AppSheet(onClose, if (isNew) "محصول جدید" else "ویرایش محصول") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) { RemoteImage(image, Modifier.fillMaxSize(), 360) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(typeFa(orig.type))
                        Pill("#${fa(orig.id)}", color = cs.outline)
                    }
                    if (orig.totalSales > 0) Text("${fa(orig.totalSales)} فروش", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
        Field(name, { name = it }, "نام محصول")
        Field(image, { image = it }, "آدرس تصویر (URL)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(sku, { sku = it }, "SKU", Modifier.weight(1f))
            Field(weight, { weight = it }, "وزن", Modifier.weight(1f), number = true)
        }
        if (orig.priceEditable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(regular, { regular = it }, "قیمت (${woo.currency})", Modifier.weight(1f), number = true)
                Field(sale, { sale = it }, "قیمت حراج", Modifier.weight(1f), number = true)
            }
        } else if (orig.type == "variable") {
            AppCard(onClick = { onVariations(orig) }, pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Layers, null, tint = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("مدیریت تنوع‌ها (${fa(orig.varCount)})", fontWeight = FontWeight.Bold)
                        Text("قیمت، حراج و موجودی هر تنوع", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        } else {
            Text("قیمت این نوع محصول از خود محصول قابل ویرایش نیست.", color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
        Text("وضعیت انتشار", fontWeight = FontWeight.SemiBold)
        Choices(listOf("publish" to "منتشر", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"), status) { status = it }
        Text("موجودی", fontWeight = FontWeight.SemiBold)
        Choices(listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"), stockStatus) { stockStatus = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("مدیریت تعداد موجودی", Modifier.weight(1f))
            Switch(manage, { manage = it })
        }
        if (manage) {
            Field(qty, { qty = it }, "تعداد", number = true)
            Text("پیش‌سفارش در صورت اتمام", fontWeight = FontWeight.SemiBold)
            Choices(listOf("no" to "غیرمجاز", "notify" to "با اطلاع", "yes" to "مجاز"), backorders) { backorders = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("محصول ویژه", Modifier.weight(1f))
            Switch(featured, { featured = it })
        }
        Text("دسته‌ها", fontWeight = FontWeight.SemiBold)
        CategoryPicker(woo.categories, cats, { cats = it })
        Field(shortDesc, { shortDesc = it }, "توضیح کوتاه", singleLine = false, minLines = 2)
        Field(desc, { desc = it }, "توضیحات", singleLine = false, minLines = 4)
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
                } else if (!isNew && diff(build()).isEmpty()) {
                    err = "تغییری انجام نشده"
                } else {
                    err = null
                    confirmSave = true
                }
            }, Modifier.weight(1f), icon = Icons.Rounded.Check)
        }
    }

    if (confirmSave) {
        val n = build()
        val changes = if (isNew) listOf("محصول جدید «${n.name}» ساخته می‌شود") else diff(n)
        ConfirmDialog(
            title = if (isNew) "ساخت محصول؟" else "اعمال این تغییرات؟",
            onConfirm = {
                confirmSave = false
                woo.save(n, n.image != orig.image) { if (it) onClose() }
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

    LaunchedEffect(product.id) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(v.regularPrice, { vars[idx] = vars[idx].copy(regularPrice = en(it)) }, "قیمت", Modifier.weight(1f), number = true)
                            Field(v.salePrice, { vars[idx] = vars[idx].copy(salePrice = en(it)) }, "حراج", Modifier.weight(1f), number = true)
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
                    }
                }
                ActionButton(
                    "ذخیرهٔ تغییرات (${fa(changed().size)})",
                    { confirm = true }, Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Check, enabled = changed().isNotEmpty()
                )
            }
        }
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
                Text("• ${v.attrs}: ${priceText(o?.regularPrice ?: "")} ← ${priceText(v.regularPrice)}", fontSize = 12.sp)
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
