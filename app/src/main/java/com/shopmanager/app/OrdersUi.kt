package com.shopmanager.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun orderColor(s: String): Color = when (s) {
    "completed" -> IncomeColor
    "processing" -> Color(0xFF60A5FA)
    "on-hold", "pending" -> Color(0xFFFBBF24)
    "cancelled", "failed" -> ExpenseColor
    else -> Color(0xFF9CA3AF)
}

private val orderStatuses = listOf(
    "pending", "processing", "on-hold", "completed", "cancelled", "refunded", "failed"
)

@Composable
fun OrdersTab(woo: WooState) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<WOrder?>(null) }

    LaunchedEffect(Unit) { if (woo.orders.isEmpty()) woo.loadOrders(true) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(woo.orderQuery) {
        if (first) { first = false; return@LaunchedEffect }
        delay(600)
        woo.loadOrders(true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("سفارش‌ها") { IconBox(Icons.Rounded.Refresh) { woo.loadOrders(true) } }
        SearchField(woo.orderQuery, { woo.orderQuery = it }, "جستجوی شماره سفارش، نام یا تلفن...") { woo.loadOrders(true) }
        Choices(
            listOf(
                "any" to "همه", "pending" to "در انتظار پرداخت", "processing" to "در حال انجام", "on-hold" to "در انتظار بررسی",
                "completed" to "تکمیل", "cancelled" to "لغو", "refunded" to "مسترد", "failed" to "ناموفق"
            ),
            woo.orderStatusF
        ) { woo.orderStatusF = it; woo.loadOrders(true) }
        Choices(
            listOf(0 to "همه زمان‌ها", 1 to "امروز", 2 to "۷ روز", 3 to "۳۰ روز"),
            woo.orderRange
        ) { woo.orderRange = it; woo.loadOrders(true) }
        if (woo.orderCustomer != 0L) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(cs.primary.copy(alpha = .16f))
                    .clickable { woo.clearOrderCustomer() }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("مشتری: ${woo.orderCustomerName}", color = cs.primary, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, null, tint = cs.primary, modifier = Modifier.size(16.dp))
            }
        }

        Box(Modifier.weight(1f)) {
            val err = woo.ordersError
            when {
                woo.orders.isEmpty() && woo.ordersLoading -> SkeletonList()
                woo.orders.isEmpty() && err != null -> Text(err, color = cs.error, modifier = Modifier.padding(20.dp))
                woo.orders.isEmpty() -> EmptyState("سفارشی پیدا نشد")
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(woo.orders, key = { it.id }) { o -> OrderRow(o, woo.currency) { open = o } }
                    if (woo.ordersHasMore) {
                        item {
                            LaunchedEffect(woo.orders.size) { woo.loadOrders(false) }
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { o ->
        val live = woo.orders.firstOrNull { it.id == o.id } ?: o
        OrderSheet(woo, live) { open = null }
    }
}

@Composable
private fun OrderRow(o: WOrder, currency: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("سفارش ${fa(o.number)}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill(orderFa(o.status), color = orderColor(o.status))
        }
        Spacer(Modifier.height(4.dp))
        Text(o.customer, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.date, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${priceText(o.total)} $currency", fontWeight = FontWeight.Bold, color = cs.primary)
        }
        if (o.lines.isNotEmpty()) {
            Text(
                o.lines.joinToString("، ") { "${it.name} ×${fa(it.qty)}" },
                fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 2
            )
        }
    }
}

@Composable
fun OrderSheet(woo: WooState, o: WOrder, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var pendingStatus by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<List<WNote>>(emptyList()) }
    var noteText by remember { mutableStateOf("") }
    var noteCustomer by remember { mutableStateOf(false) }
    var confirmNote by remember { mutableStateOf(false) }

    fun reloadNotes() = woo.loadNotes(o.id) { notes = it }
    LaunchedEffect(o.id) { reloadNotes() }

    AppSheet(onClose, "سفارش ${fa(o.number)}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(orderFa(o.status), color = orderColor(o.status))
            Spacer(Modifier.width(8.dp))
            Text(o.date, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        AppCard(pad = 12.dp) {
            Text(o.customer, fontWeight = FontWeight.Bold)
            if (o.phone.isNotBlank()) Text("📞 ${fa(o.phone)}", fontSize = 13.sp)
            if (o.email.isNotBlank()) Text(o.email, fontSize = 13.sp, color = cs.onSurfaceVariant)
            if (o.shippingAddr.isNotBlank()) Text("آدرس: ${o.shippingAddr}", fontSize = 13.sp, color = cs.onSurfaceVariant)
            if (o.payment.isNotBlank()) Text("پرداخت: ${o.payment}", fontSize = 13.sp, color = cs.onSurfaceVariant)
            if (o.customerId != 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "همهٔ سفارش‌های این مشتری", color = cs.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { woo.openCustomerOrders(o.customerId, o.customer); onClose() }
                )
            }
        }
        AppCard(pad = 12.dp) {
            o.lines.forEach { l ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.name} ×${fa(l.qty)}", Modifier.weight(1f), fontSize = 13.sp)
                    Text(money(l.total), fontSize = 13.sp)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
            if (o.discountTotal > 0) TotalLine("تخفیف", "− ${money(o.discountTotal)}")
            if (o.shippingTotal > 0) TotalLine("ارسال", money(o.shippingTotal))
            if (o.taxTotal > 0) TotalLine("مالیات", money(o.taxTotal))
            Row {
                Text("جمع کل", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
                Text("${priceText(o.total)} ${woo.currency}", fontWeight = FontWeight.ExtraBold, color = cs.primary)
            }
        }
        if (o.note.isNotBlank()) {
            AppCard(pad = 12.dp) {
                Text("توضیح مشتری", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(o.note)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                "فاکتور", {
                    printInvoice(ctx, invoiceHtml(o, woo.displayShopName, woo.shopPhone, woo.shopAddr, woo.currency, false), "invoice-${o.number}")
                }, Modifier.weight(1f), icon = Icons.Rounded.ReceiptLong
            )
            ActionButton(
                "برگهٔ ارسال", {
                    printInvoice(ctx, invoiceHtml(o, woo.displayShopName, woo.shopPhone, woo.shopAddr, woo.currency, true), "packing-${o.number}")
                }, Modifier.weight(1f), icon = Icons.Rounded.LocalShipping, kind = BtnKind.TONAL
            )
        }

        Text("تغییر وضعیت", fontWeight = FontWeight.SemiBold)
        Choices(orderStatuses.map { it to orderFa(it) }, o.status) { if (it != o.status) pendingStatus = it }

        Text("یادداشت‌ها", fontWeight = FontWeight.SemiBold)
        notes.forEach { n ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceVariant.copy(alpha = .45f))
                    .border(1.dp, if (n.forCustomer) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(n.text, fontSize = 13.sp)
                Text(n.date + if (n.forCustomer) " · برای مشتری" else "", fontSize = 11.sp, color = cs.onSurfaceVariant)
            }
        }
        Field(noteText, { noteText = it }, "یادداشت جدید", singleLine = false, minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ارسال برای مشتری", Modifier.weight(1f), fontSize = 13.sp)
            Switch(noteCustomer, { noteCustomer = it })
        }
        ActionButton("ثبت یادداشت", { confirmNote = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.NoteAdd, enabled = noteText.isNotBlank())
    }

    pendingStatus?.let { st ->
        ConfirmDialog(
            title = "تغییر وضعیت سفارش ${fa(o.number)}",
            text = "از «${orderFa(o.status)}» به «${orderFa(st)}»" +
                    (if (st == "cancelled" || st == "refunded") "\nموجودی/پرداخت ممکن است در سایت تغییر کند." else ""),
            danger = st == "cancelled" || st == "refunded" || st == "failed",
            onConfirm = {
                pendingStatus = null
                woo.setOrderStatus(o, st) { }
            },
            onDismiss = { pendingStatus = null }
        )
    }
    if (confirmNote) {
        ConfirmDialog(
            title = "ثبت یادداشت؟",
            text = if (noteCustomer) "این یادداشت برای مشتری ایمیل می‌شود." else "یادداشت خصوصی ثبت می‌شود.",
            confirmText = "ثبت",
            onConfirm = {
                confirmNote = false
                val t = noteText
                woo.addNote(o.id, t, noteCustomer) { ok -> if (ok) { noteText = ""; reloadNotes() } }
            },
            onDismiss = { confirmNote = false }
        )
    }
}

@Composable
private fun TotalLine(label: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontSize = 13.sp)
    }
}
