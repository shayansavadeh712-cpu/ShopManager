package com.shopmanager.app

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.time.LocalDate

private fun esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

/** فاکتور (یا برگهٔ ارسال بدون قیمت) به‌صورت HTML راست‌چین A4 */
fun invoiceHtml(
    o: WOrder,
    shopName: String,
    shopPhone: String,
    shopAddr: String,
    currency: String,
    packing: Boolean
): String {
    val title = if (packing) "برگهٔ ارسال" else "فاکتور فروش"
    val rows = StringBuilder()
    o.lines.forEachIndexed { i, l ->
        rows.append("<tr><td>${fa(i + 1)}</td><td class=\"nm\">${esc(l.name)}${if (l.sku.isNotBlank()) "<div class=\"sk\">SKU: ${esc(l.sku)}</div>" else ""}</td>")
        rows.append("<td>${fa(l.qty)}</td>")
        if (!packing) {
            rows.append("<td>${money(l.unit)}</td><td>${money(l.total)}</td>")
        }
        rows.append("</tr>")
    }
    val subtotal = o.lines.sumOf { it.subtotal }
    val totals = StringBuilder()
    if (!packing) {
        totals.append("<div class=\"tr\"><span>جمع اقلام</span><b>${money(subtotal)} $currency</b></div>")
        if (o.discountTotal > 0) totals.append("<div class=\"tr\"><span>تخفیف${if (o.coupons.isNotEmpty()) " (" + esc(o.coupons.joinToString("، ")) + ")" else ""}</span><b>− ${money(o.discountTotal)} $currency</b></div>")
        if (o.shippingTotal > 0) totals.append("<div class=\"tr\"><span>هزینهٔ ارسال</span><b>${money(o.shippingTotal)} $currency</b></div>")
        if (o.taxTotal > 0) totals.append("<div class=\"tr\"><span>مالیات</span><b>${money(o.taxTotal)} $currency</b></div>")
        totals.append("<div class=\"tr grand\"><span>مبلغ قابل پرداخت</span><b>${priceText(o.total)} $currency</b></div>")
    }
    val head = if (packing) "<th>#</th><th>محصول</th><th>تعداد</th>"
    else "<th>#</th><th>شرح کالا</th><th>تعداد</th><th>قیمت واحد</th><th>مبلغ</th>"
    return """
<!doctype html><html dir="rtl" lang="fa"><head><meta charset="utf-8">
<style>
@font-face{font-family:V;src:url('fonts/Vazirmatn-Regular.ttf');font-weight:400}
@font-face{font-family:V;src:url('fonts/Vazirmatn-Bold.ttf');font-weight:700}
*{box-sizing:border-box}
body{font-family:V,Tahoma,sans-serif;margin:0;padding:24px;color:#111;font-size:13px}
.top{display:flex;justify-content:space-between;align-items:flex-start;border-bottom:3px solid #f97316;padding-bottom:12px}
h1{margin:0;font-size:24px}.meta{text-align:left;line-height:1.9}
.boxes{display:flex;gap:12px;margin:16px 0}
.box{flex:1;border:1px solid #ddd;border-radius:10px;padding:10px 12px;line-height:1.9}
.box h3{margin:0 0 4px;font-size:13px;color:#f97316}
table{width:100%;border-collapse:collapse;margin-top:6px}
th{background:#111;color:#fff;padding:8px;font-size:12px}
td{padding:8px;border-bottom:1px solid #e5e5e5;text-align:center}
td.nm{text-align:right}.sk{color:#888;font-size:10px}
.totals{width:55%;margin:14px 0 0 auto}
.tr{display:flex;justify-content:space-between;padding:6px 10px;border-bottom:1px solid #eee}
.grand{background:#fff3e8;border:1px solid #f97316;border-radius:8px;font-size:15px;margin-top:6px}
.note{margin-top:14px;border:1px dashed #bbb;border-radius:8px;padding:8px 12px}
.sign{display:flex;gap:40px;margin-top:60px}.sign div{flex:1;border-top:1px solid #333;text-align:center;padding-top:6px;color:#555}
</style></head><body>
<div class="top"><div><h1>${esc(shopName.ifBlank { "فروشگاه" })}</h1>
<div>${esc(shopPhone)}</div><div>${esc(shopAddr)}</div></div>
<div class="meta"><b style="font-size:18px">$title</b><br>شمارهٔ سفارش: ${fa(o.number)}<br>تاریخ: ${o.date}<br>چاپ: ${fmtDay(LocalDate.now().toEpochDay())}</div></div>
<div class="boxes">
<div class="box"><h3>مشخصات خریدار</h3><b>${esc(o.customer)}</b><br>${if (o.phone.isNotBlank()) "تلفن: " + fa(esc(o.phone)) + "<br>" else ""}${if (o.email.isNotBlank()) esc(o.email) else ""}</div>
<div class="box"><h3>آدرس ارسال</h3>${esc(o.shippingAddr.ifBlank { "—" })}${if (!packing && o.payment.isNotBlank()) "<br>پرداخت: " + esc(o.payment) else ""}</div>
</div>
<table><thead><tr>$head</tr></thead><tbody>$rows</tbody></table>
<div class="totals">$totals</div>
${if (o.note.isNotBlank()) "<div class=\"note\"><b>توضیح مشتری:</b> ${esc(o.note)}</div>" else ""}
<div class="sign"><div>مهر و امضای فروشنده</div><div>امضای خریدار</div></div>
</body></html>
""".trimIndent()
}

private var keep: WebView? = null

fun printInvoice(ctx: Context, html: String, jobName: String) {
    val wv = WebView(ctx)
    keep = wv
    wv.settings.allowFileAccess = true
    wv.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter(jobName)
            pm.print(jobName, adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
}
