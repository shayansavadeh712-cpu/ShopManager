@file:OptIn(ExperimentalMaterial3Api::class)

package com.shopmanager.app

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val WarnColor = Color(0xFFFBBF24)

// ───────────────────────── دسته‌بندی‌ها ─────────────────────────

private fun orderCats(all: List<WCat>): List<Pair<WCat, Int>> {
    val out = ArrayList<Pair<WCat, Int>>()
    val ids = all.map { it.id }.toSet()
    fun walk(parent: Long, depth: Int) {
        for (c in all) {
            if (c.parent == parent) {
                out.add(c to depth)
                if (depth < 6) walk(c.id, depth + 1)
            }
        }
    }
    walk(0L, 0)
    for (c in all) {
        if (c.parent != 0L && !ids.contains(c.parent)) out.add(c to 0)
    }
    return out
}

@Composable
fun CategoriesTab(w: WooState) {
    val cs = MaterialTheme.colorScheme
    var edit by remember { mutableStateOf<WCat?>(null) }
    var confirm by remember { mutableStateOf<WCat?>(null) }
    val rows = orderCats(w.categories.toList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isEmpty()) item { EmptyState("هنوز دسته‌ای نیست") }
            items(rows, key = { it.first.id }) { (c, depth) ->
                AppCard(Modifier.padding(start = (depth * 16).dp), onClick = { edit = c }, pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            if (c.description.isNotBlank()) {
                                Text(
                                    c.description, fontSize = 12.sp, color = cs.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Pill("${fa(c.count)} محصول")
                        IconButton(onClick = { confirm = c }) {
                            Icon(Icons.Rounded.DeleteForever, null, tint = cs.error)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { edit = WCat(0L, "") },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("دستهٔ جدید") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 16.dp, bottom = 100.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary
        )
    }

    edit?.let { c -> CatDialog(w, c) { edit = null } }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("حذف دسته؟") },
            text = { Text("دستهٔ «${c.name}» حذف می‌شود. محصولات داخلش پاک نمی‌شوند، فقط از این دسته خارج می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    w.mg.deleteCategory(c.id) { }
                }) { Text("حذف", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun CatDialog(w: WooState, c: WCat, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(c.name) }
    var desc by remember { mutableStateOf(c.description) }
    var parent by remember { mutableStateOf(c.parent) }
    var err by remember { mutableStateOf<String?>(null) }
    val opts: List<Pair<Long, String>> =
        listOf(0L to "بدون والد") + w.categories.filter { it.id != c.id }.map { it.id to it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "دستهٔ جدید" else "ویرایش دسته") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "نام دسته", Modifier.padding(top = 4.dp))
                Field(desc, { desc = it }, "توضیح (اختیاری)", lines = 2)
                Text("دستهٔ والد", fontWeight = FontWeight.Medium)
                Choices(opts, parent) { parent = it }
                err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && w.busy == null,
                onClick = {
                    w.mg.saveCategory(c.id, name.trim(), parent, desc.trim()) { ok ->
                        if (ok) { onDismiss() } else { err = w.message }
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ───────────────────────── مشتری‌ها (کاربران فروشگاه) ─────────────────────────

@Composable
fun CustomersTab(w: WooState) {
    val cs = MaterialTheme.colorScheme
    val mg = w.mg
    var text by remember { mutableStateOf(mg.custQuery) }
    var open by remember { mutableStateOf<WCustomer?>(null) }

    LaunchedEffect(Unit) { if (mg.customers.isEmpty()) mg.loadCustomers(true) }
    LaunchedEffect(text) {
        if (text != mg.custQuery) {
            delay(500)
            mg.custQuery = text
            mg.loadCustomers(true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        placeholder = { Text("جستجوی نام، ایمیل یا نام کاربری...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (text.isNotEmpty()) {
                            { IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, null) } }
                        } else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)
                    )
                    Text("${fa(mg.custTotal)} مشتری", color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (mg.custError != null) {
                item {
                    AppCard {
                        Text(mg.custError ?: "", color = cs.error)
                        TextButton(onClick = { mg.loadCustomers(true) }) { Text("تلاش دوباره") }
                    }
                }
            }
            if (mg.customers.isEmpty() && !mg.custLoading && mg.custError == null) item { EmptyState("مشتری‌ای پیدا نشد") }
            items(mg.customers, key = { it.id }) { c ->
                AppCard(onClick = { open = c }, pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.fullName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Pill("${fa(c.orders)} سفارش")
                    }
                    if (c.email.isNotBlank()) Text(c.email, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    if (c.phone.isNotBlank()) Text(fa(c.phone), fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "خرید کل: ${priceText(c.spent)} ${w.currency}",
                        fontWeight = FontWeight.Bold, color = cs.primary, fontSize = 13.sp
                    )
                }
            }
            item {
                if (mg.custLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                } else if (mg.custHasMore) {
                    LaunchedEffect(mg.customers.size) { mg.loadCustomers(false) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                open = WCustomer(0L, "", "", "", "", "", "", "", 0, "")
            },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("مشتری جدید") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 16.dp, bottom = 100.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary
        )
    }

    open?.let { c -> CustomerDialog(w, c) { open = null } }
}

@Composable
private fun CustomerDialog(w: WooState, c: WCustomer, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mg = w.mg
    val isNew = c.id == 0L
    var first by remember { mutableStateOf(c.first) }
    var last by remember { mutableStateOf(c.last) }
    var email by remember { mutableStateOf(c.email) }
    var phone by remember { mutableStateOf(c.phone) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(c.id) { if (!isNew) mg.loadCustomerOrders(c.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "مشتری جدید" else c.fullName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) {
                    Text(
                        "ثبت‌نام: ${c.date} · ${fa(c.orders)} سفارش · ${priceText(c.spent)} ${w.currency}",
                        fontSize = 12.sp, color = cs.onSurfaceVariant
                    )
                }
                Field(first, { first = it }, "نام", Modifier.padding(top = 4.dp))
                Field(last, { last = it }, "نام خانوادگی")
                Field(email, { email = it }, "ایمیل")
                Field(phone, { phone = it }, "تلفن")
                if (isNew) {
                    Field(username, { username = it }, "نام کاربری (اختیاری)")
                    Field(password, { password = it }, "رمز عبور (اختیاری؛ خالی = خودکار)")
                }
                err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
                if (!isNew) {
                    Text("سفارش‌های اخیر", fontWeight = FontWeight.Medium)
                    if (mg.custOrdersLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (mg.custOrders.isEmpty()) {
                        Text("سفارشی ندارد", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    mg.custOrders.forEach { o ->
                        Text(
                            "#${fa(o.number)} · ${orderFa(o.status)} · ${priceText(o.total)} ${w.currency}",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = email.isNotBlank() && w.busy == null,
                onClick = {
                    mg.saveCustomer(
                        c, first.trim(), last.trim(), email.trim(), en(phone).trim(),
                        username.trim(), password.trim()
                    ) { ok -> if (ok) { onDismiss() } else { err = w.message } }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirm = true }) { Text("حذف", color = cs.error) }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        }
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("حذف مشتری؟") },
            text = { Text("حساب «${c.fullName}» برای همیشه از سایت حذف می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    mg.deleteCustomer(c.id) { ok -> if (ok) onDismiss() }
                }) { Text("حذف", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } }
        )
    }
}

// ───────────────────────── مقاله‌ها (وردپرس) ─────────────────────────

@Composable
fun PostsTab(w: WooState) {
    val cs = MaterialTheme.colorScheme
    val mg = w.mg
    val uri = LocalUriHandler.current
    var text by remember { mutableStateOf(mg.postQuery) }
    var edit by remember { mutableStateOf<WPost?>(null) }

    LaunchedEffect(Unit) { if (mg.posts.isEmpty()) mg.loadPosts(true) }
    LaunchedEffect(text) {
        if (text != mg.postQuery) {
            delay(500)
            mg.postQuery = text
            mg.loadPosts(true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { WpLoginCard(w) }
            item {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("جستجوی مقاله...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (text.isNotEmpty()) {
                        { IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, null) } }
                    } else null,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)
                )
            }
            if (mg.wpReady) {
                item {
                    Choices(
                        listOf(
                            "any" to "همه", "publish" to "منتشر شده", "draft" to "پیش‌نویس",
                            "pending" to "در انتظار", "private" to "خصوصی"
                        ),
                        mg.postStatus
                    ) { mg.postStatus = it; mg.loadPosts(true) }
                }
            }
            if (mg.postsError != null) {
                item {
                    AppCard {
                        Text(mg.postsError ?: "", color = cs.error)
                        TextButton(onClick = { mg.loadPosts(true) }) { Text("تلاش دوباره") }
                    }
                }
            }
            if (mg.posts.isEmpty() && !mg.postsLoading && mg.postsError == null) item { EmptyState("مقاله‌ای پیدا نشد") }
            items(mg.posts, key = { it.id }) { p ->
                AppCard(
                    onClick = {
                        if (mg.wpReady) { edit = p } else if (p.link.isNotBlank()) { uri.openUri(p.link) }
                    },
                    pad = 14.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.title.ifBlank { "(بدون عنوان)" }, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        if (p.status != "publish") {
                            Pill(postStatusFa(p.status), color = if (p.status == "draft") WarnColor else cs.outline)
                        }
                    }
                    if (p.excerpt.isNotBlank()) {
                        Text(
                            p.excerpt, fontSize = 12.sp, color = cs.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(p.date, fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
            item {
                if (mg.postsLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                } else if (mg.postsHasMore) {
                    LaunchedEffect(mg.posts.size) { mg.loadPosts(false) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (mg.wpReady) { edit = WPost() } else { w.message = "برای نوشتن مقاله اول وارد حساب وردپرس شو" }
            },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("مقالهٔ جدید") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 16.dp, bottom = 100.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary
        )
    }

    edit?.let { p -> PostEditor(w, p) { edit = null } }
}

@Composable
private fun WpLoginCard(w: WooState) {
    val mg = w.mg
    val cs = MaterialTheme.colorScheme
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }

    AppCard(pad = 14.dp) {
        if (mg.wpReady) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text("حساب وردپرس: ${mg.wpUser}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = { mg.clearWpLogin() }) { Text("خروج", color = cs.error) }
            }
        } else {
            Text(
                "الان فقط مقاله‌های منتشرشده دیده می‌شوند. برای ساخت، ویرایش و حذف مقاله وارد حساب وردپرس شو.",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
            TextButton(onClick = { open = !open }) {
                Text(if (open) "بستن" else "ورود با Application Password")
            }
            if (open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "در وردپرس: کاربران ← پروفایل ← بخش Application Passwords ← یک رمز جدید بساز و همین‌جا بگذار.",
                        fontSize = 12.sp, color = cs.onSurfaceVariant
                    )
                    Field(user, { user = it }, "نام کاربری وردپرس")
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Application Password") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
                    PrimaryButton(if (testing) "در حال بررسی..." else "ورود", enabled = !testing) {
                        testing = true
                        err = null
                        mg.saveWpLogin(user, pass) { e ->
                            testing = false
                            err = e
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostEditor(w: WooState, initial: WPost, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val mg = w.mg
    val isNew = initial.id == 0L
    var title by remember { mutableStateOf(initial.title) }
    var status by remember { mutableStateOf(initial.status) }
    var excerpt by remember { mutableStateOf(initial.excerpt) }
    var content by remember { mutableStateOf(initial.content) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

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
                    if (isNew) "مقالهٔ جدید" else "ویرایش مقاله",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isNew) {
                    if (initial.link.isNotBlank()) {
                        IconButton(onClick = { uri.openUri(initial.link) }) { Icon(Icons.Rounded.OpenInNew, null) }
                    }
                    IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Rounded.DeleteForever, null, tint = cs.error)
                    }
                }
            }
            Field(title, { title = it }, "عنوان مقاله")
            Text("وضعیت انتشار", fontWeight = FontWeight.Medium)
            Choices(
                listOf("publish" to "منتشر", "draft" to "پیش‌نویس", "pending" to "در انتظار", "private" to "خصوصی"),
                status
            ) { status = it }
            Field(excerpt, { excerpt = it }, "خلاصه (اختیاری)", lines = 3)
            Field(content, { content = it }, "متن مقاله (HTML)", lines = 12)
            err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
            PrimaryButton(
                if (isNew) "ذخیرهٔ مقاله" else "ذخیرهٔ تغییرات",
                enabled = title.isNotBlank() && w.busy == null
            ) {
                mg.savePost(
                    initial.copy(title = title.trim(), status = status, excerpt = excerpt, content = content)
                ) { ok -> if (ok) { onDismiss() } else { err = w.message } }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("حذف مقاله؟") },
            text = { Text("«${initial.title}» به زباله منتقل می‌شود یا برای همیشه پاک می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    mg.deletePost(initial.id, true) { ok -> if (ok) onDismiss() }
                }) { Text("حذف همیشگی", color = cs.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirm = false }) { Text("انصراف") }
                    TextButton(onClick = {
                        confirm = false
                        mg.deletePost(initial.id, false) { ok -> if (ok) onDismiss() }
                    }) { Text("انتقال به زباله") }
                }
            }
        )
    }
}

// ───────────────────────── ویژگی‌ها و تنوع‌ها (محصول متغیر) ─────────────────────────

@Composable
fun AttrsEditor(attrs: SnapshotStateList<AttrRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ویژگی‌های محصول متغیر (مثل رنگ یا سایز)", fontWeight = FontWeight.Medium)
        attrs.forEachIndexed { i, a ->
            AppCard(pad = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(a.name, { attrs[i] = a.copy(name = it) }, "نام ویژگی (مثلاً رنگ)", Modifier.weight(1f))
                    IconButton(onClick = { attrs.removeAt(i) }) { Icon(Icons.Rounded.Close, null) }
                }
                Spacer(Modifier.height(8.dp))
                Field(
                    a.optionsText, { attrs[i] = a.copy(optionsText = it) },
                    "گزینه‌ها (با ، جدا کن؛ مثلاً قرمز، آبی)"
                )
            }
        }
        OutlinedButton(onClick = { attrs.add(AttrRow()) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("افزودن ویژگی")
        }
    }
}

@Composable
fun VariationsSection(w: WooState, p: WProduct, attrs: List<WAttr>) {
    val cs = MaterialTheme.colorScheme
    val mg = w.mg
    var edit by remember { mutableStateOf<WVariation?>(null) }
    var genOpen by remember { mutableStateOf(false) }
    var allOpen by remember { mutableStateOf(false) }

    LaunchedEffect(p.id) { mg.loadVariations(p.id) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تنوع‌ها (${fa(mg.variations.size)})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (mg.varLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { genOpen = true }) { Text("ساخت همهٔ ترکیب‌ها") }
            OutlinedButton(onClick = { allOpen = true }, enabled = mg.variations.isNotEmpty()) { Text("قیمت همه") }
        }
        if (mg.variations.isEmpty() && !mg.varLoading) {
            Text(
                "هنوز تنوعی نیست. ویژگی‌ها را بالا وارد کن و «ساخت همهٔ ترکیب‌ها» را بزن.",
                fontSize = 12.sp, color = cs.onSurfaceVariant
            )
        }
        mg.variations.forEach { v ->
            AppCard(onClick = { edit = v }, pad = 12.dp) {
                Text(v.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val price = if (v.sale.isNotBlank()) {
                        "${priceText(v.regular)} ← ${priceText(v.sale)}"
                    } else {
                        priceText(v.regular)
                    }
                    Text("$price ${w.currency}", fontWeight = FontWeight.Bold, color = cs.primary, fontSize = 13.sp)
                    val sc = when (v.stockStatus) {
                        "instock" -> IncomeColor
                        "outofstock" -> ExpenseColor
                        else -> WarnColor
                    }
                    Pill(
                        stockFa(v.stockStatus) + if (v.manageStock && v.qty != null) " · ${fa(v.qty)}" else "",
                        color = sc
                    )
                }
            }
        }
    }

    edit?.let { v -> VariationDialog(w, p.id, v) { edit = null } }

    if (genOpen) {
        var price by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        val va = attrs.filter { it.variation && it.name.isNotBlank() && it.options.isNotEmpty() }
        val n = if (va.isEmpty()) 0 else va.fold(1) { acc, a -> acc * a.options.size }
        AlertDialog(
            onDismissRequest = { genOpen = false },
            title = { Text("ساخت همهٔ ترکیب‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ویژگی‌ها روی محصول ذخیره می‌شوند و ${fa(n)} ترکیب (حداکثر ۱۰۰ تا در هر بار) ساخته می‌شود. ترکیب‌های موجود دوباره ساخته نمی‌شوند.",
                        fontSize = 13.sp
                    )
                    Field(price, { price = it }, "قیمت اصلی همهٔ ترکیب‌ها (اختیاری)", number = true)
                    err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = n > 0 && w.busy == null,
                    onClick = {
                        mg.generateVariations(p.id, attrs, price) { ok ->
                            if (ok) { genOpen = false } else { err = w.message }
                        }
                    }
                ) { Text("بساز") }
            },
            dismissButton = { TextButton(onClick = { genOpen = false }) { Text("انصراف") } }
        )
    }

    if (allOpen) {
        var regular by remember { mutableStateOf("") }
        var sale by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { allOpen = false },
            title = { Text("قیمت همهٔ تنوع‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فیلد خالی بدون تغییر می‌ماند.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Field(regular, { regular = it }, "قیمت اصلی", number = true)
                    Field(sale, { sale = it }, "قیمت حراج", number = true)
                    err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (regular.isNotBlank() || sale.isNotBlank()) && w.busy == null,
                    onClick = {
                        mg.setAllVariationPrices(p.id, regular, sale) { ok ->
                            if (ok) { allOpen = false } else { err = w.message }
                        }
                    }
                ) { Text("اعمال") }
            },
            dismissButton = { TextButton(onClick = { allOpen = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun VariationDialog(w: WooState, pid: Long, v: WVariation, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var sku by remember { mutableStateOf(v.sku) }
    var regular by remember { mutableStateOf(v.regular) }
    var sale by remember { mutableStateOf(v.sale) }
    var status by remember { mutableStateOf(v.status) }
    var manage by remember { mutableStateOf(v.manageStock) }
    var qty by remember { mutableStateOf(v.qty?.toString() ?: "") }
    var stock by remember { mutableStateOf(v.stockStatus) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(v.title, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(regular, { regular = it }, "قیمت اصلی", Modifier.weight(1f).padding(top = 4.dp), number = true)
                    Field(sale, { sale = it }, "قیمت حراج", Modifier.weight(1f).padding(top = 4.dp), number = true)
                }
                Field(sku, { sku = it }, "SKU")
                Choices(listOf("publish" to "فعال", "private" to "غیرفعال"), status) { status = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مدیریت تعداد موجودی", Modifier.weight(1f))
                    Switch(checked = manage, onCheckedChange = { manage = it })
                }
                if (manage) {
                    Field(qty, { qty = it }, "تعداد موجودی", number = true)
                } else {
                    Choices(
                        listOf("instock" to "موجود", "outofstock" to "ناموجود", "onbackorder" to "پیش‌سفارش"),
                        stock
                    ) { stock = it }
                }
                err?.let { Text(it, color = cs.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = w.busy == null,
                onClick = {
                    val r = en(regular).trim()
                    val s = en(sale).trim()
                    val rd = r.toDoubleOrNull()
                    val sd = s.toDoubleOrNull()
                    if (rd != null && sd != null && sd >= rd) {
                        err = "قیمت حراج باید کمتر از قیمت اصلی باشد"
                    } else {
                        val nv = v.copy(
                            sku = sku.trim(), regular = r, sale = s, status = status,
                            manageStock = manage, qty = if (manage) (en(qty).toIntOrNull() ?: 0) else null,
                            stockStatus = stock
                        )
                        w.mg.saveVariation(pid, nv) { ok -> if (ok) { onDismiss() } else { err = w.message } }
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirm = true }) { Text("حذف", color = cs.error) }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        }
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("حذف این تنوع؟") },
            text = { Text("«${v.title}» برای همیشه حذف می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    w.mg.deleteVariation(pid, v.id) { ok -> if (ok) onDismiss() }
                }) { Text("حذف", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("انصراف") } }
        )
    }
}
