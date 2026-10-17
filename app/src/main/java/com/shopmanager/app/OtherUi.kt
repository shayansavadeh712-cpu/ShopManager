package com.shopmanager.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ───────────────────────── اتصال ─────────────────────────

@Composable
fun ConnectScreen(w: WooState) {
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("تومان") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("اتصال فروشگاه", "ووکامرس سایتت را وصل کن و همه‌چیز را از همین‌جا مدیریت کن.")
        AppCard {
            Text("ساخت کلید API", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "۱) در داشبورد وردپرس: ووکامرس ← تنظیمات ← پیشرفته ← REST API\n" +
                        "۲) «افزودن کلید» را بزن و دسترسی را «خواندن/نوشتن» انتخاب کن\n" +
                        "۳) Consumer Key و Consumer Secret را همین‌جا وارد کن\n" +
                        "سایت باید HTTPS داشته باشد.",
                color = cs.onSurfaceVariant, fontSize = 13.sp, lineHeight = 22.sp
            )
        }
        Field(url, { url = it }, "آدرس سایت (مثلاً mysite.com)")
        Field(key, { key = it }, "Consumer Key (ck_...)")
        Field(secret, { secret = it }, "Consumer Secret (cs_...)")
        Field(cur, { cur = it }, "واحد پول (مثلاً تومان)")
        if (err != null) Text(err!!, color = cs.error, fontSize = 13.sp)
        ActionButton(
            if (busy) "در حال تست اتصال..." else "اتصال و تست",
            {
                busy = true
                err = null
                w.connect(url, key, secret, cur) { e -> busy = false; err = e }
            },
            Modifier.fillMaxWidth(), icon = Icons.Rounded.Link, enabled = !busy
        )
        Text(
            "کلیدها با Android Keystore رمزنگاری و فقط روی همین گوشی نگه‌داری می‌شوند.",
            color = cs.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

// ───────────────────────── داشبورد ─────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    val st = woo.stats
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(woo.displayShopName.ifBlank { "فروشگاه" }, greeting()) {
                IconBox(Icons.Rounded.Refresh) { woo.refreshAll() }
            }
        }
        item { SalesHero(woo, st) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Rounded.Inventory2, "محصولات") { woo.tab = 1 }
                QuickAction(Icons.Rounded.ReceiptLong, "سفارش‌ها") { woo.tab = 3 }
                QuickAction(Icons.Rounded.RemoveShoppingCart, "ناموجودها") {
                    woo.resetFilters(); woo.stockF = "outofstock"; woo.tab = 1; woo.loadProducts(true)
                }
                QuickAction(Icons.Rounded.People, "مشتری‌ها") { woo.tab = 4 }
            }
        }
        if (st != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("محصولات", fa(st.products), Icons.Rounded.Inventory2, cs.primary, Modifier.weight(1f)) { woo.tab = 1 }
                    KpiTile("ناموجود", fa(st.outOfStock), Icons.Rounded.RemoveShoppingCart, ExpenseColor, Modifier.weight(1f)) {
                        woo.resetFilters(); woo.stockF = "outofstock"; woo.tab = 1; woo.loadProducts(true)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("منتشر شده", fa(st.published), Icons.Rounded.Public, IncomeColor, Modifier.weight(1f)) {
                        woo.resetFilters(); woo.statusF = "publish"; woo.tab = 1; woo.loadProducts(true)
                    }
                    KpiTile("پیش‌نویس", fa(st.drafts), Icons.Rounded.Edit, cs.tertiary, Modifier.weight(1f)) {
                        woo.resetFilters(); woo.statusF = "draft"; woo.tab = 1; woo.loadProducts(true)
                    }
                }
            }
            item {
                AppCard {
                    SectionTitle("فروش ۱۴ روز اخیر", Icons.Rounded.BarChart)
                    if (st.series.any { it > 0f }) BarChart(st.series, st.seriesLabels, height = 130.dp)
                    else EmptyState("فروشی در این بازه ثبت نشده")
                }
            }
            if (st.orderTotals.isNotEmpty()) {
                item {
                    AppCard {
                        SectionTitle("وضعیت سفارش‌ها", Icons.Rounded.ReceiptLong)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            st.orderTotals.forEach { (slug, n) ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(orderColor(slug).copy(alpha = .16f))
                                        .clickable {
                                            woo.orderCustomer = 0L; woo.orderQuery = ""; woo.orderRange = 0
                                            woo.orderStatusF = slug; woo.tab = 3; woo.loadOrders(true)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(orderFa(slug), fontSize = 13.sp, color = orderColor(slug))
                                    Spacer(Modifier.width(6.dp))
                                    Text(fa(n), fontWeight = FontWeight.ExtraBold, color = orderColor(slug))
                                }
                            }
                        }
                    }
                }
            }
            if (st.top.isNotEmpty()) {
                item {
                    AppCard {
                        SectionTitle("پرفروش‌های ماه", Icons.Rounded.Whatshot)
                        st.top.forEachIndexed { i, (n, q) ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(fa(i + 1), Modifier.width(24.dp), color = cs.primary, fontWeight = FontWeight.ExtraBold)
                                Text(n, Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                                Pill("${fa(q)} عدد")
                            }
                        }
                    }
                }
            }
        } else {
            item { SkeletonList(3) }
        }
    }
}

@Composable
private fun SalesHero(woo: WooState, st: ShopStats?) {
    val cs = MaterialTheme.colorScheme
    AppCard(pad = 20.dp) {
        if (st == null) {
            Skeleton(Modifier.fillMaxWidth().height(130.dp))
            return@AppCard
        }
        val avg = if (st.series.isEmpty()) 0.0 else st.series.map { it.toDouble() }.average()
        val ratio = if (avg > 0.0) (st.salesToday / avg).toFloat() else 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("فروش امروز", color = cs.onSurfaceVariant, fontSize = 13.sp)
                Text(money(st.salesToday), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(woo.currency, color = cs.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Pill("${fa(st.ordersToday)} سفارش امروز", Icons.Rounded.ReceiptLong)
            }
            RingProgress(ratio, dia = 118.dp, stroke = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (avg > 0.0) "٪" + fa((ratio * 100).toInt()) else "—",
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text("نسبت به میانگین", fontSize = 9.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = cs.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("فروش این ماه", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text("${money(st.salesMonth)} ${woo.currency}", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("سفارش این ماه", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text(fa(st.ordersMonth), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(60.dp).raised(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(26.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ───────────────────────── دسته‌ها ─────────────────────────

@Composable
fun CategoriesTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WCat?>(null) }
    var creating by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(0) }
    val isBrand = mode == 1
    LaunchedEffect(mode) { if (isBrand) woo.loadBrands() }
    val src = if (isBrand) woo.brands.toList() else woo.categories.toList()
    val list = if (q.isBlank()) src else src.filter { it.name.contains(q.trim(), true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(
            if (isBrand) "برندها" else "دسته‌ها",
            if (isBrand) "${fa(woo.brands.size)} برند" else "${fa(woo.categories.size)} دسته"
        ) { IconBox(Icons.Rounded.Add, filled = true) { creating = true } }
        Choices(listOf(0 to "دسته‌ها", 1 to "برندها"), mode) { mode = it; q = "" }
        SearchField(q, { q = it }, if (isBrand) "جستجوی برند..." else "جستجوی دسته...")
        if (isBrand && !woo.brandsSupported) {
            EmptyState("برندها در این سایت فعال نیست. ووکامرس نسخهٔ ۹٫۶ به بالا یا فعال‌سازی ویژگی برندها لازم است.")
        } else if (list.isEmpty()) EmptyState(if (isBrand) "برندی پیدا نشد" else "دسته‌ای پیدا نشد")
        LazyColumn(contentPadding = PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { c ->
                AppCard(
                    Modifier.padding(start = if (q.isBlank() && !isBrand) (c.depth * 18).dp else 0.dp),
                    onClick = { if (isBrand) editing = c else woo.openCategory(c.id) }, pad = 10.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                            RemoteImage(c.image, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text("${fa(c.count)} محصول", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        if (!isBrand) {
                            IconBox(Icons.Rounded.DoneAll) {
                                woo.selectInCategory(c.id) { woo.tab = 1 }
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        IconBox(Icons.Rounded.Edit) { editing = c }
                    }
                }
            }
        }
    }

    if (creating) CategoryEditor(woo, null, isBrand) { creating = false }
    editing?.let { CategoryEditor(woo, it, isBrand) { editing = null } }
}

@Composable
private fun CategoryEditor(woo: WooState, cat: WCat?, isBrand: Boolean, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(cat?.name ?: "") }
    var parent by remember { mutableStateOf(cat?.parent ?: 0L) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }

    val noun = if (isBrand) "برند" else "دسته"
    AppSheet(onClose, if (cat == null) "$noun جدید" else "ویرایش $noun") {
        Field(name, { name = it }, "نام $noun")
        if (!isBrand) {
            Text("دستهٔ والد (اختیاری)", fontWeight = FontWeight.SemiBold)
            CategoryPicker(
                woo.categories.filter { it.id != cat?.id },
                if (parent == 0L) emptyList() else listOf(parent),
                { parent = it.firstOrNull() ?: 0L }, single = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cat != null) ActionButton("حذف", { confirmDel = true }, kind = BtnKind.DANGER, icon = Icons.Rounded.DeleteForever)
            ActionButton("ذخیره", { confirmSave = true }, Modifier.weight(1f), icon = Icons.Rounded.Check, enabled = name.isNotBlank())
        }
    }
    if (confirmSave) {
        ConfirmDialog(
            title = if (cat == null) "ساخت $noun؟" else "ذخیرهٔ تغییرات $noun؟",
            text = "نام: ${name.trim()}",
            onConfirm = {
                confirmSave = false
                if (isBrand) woo.saveBrand(cat?.id ?: 0L, name.trim()) { onClose() }
                else woo.saveCategory(cat?.id ?: 0L, name.trim(), parent) { onClose() }
            },
            onDismiss = { confirmSave = false }
        )
    }
    if (confirmDel && cat != null) {
        ConfirmDialog(
            title = "حذف $noun «${cat.name}»؟",
            text = "محصولات حذف نمی‌شوند، فقط از این دسته خارج می‌شوند.",
            confirmText = "حذف", danger = true,
            onConfirm = {
                confirmDel = false
                if (isBrand) woo.deleteBrand(cat.id) { onClose() }
                else woo.deleteCategory(cat.id) { onClose() }
            },
            onDismiss = { confirmDel = false }
        )
    }
}

// ───────────────────────── مشتری‌ها ─────────────────────────

@Composable
fun CustomersTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<WCustomer?>(null) }
    LaunchedEffect(Unit) { if (woo.customers.isEmpty()) woo.loadCustomers(true) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(woo.customerQuery) {
        if (first) { first = false; return@LaunchedEffect }
        delay(600)
        woo.loadCustomers(true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("مشتری‌ها") { IconBox(Icons.Rounded.Refresh) { woo.loadCustomers(true) } }
        SearchField(woo.customerQuery, { woo.customerQuery = it }, "جستجوی نام یا ایمیل...") { woo.loadCustomers(true) }
        Box(Modifier.weight(1f)) {
            val err = woo.customersError
            when {
                woo.customers.isEmpty() && woo.customersLoading -> SkeletonList()
                woo.customers.isEmpty() && err != null -> Text(err, color = cs.error, modifier = Modifier.padding(20.dp))
                woo.customers.isEmpty() -> EmptyState("مشتری‌ای پیدا نشد")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(woo.customers, key = { it.id }) { c ->
                        AppCard(onClick = { open = c }, pad = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(cs.primary.copy(alpha = .2f)),
                                    contentAlignment = Alignment.Center
                                ) { Text(c.name.take(1), color = cs.primary, fontWeight = FontWeight.ExtraBold) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(c.email, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${fa(c.orders)} سفارش", fontSize = 12.sp)
                                    Text("${money(c.spent)} ${woo.currency}", fontSize = 12.sp, color = cs.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (woo.customersHasMore) {
                        item {
                            LaunchedEffect(woo.customers.size) { woo.loadCustomers(false) }
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { c ->
        AppSheet({ open = null }, c.name) {
            if (c.email.isNotBlank()) Text(c.email)
            if (c.phone.isNotBlank()) Text("📞 ${fa(c.phone)}")
            Text("عضویت: ${c.date}", color = cs.onSurfaceVariant, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile("سفارش", fa(c.orders), Icons.Rounded.ReceiptLong, cs.primary, Modifier.weight(1f))
                KpiTile("مجموع خرید", money(c.spent), Icons.Rounded.Paid, IncomeColor, Modifier.weight(1f))
            }
            ActionButton(
                "مشاهدهٔ سفارش‌ها", { woo.openCustomerOrders(c.id, c.name); open = null },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.ReceiptLong
            )
        }
    }
}

// ───────────────────────── بیشتر ─────────────────────────

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick, pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).raised(CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = cs.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(sub, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronLeft, null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun MoreTab(woo: WooState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("بیشتر")
        MoreItem(Icons.Rounded.Category, "دسته‌ها و برندها", "ساخت و ویرایش، دیدن محصولات هر دسته") { woo.tab = 2 }
        MoreItem(Icons.Rounded.People, "مشتری‌ها", "لیست مشتری‌ها و سفارش‌هایشان") { woo.tab = 4 }
        MoreItem(Icons.Rounded.Settings, "تنظیمات", "اعلان‌ها، اطلاعات فاکتور، ظاهر برنامه، حساب") { woo.tab = 5 }
    }
}
