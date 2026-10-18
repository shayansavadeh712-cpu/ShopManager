package com.shopmanager.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import android.icu.util.Calendar
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ───────────────────────── رمزنگاری کلیدها (Android Keystore) ─────────────────────────

object Vault {
    private const val ALIAS = "sb_vault_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun enc(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    fun dec(s: String): String {
        if (s.isEmpty()) return ""
        return try {
            val b = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

// ───────────────────────── مدل‌ها ─────────────────────────

data class WCat(
    val id: Long,
    val name: String,
    val count: Int = 0,
    val parent: Long = 0,
    val image: String = "",
    val depth: Int = 0
)

fun orderCats(list: List<WCat>): List<WCat> {
    val ids = list.map { it.id }.toSet()
    val byParent = list.groupBy { if (ids.contains(it.parent)) it.parent else 0L }
    val out = ArrayList<WCat>()
    fun walk(parent: Long, depth: Int) {
        if (depth > 6) return
        val kids = (byParent[parent] ?: emptyList()).sortedBy { it.name }
        for (k in kids) {
            out.add(k.copy(depth = depth))
            walk(k.id, depth + 1)
        }
    }
    walk(0L, 0)
    return out
}

data class WProduct(
    val id: Long = 0,
    val name: String = "",
    val type: String = "simple",
    val status: String = "publish",
    val sku: String = "",
    val regularPrice: String = "",
    val salePrice: String = "",
    val price: String = "",
    val onSale: Boolean = false,
    val stockStatus: String = "instock",
    val manageStock: Boolean = false,
    val stockQty: Int? = null,
    val backorders: String = "no",
    val catIds: List<Long> = emptyList(),
    val catNames: List<String> = emptyList(),
    val image: String = "",
    val description: String = "",
    val shortDescription: String = "",
    val featured: Boolean = false,
    val weight: String = "",
    val permalink: String = "",
    val totalSales: Int = 0,
    val varCount: Int = 0,
    val date: String = "",
    val dateGmt: String = "",
    val schedDate: String = "",
    val schedGmt: String = "",
    val brandIds: List<Long> = emptyList(),
    val brandNames: List<String> = emptyList(),
    val gallery: List<String> = emptyList(),
    val imgIds: Map<String, Long> = emptyMap(),
    val slug: String = "",
    val saleFrom: String = "",
    val saleTo: String = "",
    val taxStatus: String = "taxable",
    val taxClass: String = "",
    val lowStock: Int? = null,
    val soldIndividually: Boolean = false,
    val length: String = "",
    val width: String = "",
    val height: String = "",
    val shippingClass: String = "",
    val tagNames: List<String> = emptyList(),
    val purchaseNote: String = "",
    val menuOrder: Int = 0,
    val reviewsAllowed: Boolean = true,
    val catalogVisibility: String = "visible",
    val virtual: Boolean = false,
    val downloadable: Boolean = false,
    val externalUrl: String = "",
    val buttonText: String = "",
    val upsellIds: List<Long> = emptyList(),
    val crossSellIds: List<Long> = emptyList(),
    val groupedIds: List<Long> = emptyList(),
    val avgRating: String = "",
    val ratingCount: Int = 0
) {
    val priceEditable: Boolean get() = type == "simple" || type == "external"
}

data class WAttrDef(val id: Long, val name: String, val slug: String)

data class WVariation(
    val id: Long,
    val parentId: Long,
    val attrs: String,
    val sku: String,
    val regularPrice: String,
    val salePrice: String,
    val status: String,
    val manageStock: Boolean,
    val stockQty: Int?,
    val stockStatus: String,
    val image: String,
    val newImage: String? = null,
    val description: String = "",
    val weight: String = "",
    val length: String = "",
    val width: String = "",
    val height: String = "",
    val backorders: String = "no",
    val taxStatus: String = "taxable",
    val saleFrom: String = "",
    val saleTo: String = "",
    val virtual: Boolean = false,
    val downloadable: Boolean = false,
    val lowStock: Int? = null
)

data class WLine(
    val name: String,
    val sku: String,
    val qty: Int,
    val unit: Double,
    val subtotal: Double,
    val total: Double
)

data class WOrder(
    val id: Long,
    val number: String,
    val status: String,
    val total: String,
    val date: String,
    val customer: String,
    val customerId: Long,
    val phone: String,
    val email: String,
    val billingAddr: String,
    val shippingAddr: String,
    val lines: List<WLine>,
    val shippingTotal: Double,
    val discountTotal: Double,
    val taxTotal: Double,
    val payment: String,
    val note: String,
    val coupons: List<String>
)

data class WNote(val id: Long, val text: String, val forCustomer: Boolean, val date: String)

data class WCustomer(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
    val orders: Int,
    val spent: Double,
    val date: String
)

data class ShopStats(
    val products: Int,
    val published: Int,
    val drafts: Int,
    val outOfStock: Int,
    val salesMonth: Double,
    val ordersMonth: Int,
    val orderTotals: Map<String, Int>,
    val series: List<Float>,
    val seriesLabels: List<String>,
    val salesToday: Double,
    val ordersToday: Int,
    val top: List<Pair<String, Int>>
)

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.dbl(k: String): Double = str(k).toDoubleOrNull() ?: 0.0

fun clean(s: String): String =
    android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

fun parseProduct(o: JSONObject): WProduct {
    val cats = o.optJSONArray("categories") ?: JSONArray()
    val ids = ArrayList<Long>()
    val names = ArrayList<String>()
    for (i in 0 until cats.length()) {
        val c = cats.getJSONObject(i)
        ids.add(c.optLong("id"))
        names.add(clean(c.str("name")))
    }
    val imgs = o.optJSONArray("images")
    val srcs = ArrayList<String>()
    val idMap = HashMap<String, Long>()
    if (imgs != null) {
        for (i in 0 until imgs.length()) {
            val im = imgs.getJSONObject(i)
            val sx = im.str("src")
            if (sx.isNotBlank()) {
                srcs.add(sx)
                idMap[sx] = im.optLong("id")
            }
        }
    }
    val img = srcs.firstOrNull() ?: ""
    val gal: List<String> = if (srcs.size > 1) srcs.drop(1) else emptyList()
    val vars = o.optJSONArray("variations")
    val bs = o.optJSONArray("brands") ?: JSONArray()
    val bIds = ArrayList<Long>()
    val bNames = ArrayList<String>()
    for (i in 0 until bs.length()) {
        val b = bs.getJSONObject(i)
        bIds.add(b.optLong("id"))
        bNames.add(clean(b.str("name")))
    }
    return WProduct(
        id = o.optLong("id"),
        name = clean(o.str("name")),
        type = o.str("type").ifBlank { "simple" },
        status = o.str("status").ifBlank { "publish" },
        sku = o.str("sku"),
        regularPrice = o.str("regular_price"),
        salePrice = o.str("sale_price"),
        price = o.str("price"),
        onSale = o.optBoolean("on_sale", false),
        stockStatus = o.str("stock_status").ifBlank { "instock" },
        manageStock = o.optBoolean("manage_stock", false),
        stockQty = if (o.isNull("stock_quantity")) null else o.optInt("stock_quantity"),
        backorders = o.str("backorders").ifBlank { "no" },
        catIds = ids,
        catNames = names,
        image = img,
        description = o.str("description"),
        shortDescription = o.str("short_description"),
        featured = o.optBoolean("featured", false),
        weight = o.str("weight"),
        permalink = o.str("permalink"),
        totalSales = o.optInt("total_sales", 0),
        varCount = vars?.length() ?: 0,
        date = o.str("date_created"),
        dateGmt = o.str("date_created_gmt"),
        brandIds = bIds,
        brandNames = bNames,
        gallery = gal,
        imgIds = idMap,
        slug = o.str("slug"),
        saleFrom = dayOnly(o.str("date_on_sale_from")),
        saleTo = dayOnly(o.str("date_on_sale_to")),
        taxStatus = o.str("tax_status").ifBlank { "taxable" },
        taxClass = o.str("tax_class"),
        lowStock = if (o.isNull("low_stock_amount") || !o.has("low_stock_amount")) null else o.optInt("low_stock_amount"),
        soldIndividually = o.optBoolean("sold_individually", false),
        length = o.optJSONObject("dimensions")?.str("length") ?: "",
        width = o.optJSONObject("dimensions")?.str("width") ?: "",
        height = o.optJSONObject("dimensions")?.str("height") ?: "",
        shippingClass = o.str("shipping_class"),
        tagNames = namesOf(o.optJSONArray("tags")),
        purchaseNote = o.str("purchase_note"),
        menuOrder = o.optInt("menu_order", 0),
        reviewsAllowed = o.optBoolean("reviews_allowed", true),
        catalogVisibility = o.str("catalog_visibility").ifBlank { "visible" },
        virtual = o.optBoolean("virtual", false),
        downloadable = o.optBoolean("downloadable", false),
        externalUrl = o.str("external_url"),
        buttonText = o.str("button_text"),
        upsellIds = idsOf(o.optJSONArray("upsell_ids")),
        crossSellIds = idsOf(o.optJSONArray("cross_sell_ids")),
        groupedIds = idsOf(o.optJSONArray("grouped_products")),
        avgRating = o.str("average_rating"),
        ratingCount = o.optInt("rating_count", 0)
    )
}

fun dayOnly(s: String): String = if (s.length >= 10) s.substring(0, 10) else ""

fun namesOf(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val l = ArrayList<String>()
    for (i in 0 until a.length()) l.add(clean(a.getJSONObject(i).str("name")))
    return l
}

fun idsOf(a: JSONArray?): List<Long> {
    if (a == null) return emptyList()
    val l = ArrayList<Long>()
    for (i in 0 until a.length()) l.add(a.optLong(i))
    return l
}

fun parseIds(t: String): List<Long> =
    t.replace("،", ",").split(",", " ", "\n").mapNotNull { it.trim().toLongOrNull() }.distinct()

fun WProduct.toJson(isNew: Boolean, imageChanged: Boolean, tagIds: List<Long>? = null): JSONObject {
    val o = JSONObject()
    o.put("name", name)
    // زمان‌بندی: وردپرس خودش محصول را با وضعیت publish و تاریخ آینده زمان‌بندی می‌کند
    o.put("status", if (status == "future") "publish" else status)
    if (schedDate.isNotBlank()) o.put("date_created", schedDate)
    if (schedGmt.isNotBlank()) o.put("date_created_gmt", schedGmt)
    o.put("sku", sku)
    if (priceEditable) {
        o.put("regular_price", regularPrice)
        o.put("sale_price", salePrice)
    }
    o.put("manage_stock", manageStock)
    if (manageStock) o.put("stock_quantity", stockQty ?: 0)
    o.put("stock_status", stockStatus)
    o.put("backorders", backorders)
    o.put("featured", featured)
    o.put("description", description)
    o.put("short_description", shortDescription)
    o.put("weight", weight)
    o.put("dimensions", JSONObject().put("length", length).put("width", width).put("height", height))
    if (slug.isNotBlank()) o.put("slug", slug)
    o.put("tax_status", taxStatus)
    o.put("tax_class", taxClass)
    o.put("sold_individually", soldIndividually)
    o.put("shipping_class", shippingClass)
    o.put("purchase_note", purchaseNote)
    o.put("menu_order", menuOrder)
    o.put("reviews_allowed", reviewsAllowed)
    o.put("catalog_visibility", catalogVisibility)
    o.put("virtual", virtual)
    o.put("downloadable", downloadable)
    o.put("low_stock_amount", if (manageStock && lowStock != null) lowStock else JSONObject.NULL)
    if (priceEditable) {
        o.put("date_on_sale_from", if (saleFrom.isBlank()) JSONObject.NULL else "${saleFrom}T00:00:00")
        o.put("date_on_sale_to", if (saleTo.isBlank()) JSONObject.NULL else "${saleTo}T23:59:59")
    }
    if (type == "external") {
        o.put("external_url", externalUrl)
        o.put("button_text", buttonText)
    }
    if (type == "grouped") o.put("grouped_products", JSONArray(groupedIds))
    o.put("upsell_ids", JSONArray(upsellIds))
    o.put("cross_sell_ids", JSONArray(crossSellIds))
    val arr = JSONArray()
    catIds.forEach { arr.put(JSONObject().put("id", it)) }
    o.put("categories", arr)
    val barr = JSONArray()
    brandIds.forEach { barr.put(JSONObject().put("id", it)) }
    o.put("brands", barr)
    if (tagIds != null) {
        val ta = JSONArray()
        tagIds.forEach { ta.put(JSONObject().put("id", it)) }
        o.put("tags", ta)
    }
    if (isNew) o.put("type", type)
    if (imageChanged) {
        val imgs = JSONArray()
        (listOf(image) + gallery).filter { it.isNotBlank() }.distinct().forEach { u ->
            val iid = imgIds[u] ?: 0L
            imgs.put(if (iid > 0L) JSONObject().put("id", iid) else JSONObject().put("src", u))
        }
        o.put("images", imgs)
    }
    return o
}

fun parseVariation(o: JSONObject, pid: Long): WVariation {
    val at = o.optJSONArray("attributes") ?: JSONArray()
    val parts = ArrayList<String>()
    for (i in 0 until at.length()) {
        val a = at.getJSONObject(i)
        val opt = a.str("option")
        parts.add(if (opt.isBlank()) "هر ${a.str("name")}" else "${a.str("name")}: $opt")
    }
    val img = o.optJSONObject("image")?.str("src") ?: ""
    return WVariation(
        id = o.optLong("id"),
        parentId = pid,
        attrs = parts.joinToString(" · ").ifBlank { "تنوع ${o.optLong("id")}" },
        sku = o.str("sku"),
        regularPrice = o.str("regular_price"),
        salePrice = o.str("sale_price"),
        status = o.str("status").ifBlank { "publish" },
        manageStock = o.optBoolean("manage_stock", false),
        stockQty = if (o.isNull("stock_quantity")) null else o.optInt("stock_quantity"),
        stockStatus = o.str("stock_status").ifBlank { "instock" },
        image = img,
        description = o.str("description"),
        weight = o.str("weight"),
        length = o.optJSONObject("dimensions")?.str("length") ?: "",
        width = o.optJSONObject("dimensions")?.str("width") ?: "",
        height = o.optJSONObject("dimensions")?.str("height") ?: "",
        backorders = o.str("backorders").ifBlank { "no" },
        taxStatus = o.str("tax_status").ifBlank { "taxable" },
        saleFrom = dayOnly(o.str("date_on_sale_from")),
        saleTo = dayOnly(o.str("date_on_sale_to")),
        virtual = o.optBoolean("virtual", false),
        downloadable = o.optBoolean("downloadable", false),
        lowStock = if (o.isNull("low_stock_amount") || !o.has("low_stock_amount")) null else o.optInt("low_stock_amount")
    )
}

fun WVariation.toJson(): JSONObject {
    val o = JSONObject()
    o.put("regular_price", regularPrice)
    o.put("sale_price", salePrice)
    o.put("sku", sku)
    o.put("status", status)
    o.put("manage_stock", manageStock)
    if (manageStock) o.put("stock_quantity", stockQty ?: 0)
    o.put("stock_status", stockStatus)
    o.put("backorders", backorders)
    o.put("description", description)
    o.put("weight", weight)
    o.put("dimensions", JSONObject().put("length", length).put("width", width).put("height", height))
    o.put("tax_status", taxStatus)
    o.put("virtual", virtual)
    o.put("downloadable", downloadable)
    o.put("date_on_sale_from", if (saleFrom.isBlank()) JSONObject.NULL else "${saleFrom}T00:00:00")
    o.put("date_on_sale_to", if (saleTo.isBlank()) JSONObject.NULL else "${saleTo}T23:59:59")
    o.put("low_stock_amount", if (manageStock && lowStock != null) lowStock else JSONObject.NULL)
    newImage?.let { u ->
        val id = ImageIds.map[u]
        o.put("image", if (id != null && id > 0) JSONObject().put("id", id) else JSONObject().put("src", u))
    }
    return o
}

object ImageIds {
    val map = HashMap<String, Long>()
}

fun addrOf(b: JSONObject?): String {
    if (b == null) return ""
    return listOf(b.str("address_1"), b.str("address_2"), b.str("city"), b.str("state"), b.str("postcode"))
        .filter { it.isNotBlank() }.joinToString("، ")
}

fun parseOrder(o: JSONObject): WOrder {
    val b = o.optJSONObject("billing")
    val sh = o.optJSONObject("shipping")
    val name = if (b != null) "${b.str("first_name")} ${b.str("last_name")}".trim() else ""
    val li = o.optJSONArray("line_items") ?: JSONArray()
    val lines = ArrayList<WLine>()
    for (i in 0 until li.length()) {
        val x = li.getJSONObject(i)
        val q = x.optInt("quantity", 1)
        val st = x.dbl("subtotal")
        lines.add(
            WLine(
                clean(x.str("name")), x.str("sku"), q,
                if (q > 0) st / q else 0.0, st, x.dbl("total")
            )
        )
    }
    val cl = o.optJSONArray("coupon_lines") ?: JSONArray()
    val coupons = ArrayList<String>()
    for (i in 0 until cl.length()) coupons.add(cl.getJSONObject(i).str("code"))
    val shipAddr = addrOf(sh).ifBlank { addrOf(b) }
    return WOrder(
        id = o.optLong("id"),
        number = o.str("number").ifBlank { o.optLong("id").toString() },
        status = o.str("status"),
        total = o.str("total"),
        date = orderDate(o.str("date_created")),
        customer = name.ifBlank { "مهمان" },
        customerId = o.optLong("customer_id"),
        phone = b?.str("phone") ?: "",
        email = b?.str("email") ?: "",
        billingAddr = addrOf(b),
        shippingAddr = shipAddr,
        lines = lines,
        shippingTotal = o.dbl("shipping_total"),
        discountTotal = o.dbl("discount_total"),
        taxTotal = o.dbl("total_tax"),
        payment = o.str("payment_method_title"),
        note = o.str("customer_note"),
        coupons = coupons
    )
}

fun orderDate(s: String): String = try {
    val d = LocalDate.parse(s.take(10))
    val t = if (s.length >= 16) " " + fa(s.substring(11, 16)) else ""
    fmtDay(d.toEpochDay()) + t
} catch (e: Exception) {
    s
}

/** زمان UTC وردپرس را به ساعت محلی گوشی تبدیل می‌کند. */
fun gmtLocal(gmt: String): java.time.LocalDateTime? = try {
    java.time.LocalDateTime.parse(gmt.take(19)).atZone(java.time.ZoneOffset.UTC)
        .withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
} catch (e: Exception) {
    null
}

/** زمان انتخاب‌شده روی گوشی ← (ساعت محلی سایت، UTC) برای ارسال به وردپرس. */
fun deviceToSite(local: String, siteOffsetSec: Int?): Pair<String, String> = try {
    val inst = java.time.LocalDateTime.parse(local).atZone(java.time.ZoneId.systemDefault()).toInstant()
    val gmt = java.time.LocalDateTime.ofInstant(inst, java.time.ZoneOffset.UTC)
    val site = if (siteOffsetSec != null) gmt.plusSeconds(siteOffsetSec.toLong())
    else java.time.LocalDateTime.ofInstant(inst, java.time.ZoneId.systemDefault())
    val f = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
    Pair(site.withNano(0).format(f), gmt.withNano(0).format(f))
} catch (e: Exception) {
    Pair("", "")
}

fun priceText(s: String): String = s.toDoubleOrNull()?.let { money(it) } ?: "—"

fun fmtPrice(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

fun roundStep(v: Double, step: Int): Double =
    if (step <= 0) Math.round(v * 100.0) / 100.0
    else if (step == 1) Math.round(v).toDouble()
    else Math.round(v / step).toDouble() * step

// ───────────────────────── کلاینت REST ─────────────────────────

class WooException(msg: String) : Exception(msg)

class ApiResult(val code: Int, val body: String, val total: Int, val pages: Int) {
    val ok: Boolean get() = code in 200..299
}

fun ApiResult.errorText(): String {
    val msg = try {
        JSONObject(body).optString("message", "")
    } catch (e: Exception) {
        ""
    }
    return when {
        code == 401 || code == 403 ->
            "دسترسی رد شد؛ کلید و سطح دسترسی (Read/Write) را بررسی کن." + (if (msg.isNotBlank()) "\n$msg" else "")
        code == 404 && msg.isBlank() -> "ووکامرس پیدا نشد؛ آدرس سایت را بررسی کن"
        msg.isNotBlank() -> msg
        else -> "خطای سرور ($code)"
    }
}

class WooClient(site: String, private val key: String, private val secret: String) {
    private val base = normalize(site) + "/wp-json/wc/v3"
    private var queryAuth = false

    companion object {
        fun normalize(s: String): String {
            var u = s.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            return u
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun call(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        var r = once(method, path, query, body, queryAuth)
        if (r.code == 401 && !queryAuth) {
            val r2 = once(method, path, query, body, true)
            if (r2.code != 401) {
                queryAuth = true
                r = r2
            }
        }
        r
    }

    private fun once(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String?,
        qAuth: Boolean
    ): ApiResult {
        val q = LinkedHashMap<String, String>(query)
        if (qAuth) {
            q["consumer_key"] = key
            q["consumer_secret"] = secret
        }
        val qs = if (q.isEmpty()) "" else "?" + q.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val conn = URL(base + path + qs).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 40000
            conn.setRequestProperty("Accept", "application/json")
            if (!qAuth) {
                val cred = Base64.encodeToString("$key:$secret".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $cred")
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val total = conn.getHeaderField("X-WP-Total")?.toIntOrNull() ?: 0
            val pages = conn.getHeaderField("X-WP-TotalPages")?.toIntOrNull() ?: 0
            return ApiResult(code, text, total, pages)
        } finally {
            conn.disconnect()
        }
    }
}

fun friendly(e: Throwable): String = when (e) {
    is WooException -> e.message ?: "خطا"
    is java.net.UnknownHostException -> "آدرس سایت پیدا نشد یا اینترنت قطع است"
    is java.net.SocketTimeoutException -> "سرور دیر پاسخ داد؛ دوباره تلاش کن"
    is javax.net.ssl.SSLException -> "مشکل در گواهی SSL سایت"
    is org.json.JSONException -> "پاسخ سرور قابل خواندن نیست (آدرس یا افزونهٔ امنیتی را بررسی کن)"
    else -> e.message ?: "خطای ناشناخته"
}

// ───────────────────────── ویرایش گروهی ─────────────────────────

enum class Mode { SET, PCT_UP, PCT_DOWN, AMT_UP, AMT_DOWN }
enum class CatMode { ADD, REPLACE, REMOVE }

sealed interface BulkOp
data class BPrice(val sale: Boolean, val mode: Mode, val value: Double, val round: Int) : BulkOp
data class BSalePct(val percent: Double, val round: Int) : BulkOp
data object BClearSale : BulkOp
data class BStatus(val status: String) : BulkOp
data class BStockStatus(val status: String) : BulkOp
data class BQty(val add: Boolean, val value: Int) : BulkOp
data class BCats(val ids: List<Long>, val mode: CatMode) : BulkOp
data class BFeatured(val on: Boolean) : BulkOp
data class BBackorders(val value: String) : BulkOp
data class BName(val find: String, val replace: String, val prefix: String, val suffix: String) : BulkOp
data class BDelete(val permanent: Boolean) : BulkOp

class Change(
    val id: Long,
    val name: String,
    val json: JSONObject,
    val desc: String,
    val viaVariations: Boolean = false,
    val parentId: Long = 0
)

private fun regularFor(op: BPrice, regular: String, sale: String): Pair<JSONObject, String>? {
    val old = (if (op.sale) sale else regular).toDoubleOrNull()
    var nv: Double
    if (op.mode == Mode.SET) {
        nv = op.value
    } else {
        if (old == null) return null
        nv = when (op.mode) {
            Mode.PCT_UP -> old * (1 + op.value / 100.0)
            Mode.PCT_DOWN -> old * (1 - op.value / 100.0)
            Mode.AMT_UP -> old + op.value
            else -> old - op.value
        }
    }
    nv = roundStep(nv, op.round)
    if (nv < 0) return null
    if (op.sale) {
        val reg = regular.toDoubleOrNull() ?: return null
        if (nv >= reg) return null
    }
    if (old != null && old == nv) return null
    val j = JSONObject()
    j.put(if (op.sale) "sale_price" else "regular_price", fmtPrice(nv))
    return Pair(j, "از ${if (old != null) money(old) else "—"} به ${money(nv)}")
}

private fun salePctFor(op: BSalePct, regular: String, sale: String): Pair<JSONObject, String>? {
    val reg = regular.toDoubleOrNull() ?: return null
    val nv = roundStep(reg * (1 - op.percent / 100.0), op.round)
    if (nv <= 0 || nv >= reg) return null
    if (sale.toDoubleOrNull() == nv) return null
    val j = JSONObject()
    j.put("sale_price", fmtPrice(nv))
    return Pair(j, "حراج ${money(nv)} (از ${money(reg)})")
}

fun priceJson(op: BulkOp, regular: String, sale: String): Pair<JSONObject, String>? = when (op) {
    is BPrice -> regularFor(op, regular, sale)
    is BSalePct -> salePctFor(op, regular, sale)
    is BClearSale -> if (sale.isBlank()) null else Pair(JSONObject().put("sale_price", ""), "پایان حراج")
    else -> null
}

fun planBulk(p: WProduct, op: BulkOp): Change? {
    return when (op) {
        is BPrice, is BSalePct, is BClearSale -> {
            if (p.type == "variable") {
                Change(p.id, p.name, JSONObject(), "همهٔ تنوع‌ها", viaVariations = true)
            } else {
                if (!p.priceEditable) return null
                val r = priceJson(op, p.regularPrice, p.salePrice) ?: return null
                Change(p.id, p.name, r.first, r.second)
            }
        }
        is BStatus -> {
            if (p.status == op.status) return null
            Change(p.id, p.name, JSONObject().put("status", op.status), "وضعیت: ${statusFa(op.status)}")
        }
        is BStockStatus -> {
            if (p.stockStatus == op.status) return null
            Change(p.id, p.name, JSONObject().put("stock_status", op.status), "موجودی: ${stockFa(op.status)}")
        }
        is BQty -> {
            val cur = p.stockQty ?: 0
            val nq = (if (op.add) cur + op.value else op.value).coerceAtLeast(0)
            if (p.manageStock && p.stockQty == nq) return null
            val j = JSONObject()
            j.put("manage_stock", true)
            j.put("stock_quantity", nq)
            Change(p.id, p.name, j, "تعداد ${fa(cur)} به ${fa(nq)}")
        }
        is BCats -> {
            val ids = when (op.mode) {
                CatMode.REPLACE -> op.ids
                CatMode.ADD -> (p.catIds + op.ids).distinct()
                CatMode.REMOVE -> p.catIds.filter { !op.ids.contains(it) }
            }
            if (ids == p.catIds) return null
            val arr = JSONArray()
            ids.forEach { arr.put(JSONObject().put("id", it)) }
            Change(p.id, p.name, JSONObject().put("categories", arr), "${fa(ids.size)} دسته")
        }
        is BFeatured -> {
            if (p.featured == op.on) return null
            Change(p.id, p.name, JSONObject().put("featured", op.on), if (op.on) "ویژه" else "حذف از ویژه‌ها")
        }
        is BBackorders -> {
            if (p.backorders == op.value) return null
            val label = when (op.value) {
                "yes" -> "مجاز"
                "notify" -> "مجاز با اطلاع به مشتری"
                else -> "غیرمجاز"
            }
            Change(p.id, p.name, JSONObject().put("backorders", op.value), "پیش‌سفارش: $label")
        }
        is BName -> {
            var n = p.name
            if (op.find.isNotEmpty()) n = n.replace(op.find, op.replace)
            n = op.prefix + n + op.suffix
            if (n == p.name || n.isBlank()) return null
            Change(p.id, p.name, JSONObject().put("name", n), n)
        }
        is BDelete -> null
    }
}

fun oldValue(p: WProduct, k: String): Any = when (k) {
    "regular_price" -> p.regularPrice
    "sale_price" -> p.salePrice
    "status" -> p.status
    "stock_status" -> p.stockStatus
    "manage_stock" -> p.manageStock
    "stock_quantity" -> p.stockQty ?: JSONObject.NULL
    "featured" -> p.featured
    "backorders" -> p.backorders
    "name" -> p.name
    "categories" -> {
        val a = JSONArray()
        p.catIds.forEach { a.put(JSONObject().put("id", it)) }
        a
    }
    else -> JSONObject.NULL
}

fun revertOf(p: WProduct, c: Change): Change {
    val j = JSONObject()
    val keys = c.json.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        j.put(k, oldValue(p, k))
    }
    return Change(p.id, p.name, j, "")
}

fun statusFa(s: String): String = when (s) {
    "publish" -> "منتشر شده"
    "draft" -> "پیش‌نویس"
    "pending" -> "در انتظار"
    "private" -> "خصوصی"
    "trash" -> "زباله"
    "future" -> "زمان‌بندی‌شده"
    else -> s
}

fun stockFa(s: String): String = when (s) {
    "instock" -> "موجود"
    "outofstock" -> "ناموجود"
    "onbackorder" -> "پیش‌سفارش"
    else -> s
}

fun orderFa(s: String): String = when (s) {
    "pending" -> "در انتظار پرداخت"
    "processing" -> "در حال انجام"
    "on-hold" -> "در انتظار بررسی"
    "completed" -> "تکمیل شده"
    "cancelled" -> "لغو شده"
    "refunded" -> "مسترد شده"
    "failed" -> "ناموفق"
    else -> s
}

fun typeFa(s: String): String = when (s) {
    "simple" -> "ساده"
    "variable" -> "متغیر"
    "grouped" -> "گروهی"
    "external" -> "خارجی"
    else -> s
}

// ───────────────────────── وضعیت و عملیات فروشگاه ─────────────────────────

class WooState(ctx: Context, private val scope: CoroutineScope) {
    private val app: Context = ctx.applicationContext
    private val sp = ctx.getSharedPreferences("woo", Context.MODE_PRIVATE)
    private var key = ""
    private var secret = ""
    private var cl: WooClient? = null
    private var booted = false
    private var prodJob: Job? = null
    private var orderJob: Job? = null
    private var custJob: Job? = null

    var siteUrl by mutableStateOf(sp.getString("url", "") ?: "")
        private set
    var currency by mutableStateOf(sp.getString("cur", "تومان") ?: "تومان")
        private set
    var shopName by mutableStateOf(sp.getString("shop_name", "") ?: "")
        private set
    var shopPhone by mutableStateOf(sp.getString("shop_phone", "") ?: "")
        private set
    var shopAddr by mutableStateOf(sp.getString("shop_addr", "") ?: "")
        private set
    var connected by mutableStateOf(false)
        private set
    var wpUser by mutableStateOf(sp.getString("wu", "") ?: "")
        private set
    var wpPass by mutableStateOf(Vault.dec(sp.getString("wp", "") ?: ""))
        private set
    val hasWpAuth: Boolean get() = wpUser.isNotBlank() && wpPass.isNotBlank()

    val displayShopName: String get() = shopName.ifBlank { siteUrl.removePrefix("https://") }

    // ناوبری: 0 داشبورد، 1 محصولات، 2 دسته‌ها، 3 سفارش‌ها، 4 مشتری‌ها، 5 تنظیمات
    var tab by mutableIntStateOf(0)

    // محصولات و فیلترها
    val products = mutableStateListOf<WProduct>()
    var total by mutableIntStateOf(0)
    var page by mutableIntStateOf(0)
    var pages by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var statusF by mutableStateOf("any")
    var stockF by mutableStateOf("")
    var catF by mutableLongStateOf(0L)
    var sort by mutableIntStateOf(0)
    var typeF by mutableStateOf("")
    var onSaleF by mutableStateOf(false)
    var featuredF by mutableStateOf(false)
    var minPrice by mutableStateOf("")
    var maxPrice by mutableStateOf("")
    var viewGrid by mutableStateOf(false)
    var siteOffsetSec by mutableStateOf<Int?>(null)

    val activeFilterCount: Int
        get() = listOf(
            statusF != "any", stockF.isNotEmpty(),
            typeF.isNotEmpty(), onSaleF, featuredF,
            minPrice.isNotBlank(), maxPrice.isNotBlank(), catF != 0L
        ).count { it }

    val sel = mutableStateMapOf<Long, WProduct>()
    val categories = mutableStateListOf<WCat>()
    val brands = mutableStateListOf<WCat>()
    var brandsSupported by mutableStateOf(true)
    var brandsError by mutableStateOf("")
    var lastCreatedId by mutableStateOf(0L)
    val attrDefs = mutableStateListOf<WAttrDef>()
    val tags = mutableStateListOf<WCat>()
    val attrTerms = androidx.compose.runtime.mutableStateMapOf<Long, List<String>>()

    var busy by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var undoList by mutableStateOf<List<Change>>(emptyList())
        private set
    var stats by mutableStateOf<ShopStats?>(null)
        private set

    // سفارش‌ها
    val orders = mutableStateListOf<WOrder>()
    var ordersLoading by mutableStateOf(false)
    var ordersError by mutableStateOf<String?>(null)
    var orderStatusF by mutableStateOf("any")
    var orderQuery by mutableStateOf("")
    var orderRange by mutableIntStateOf(0)
    var orderCustomer by mutableLongStateOf(0L)
    var orderCustomerName by mutableStateOf("")
    private var ordersPage = 0
    private var ordersPages = 0
    val ordersHasMore: Boolean get() = ordersPage in 1 until ordersPages

    // مشتری‌ها
    val customers = mutableStateListOf<WCustomer>()
    var customersLoading by mutableStateOf(false)
    var customersError by mutableStateOf<String?>(null)
    var customerQuery by mutableStateOf("")
    private var custPage = 0
    private var custPages = 0
    val customersHasMore: Boolean get() = custPage in 1 until custPages

    init {
        key = Vault.dec(sp.getString("k", "") ?: "")
        secret = Vault.dec(sp.getString("s", "") ?: "")
        if (siteUrl.isNotEmpty() && key.isNotEmpty() && secret.isNotEmpty()) {
            cl = WooClient(siteUrl, key, secret)
            connected = true
        }
    }

    private fun go(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
            }
        }
    }

    // ── اتصال
    fun connect(url: String, k: String, s: String, cur: String, onResult: (String?) -> Unit) {
        val u = WooClient.normalize(url)
        if (u.startsWith("http://")) {
            onResult("فقط HTTPS پشتیبانی می‌شود؛ کلیدها نباید روی HTTP ارسال شوند.")
            return
        }
        if (k.isBlank() || s.isBlank()) {
            onResult("Consumer Key و Secret را وارد کن")
            return
        }
        val c = WooClient(u, k.trim(), s.trim())
        scope.launch {
            try {
                val r = c.call("GET", "/products", mapOf("per_page" to "1"))
                if (!r.ok) {
                    onResult(r.errorText())
                    return@launch
                }
                key = k.trim()
                secret = s.trim()
                sp.edit()
                    .putString("url", u)
                    .putString("k", Vault.enc(key))
                    .putString("s", Vault.enc(secret))
                    .putString("cur", cur.trim().ifBlank { "تومان" })
                    .apply()
                siteUrl = u
                currency = cur.trim().ifBlank { "تومان" }
                cl = c
                booted = false
                connected = true
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(friendly(e))
            }
        }
    }

    fun changeCurrency(c: String) {
        currency = c.trim().ifBlank { "تومان" }
        sp.edit().putString("cur", currency).apply()
    }

    fun saveWpAuth(user: String, pass: String) {
        wpUser = user.trim()
        wpPass = pass.trim()
        sp.edit().putString("wu", wpUser).putString("wp", Vault.enc(wpPass)).apply()
    }

    fun saveShopInfo(name: String, phone: String, addr: String) {
        shopName = name.trim()
        shopPhone = phone.trim()
        shopAddr = addr.trim()
        sp.edit().putString("shop_name", shopName).putString("shop_phone", shopPhone)
            .putString("shop_addr", shopAddr).apply()
    }

    fun disconnect() {
        sp.edit().clear().apply()
        key = ""
        secret = ""
        cl = null
        siteUrl = ""
        shopName = ""
        shopPhone = ""
        shopAddr = ""
        wpUser = ""
        wpPass = ""
        connected = false
        booted = false
        products.clear()
        orders.clear()
        customers.clear()
        categories.clear()
        brands.clear()
        sel.clear()
        stats = null
        undoList = emptyList()
        tab = 0
    }

    fun bootstrap() {
        if (booted || !connected) return
        booted = true
        loadCategories()
        loadBrands()
        loadAttributes()
        loadTags()
        loadStats()
        loadProducts(true)
    }

    fun refreshAll() {
        loadCategories()
        loadStats()
        loadProducts(true)
        if (orders.isNotEmpty()) loadOrders(true)
        if (customers.isNotEmpty()) loadCustomers(true)
    }

    // ── محصولات
    private fun productQuery(): LinkedHashMap<String, String> {
        val q = LinkedHashMap<String, String>()
        q["status"] = statusF
        if (query.isNotBlank()) q["search"] = query.trim()
        if (stockF.isNotEmpty()) q["stock_status"] = stockF
        if (catF != 0L) q["category"] = catF.toString()
        if (typeF.isNotEmpty()) q["type"] = typeF
        if (onSaleF) q["on_sale"] = "true"
        if (featuredF) q["featured"] = "true"
        val mn = en(minPrice).trim()
        val mx = en(maxPrice).trim()
        if (mn.toDoubleOrNull() != null) q["min_price"] = mn
        if (mx.toDoubleOrNull() != null) q["max_price"] = mx
        when (sort) {
            1 -> { q["orderby"] = "title"; q["order"] = "asc" }
            2 -> { q["orderby"] = "price"; q["order"] = "asc" }
            3 -> { q["orderby"] = "price"; q["order"] = "desc" }
            4 -> { q["orderby"] = "popularity"; q["order"] = "desc" }
            else -> { q["orderby"] = "date"; q["order"] = "desc" }
        }
        return q
    }

    fun resetFilters() {
        statusF = "any"
        stockF = ""
        catF = 0L
        typeF = ""
        onSaleF = false
        featuredF = false
        minPrice = ""
        maxPrice = ""
        query = ""
    }

    fun openCategory(id: Long) {
        resetFilters()
        catF = id
        tab = 1
        loadProducts(true)
    }

    fun loadProducts(reset: Boolean = true) {
        val c = cl ?: return
        if (!reset && (loading || page >= pages)) return
        if (reset) {
            prodJob?.cancel()
        }
        val next = if (reset) 1 else page + 1
        loading = true
        error = null
        prodJob = scope.launch {
            try {
                val q = productQuery()
                q["per_page"] = "30"
                q["page"] = next.toString()
                val r = c.call("GET", "/products", q)
                if (!r.ok) throw WooException(r.errorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WProduct>()
                for (i in 0 until arr.length()) list.add(parseProduct(arr.getJSONObject(i)))
                if (reset) products.clear()
                products.addAll(list)
                if (siteOffsetSec == null) {
                    siteOffsetSec = list.firstNotNullOfOrNull { p ->
                        try {
                            (java.time.LocalDateTime.parse(p.date.take(19)).toEpochSecond(java.time.ZoneOffset.UTC) -
                                    java.time.LocalDateTime.parse(p.dateGmt.take(19)).toEpochSecond(java.time.ZoneOffset.UTC)).toInt()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                page = next
                pages = r.pages
                total = r.total
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e)
                loading = false
            }
        }
    }

    fun toggleSelect(p: WProduct) {
        if (sel.containsKey(p.id)) sel.remove(p.id) else sel[p.id] = p
    }

    private suspend fun fetchProductPages(c: WooClient, base: Map<String, String>, maxPages: Int, onPage: (List<WProduct>) -> Unit) {
        var pg = 1
        while (pg <= maxPages) {
            val q = LinkedHashMap<String, String>(base)
            q["per_page"] = "100"
            q["page"] = pg.toString()
            val r = c.call("GET", "/products", q)
            if (!r.ok) throw WooException(r.errorText())
            val arr = JSONArray(r.body)
            val list = ArrayList<WProduct>()
            for (i in 0 until arr.length()) list.add(parseProduct(arr.getJSONObject(i)))
            onPage(list)
            if (pg >= r.pages) break
            pg++
        }
    }

    fun selectAllMatching(onDone: (Int) -> Unit = {}) {
        val c = cl ?: return
        go {
            var count = 0
            busy = "در حال انتخاب نتایج..."
            fetchProductPages(c, productQuery(), 10) { list ->
                list.forEach { sel[it.id] = it }
                count += list.size
                busy = "در حال انتخاب... ${fa(count)}"
            }
            busy = null
            message = "${fa(count)} محصول انتخاب شد"
            onDone(count)
        }
    }

    fun selectInCategory(catId: Long, onDone: (Int) -> Unit = {}) {
        val c = cl ?: return
        go {
            var count = 0
            busy = "در حال انتخاب محصولات دسته..."
            fetchProductPages(c, mapOf("status" to "any", "category" to catId.toString()), 10) { list ->
                list.forEach { sel[it.id] = it }
                count += list.size
            }
            busy = null
            message = "${fa(count)} محصول انتخاب شد"
            onDone(count)
        }
    }

    // ── دسته‌ها
    fun loadCategories() {
        val c = cl ?: return
        go {
            val all = ArrayList<WCat>()
            var pg = 1
            while (pg <= 5) {
                val r = c.call("GET", "/products/categories", mapOf("per_page" to "100", "page" to pg.toString(), "hide_empty" to "false"))
                if (!r.ok) break
                val arr = JSONArray(r.body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val im = o.optJSONObject("image")?.str("src") ?: ""
                    all.add(WCat(o.optLong("id"), clean(o.str("name")), o.optInt("count"), o.optLong("parent"), im))
                }
                if (pg >= r.pages) break
                pg++
            }
            categories.clear()
            categories.addAll(orderCats(all))
        }
    }

    /** برچسب‌ها: نام‌ها را به شناسه تبدیل می‌کند و برچسب جدید می‌سازد */
    private suspend fun resolveTags(c: WooClient, names: List<String>): List<Long>? {
        if (names.isEmpty()) return emptyList()
        val ids = ArrayList<Long>()
        for (n in names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val known = tags.firstOrNull { it.name.equals(n, true) }
            if (known != null) { ids.add(known.id); continue }
            val r = c.call("POST", "/products/tags", body = JSONObject().put("name", n).toString())
            if (r.ok) {
                val o = JSONObject(r.body)
                val t = WCat(o.optLong("id"), clean(o.str("name")), 0, 0L, "")
                tags.add(t)
                ids.add(t.id)
            } else {
                // اگر از قبل وجود داشته باشد وردپرس شناسه را در خطا برمی‌گرداند
                val m = Regex("\"resource_id\":\\s*(\\d+)").find(r.body)
                m?.groupValues?.get(1)?.toLongOrNull()?.let { ids.add(it) }
            }
        }
        return ids
    }

    fun loadTags() {
        val c = cl ?: return
        scope.launch {
            try {
                val all = ArrayList<WCat>()
                var pg = 1
                while (pg <= 5) {
                    val r = c.call("GET", "/products/tags", mapOf("per_page" to "100", "page" to pg.toString(), "hide_empty" to "false"))
                    if (!r.ok) break
                    val arr = JSONArray(r.body)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        all.add(WCat(o.optLong("id"), clean(o.str("name")), o.optInt("count"), 0L, ""))
                    }
                    if (pg >= r.pages) break
                    pg++
                }
                tags.clear()
                tags.addAll(all.sortedBy { it.name })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    /** ویژگی‌های سراسری سایت (مثل رنگ و سایز) */
    fun loadAttributes() {
        val c = cl ?: return
        scope.launch {
            try {
                val r = c.call("GET", "/products/attributes")
                if (!r.ok) return@launch
                val arr = JSONArray(r.body)
                val list = ArrayList<WAttrDef>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(WAttrDef(o.optLong("id"), clean(o.str("name")), o.str("slug")))
                }
                attrDefs.clear()
                attrDefs.addAll(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    /** مقدارهای (ترم‌های) یک ویژگی سراسری */
    fun loadAttrTerms(attrId: Long) {
        val c = cl ?: return
        if (attrTerms.containsKey(attrId)) return
        scope.launch {
            try {
                val all = ArrayList<String>()
                var pg = 1
                while (pg <= 5) {
                    val r = c.call("GET", "/products/attributes/$attrId/terms", mapOf("per_page" to "100", "page" to pg.toString(), "hide_empty" to "false"))
                    if (!r.ok) break
                    val arr = JSONArray(r.body)
                    for (i in 0 until arr.length()) all.add(clean(arr.getJSONObject(i).str("name")))
                    if (pg >= r.pages) break
                    pg++
                }
                attrTerms[attrId] = all
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attrTerms[attrId] = emptyList()
            }
        }
    }

    fun loadBrands() {
        val c = cl ?: return
        go {
            val all = ArrayList<WCat>()
            var pg = 1
            while (pg <= 5) {
                val r = c.call("GET", "/products/brands", mapOf("per_page" to "100", "page" to pg.toString(), "hide_empty" to "false"))
                if (!r.ok) {
                    if (pg == 1) {
                        brandsSupported = false
                        brandsError = "کد ${r.code}"
                        return@go
                    }
                    break
                }
                val arr = JSONArray(r.body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val im = o.optJSONObject("image")?.str("src") ?: ""
                    all.add(WCat(o.optLong("id"), clean(o.str("name")), o.optInt("count"), 0L, im))
                }
                if (pg >= r.pages) break
                pg++
            }
            brandsSupported = true
            brandsError = ""
            brands.clear()
            brands.addAll(all.sortedBy { it.name })
        }
    }

    fun saveBrand(id: Long, name: String, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        go {
            busy = "در حال ذخیرهٔ برند..."
            val body = JSONObject().put("name", name).toString()
            val r = if (id == 0L) c.call("POST", "/products/brands", body = body)
            else c.call("PUT", "/products/brands/$id", body = body)
            if (!r.ok) throw WooException(r.errorText())
            message = "برند ذخیره شد"
            busy = null
            loadBrands()
            onDone(true)
        }
    }

    fun deleteBrand(id: Long, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        go {
            busy = "در حال حذف برند..."
            val r = c.call("DELETE", "/products/brands/$id", mapOf("force" to "true"))
            if (!r.ok) throw WooException(r.errorText())
            message = "برند حذف شد"
            busy = null
            loadBrands()
            onDone(true)
        }
    }

    fun saveCategory(id: Long, name: String, parent: Long, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        go {
            busy = "در حال ذخیرهٔ دسته..."
            val body = JSONObject().put("name", name).put("parent", parent).toString()
            val r = if (id == 0L) c.call("POST", "/products/categories", body = body)
            else c.call("PUT", "/products/categories/$id", body = body)
            if (!r.ok) throw WooException(r.errorText())
            message = "دسته ذخیره شد"
            busy = null
            loadCategories()
            onDone(true)
        }
    }

    fun deleteCategory(id: Long, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        go {
            busy = "در حال حذف دسته..."
            val r = c.call("DELETE", "/products/categories/$id", mapOf("force" to "true"))
            if (!r.ok) throw WooException(r.errorText())
            message = "دسته حذف شد"
            busy = null
            loadCategories()
            onDone(true)
        }
    }

    // ── آمار
    private suspend fun count(c: WooClient, extra: Map<String, String>): Int {
        return try {
            val q = LinkedHashMap<String, String>(extra)
            q["per_page"] = "1"
            val r = c.call("GET", "/products", q)
            if (r.ok) r.total else 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0
        }
    }

    fun loadStats() {
        val c = cl ?: return
        scope.launch {
            try {
                val today = LocalDate.now()
                val from = today.minusDays(13)
                val st = coroutineScope {
                    val all = async { count(c, mapOf("status" to "any")) }
                    val pub = async { count(c, mapOf("status" to "publish")) }
                    val dr = async { count(c, mapOf("status" to "draft")) }
                    val oos = async { count(c, mapOf("status" to "any", "stock_status" to "outofstock")) }
                    val month = async {
                        try { c.call("GET", "/reports/sales", mapOf("period" to "month")) } catch (e: Exception) { null }
                    }
                    val days = async {
                        try {
                            c.call("GET", "/reports/sales", mapOf("date_min" to from.toString(), "date_max" to today.toString()))
                        } catch (e: Exception) { null }
                    }
                    val totals = async {
                        try { c.call("GET", "/reports/orders/totals") } catch (e: Exception) { null }
                    }
                    val topS = async {
                        try { c.call("GET", "/reports/top_sellers", mapOf("period" to "month")) } catch (e: Exception) { null }
                    }
                    var salesV = 0.0
                    var ordersV = 0
                    val mr = month.await()
                    if (mr != null && mr.ok) {
                        try {
                            val o = JSONArray(mr.body).getJSONObject(0)
                            salesV = o.dbl("total_sales")
                            ordersV = o.optInt("total_orders")
                        } catch (e: Exception) {
                        }
                    }
                    val series = ArrayList<Float>()
                    val labels = ArrayList<String>()
                    var sToday = 0.0
                    var oToday = 0
                    val dr2 = days.await()
                    var totalsObj: JSONObject? = null
                    if (dr2 != null && dr2.ok) {
                        try {
                            totalsObj = JSONArray(dr2.body).getJSONObject(0).optJSONObject("totals")
                        } catch (e: Exception) {
                        }
                    }
                    for (d in 0..13) {
                        val day = from.plusDays(d.toLong())
                        val o = totalsObj?.optJSONObject(day.toString())
                        val sv = o?.dbl("sales") ?: 0.0
                        series.add(sv.toFloat())
                        labels.add(fa(pc(day.toEpochDay()).get(Calendar.DAY_OF_MONTH)))
                        if (d == 13) {
                            sToday = sv
                            oToday = o?.optInt("orders") ?: 0
                        }
                    }
                    val map = LinkedHashMap<String, Int>()
                    val tr = totals.await()
                    if (tr != null && tr.ok) {
                        try {
                            val a = JSONArray(tr.body)
                            for (i in 0 until a.length()) {
                                val o = a.getJSONObject(i)
                                map[o.str("slug")] = o.str("total").toIntOrNull() ?: 0
                            }
                        } catch (e: Exception) {
                        }
                    }
                    val top = ArrayList<Pair<String, Int>>()
                    val ts = topS.await()
                    if (ts != null && ts.ok) {
                        try {
                            val a = JSONArray(ts.body)
                            for (i in 0 until minOf(a.length(), 5)) {
                                val o = a.getJSONObject(i)
                                top.add(Pair(clean(o.str("name").ifBlank { o.str("title") }), o.optInt("quantity")))
                            }
                        } catch (e: Exception) {
                        }
                    }
                    ShopStats(
                        all.await(), pub.await(), dr.await(), oos.await(), salesV, ordersV,
                        map, series, labels, sToday, oToday, top
                    )
                }
                stats = st
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    // ── ذخیره / حذف محصول
    fun save(p: WProduct, imageChanged: Boolean, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال ذخیره..."
            try {
                val isNew = p.id == 0L
                val tids = resolveTags(c, p.tagNames)
                val body = p.toJson(isNew, imageChanged, tids).toString()
                val r = if (isNew) c.call("POST", "/products", body = body)
                else c.call("PUT", "/products/${p.id}", body = body)
                if (!r.ok) throw WooException(r.errorText())
                val np = parseProduct(JSONObject(r.body))
                val i = products.indexOfFirst { it.id == np.id }
                if (i >= 0) products[i] = np else products.add(0, np)
                if (sel.containsKey(np.id)) sel[np.id] = np
                if (np.status == "future") Notifier.scheduleAlarm(app, np.id, np.name, Notifier.gmtToMillis(np.dateGmt))
                else Notifier.cancelAlarm(app, np.id)
                message = if (isNew) "محصول ساخته شد" else if (np.status == "future") "ذخیره و زمان‌بندی شد" else "ذخیره شد"
                loadStats()
                busy = null
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    // ── آپلود تصویر از گالری (نیاز به Application Password وردپرس)
    private fun prepareImage(ctx: Context, uri: Uri): ByteArray {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= 1600 || o.outHeight / (s * 2) >= 1600) s *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = s
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: throw WooException("خواندن تصویر ناموفق بود")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }

    private fun doUpload(ctx: Context, uri: Uri): String {
        val bytes = prepareImage(ctx, uri)
        val conn = URL(siteUrl + "/wp-json/wp/v2/media").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bytes.size)
            val cred = Base64.encodeToString("$wpUser:$wpPass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $cred")
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("Content-Disposition", "attachment; filename=\"app-${System.currentTimeMillis()}.jpg\"")
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).optString("message", "") } catch (e: Exception) { "" }
                throw WooException(
                    if (code == 401 || code == 403) "آپلود رد شد؛ نام کاربری و Application Password وردپرس را بررسی کن. $msg"
                    else msg.ifBlank { "آپلود ناموفق ($code)" }
                )
            }
            val j = JSONObject(text)
            val u = j.getString("source_url")
            ImageIds.map[u] = j.optLong("id")
            return u
        } finally {
            conn.disconnect()
        }
    }

    fun uploadImage(ctx: Context, uri: Uri, onDone: (String?) -> Unit) {
        if (!hasWpAuth) {
            message = "برای آپلود از گالری، در تنظیمات «نام کاربری وردپرس» و «Application Password» را وارد کن"
            onDone(null)
            return
        }
        scope.launch {
            busy = "در حال آپلود تصویر..."
            try {
                val url = withContext(Dispatchers.IO) { doUpload(ctx, uri) }
                message = "تصویر آپلود شد"
                busy = null
                onDone(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(null)
            }
        }
    }

    fun uploadImages(ctx: Context, uris: List<Uri>, onDone: (List<String>) -> Unit) {
        if (!hasWpAuth) {
            message = "برای آپلود از گالری، در تنظیمات «نام کاربری وردپرس» و «Application Password» را وارد کن"
            onDone(emptyList())
            return
        }
        scope.launch {
            val out = ArrayList<String>()
            try {
                for ((i, u) in uris.withIndex()) {
                    busy = "آپلود تصویر ${fa(i + 1)} از ${fa(uris.size)}..."
                    out.add(withContext(Dispatchers.IO) { doUpload(ctx, u) })
                }
                message = "${fa(out.size)} تصویر آپلود شد"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
            }
            busy = null
            onDone(out)
        }
    }

    // ── افزودن یک تنوع به محصول متغیر موجود
    fun addVariation(
        pid: Long,
        attrs: List<Pair<String, String>>,
        regular: String,
        sale: String,
        qty: Int?,
        onDone: (Boolean) -> Unit
    ) {
        val c = cl ?: return
        scope.launch {
            try {
                busy = "در حال افزودن تنوع..."
                val pr = c.call("GET", "/products/$pid")
                if (!pr.ok) throw WooException(pr.errorText())
                val ex = JSONObject(pr.body).optJSONArray("attributes") ?: JSONArray()
                val newArr = JSONArray()
                val ids = HashMap<String, Long>()
                val used = HashSet<String>()
                for (i in 0 until ex.length()) {
                    val a = ex.getJSONObject(i)
                    val an = a.str("name")
                    val want = attrs.firstOrNull { it.first.equals(an, true) }
                    ids[an.lowercase()] = a.optLong("id")
                    val o = JSONObject()
                    if (a.optLong("id") > 0) o.put("id", a.optLong("id"))
                    o.put("name", an).put("position", i)
                    o.put("visible", a.optBoolean("visible", true))
                    o.put("variation", a.optBoolean("variation", false) || want != null)
                    val opts = JSONArray()
                    val oa = a.optJSONArray("options") ?: JSONArray()
                    var has = false
                    for (j in 0 until oa.length()) {
                        val sv = oa.getString(j)
                        opts.put(sv)
                        if (want != null && sv.equals(want.second, true)) has = true
                    }
                    if (want != null && !has) opts.put(want.second)
                    o.put("options", opts)
                    newArr.put(o)
                    if (want != null) used.add(want.first.lowercase())
                }
                attrs.filter { !used.contains(it.first.lowercase()) }.forEach {
                    newArr.put(
                        JSONObject().put("name", it.first).put("position", newArr.length())
                            .put("visible", true).put("variation", true).put("options", JSONArray().put(it.second))
                    )
                }
                val ur = c.call("PUT", "/products/$pid", body = JSONObject().put("attributes", newArr).toString())
                if (!ur.ok) throw WooException(ur.errorText())
                val v = JSONObject()
                val at = JSONArray()
                attrs.forEach { (n, o) ->
                    val aid = ids[n.lowercase()] ?: 0L
                    val j = JSONObject()
                    if (aid > 0) j.put("id", aid) else j.put("name", n)
                    at.put(j.put("option", o))
                }
                v.put("attributes", at)
                v.put("regular_price", regular)
                if (sale.isNotBlank()) v.put("sale_price", sale)
                if (qty != null) {
                    v.put("manage_stock", true)
                    v.put("stock_quantity", qty)
                }
                v.put("status", "publish")
                val vr = c.call("POST", "/products/$pid/variations", body = v.toString())
                if (!vr.ok) throw WooException(vr.errorText())
                message = "تنوع جدید اضافه شد"
                busy = null
                loadProducts(true)
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    // ── ساخت محصول متغیر با تنوع‌ها
    fun createVariable(
        p: WProduct,
        attrs: List<Pair<String, List<String>>>,
        regular: String,
        sale: String,
        qty: Int?,
        attrIds: Map<String, Long> = emptyMap(),
        onDone: (Boolean) -> Unit
    ) {
        val c = cl ?: return
        scope.launch {
            try {
                busy = "در حال ساخت محصول متغیر..."
                val body = p.toJson(true, p.image.isNotBlank() || p.gallery.isNotEmpty(), resolveTags(c, p.tagNames))
                val aa = JSONArray()
                attrs.forEachIndexed { i, (n, opts) ->
                    val oa = JSONArray()
                    opts.forEach { oa.put(it) }
                    val aj = JSONObject()
                    val gid = attrIds[n] ?: 0L
                    if (gid > 0L) aj.put("id", gid) else aj.put("name", n)
                    aa.put(aj.put("position", i).put("visible", true).put("variation", true).put("options", oa))
                }
                body.put("attributes", aa)
                val r = c.call("POST", "/products", body = body.toString())
                if (!r.ok) throw WooException(r.errorText())
                val np = parseProduct(JSONObject(r.body))
                lastCreatedId = np.id
                if (np.status == "future") Notifier.scheduleAlarm(app, np.id, np.name, Notifier.gmtToMillis(np.dateGmt))
                var combos: List<List<Pair<String, String>>> = listOf(emptyList())
                for ((n, opts) in attrs) combos = combos.flatMap { pre -> opts.map { pre + (n to it) } }
                var ok = 0
                for (chunk in combos.chunked(50)) {
                    busy = "ساخت تنوع‌ها ${fa(ok)} از ${fa(combos.size)}"
                    val arr = JSONArray()
                    chunk.forEach { combo ->
                        val v = JSONObject()
                        val at = JSONArray()
                        combo.forEach { (n, o) ->
                            val gid = attrIds[n] ?: 0L
                            val aj = JSONObject()
                            if (gid > 0L) aj.put("id", gid) else aj.put("name", n)
                            at.put(aj.put("option", o))
                        }
                        v.put("attributes", at)
                        v.put("regular_price", regular)
                        if (sale.isNotBlank()) v.put("sale_price", sale)
                        if (qty != null) {
                            v.put("manage_stock", true)
                            v.put("stock_quantity", qty)
                        }
                        v.put("status", "publish")
                        arr.put(v)
                    }
                    val rr = c.call("POST", "/products/${np.id}/variations/batch", body = JSONObject().put("create", arr).toString())
                    if (rr.ok) {
                        val cr = JSONObject(rr.body).optJSONArray("create") ?: JSONArray()
                        for (i in 0 until cr.length()) if (!cr.getJSONObject(i).has("error")) ok++
                    }
                }
                message = "محصول متغیر ساخته شد؛ ${fa(ok)} از ${fa(combos.size)} تنوع"
                busy = null
                loadProducts(true)
                loadStats()
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    fun deleteProduct(p: WProduct, permanent: Boolean, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال حذف..."
            try {
                val r = c.call("DELETE", "/products/${p.id}", mapOf("force" to permanent.toString()))
                if (!r.ok) throw WooException(r.errorText())
                products.removeAll { it.id == p.id }
                sel.remove(p.id)
                message = if (permanent) "برای همیشه حذف شد" else "به زباله منتقل شد"
                loadStats()
                busy = null
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    // ── تنوع‌ها
    private suspend fun fetchVariations(c: WooClient, pid: Long): List<WVariation> {
        val out = ArrayList<WVariation>()
        var pg = 1
        while (pg <= 10) {
            val r = c.call("GET", "/products/$pid/variations", mapOf("per_page" to "100", "page" to pg.toString()))
            if (!r.ok) throw WooException(r.errorText())
            val arr = JSONArray(r.body)
            for (i in 0 until arr.length()) out.add(parseVariation(arr.getJSONObject(i), pid))
            if (pg >= r.pages) break
            pg++
        }
        return out
    }

    fun loadVariations(pid: Long, onDone: (List<WVariation>?) -> Unit) {
        val c = cl ?: return
        scope.launch {
            try {
                onDone(fetchVariations(c, pid))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                onDone(null)
            }
        }
    }

    fun saveVariations(pid: Long, vars: List<WVariation>, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            try {
                var ok = 0
                var fail = 0
                for (chunk in vars.chunked(50)) {
                    busy = "ذخیرهٔ تنوع‌ها ${fa(ok + fail)} از ${fa(vars.size)}"
                    val arr = JSONArray()
                    chunk.forEach { arr.put(it.toJson().put("id", it.id)) }
                    val r = c.call("POST", "/products/$pid/variations/batch", body = JSONObject().put("update", arr).toString())
                    if (!r.ok) {
                        fail += chunk.size
                        continue
                    }
                    val upd = JSONObject(r.body).optJSONArray("update") ?: JSONArray()
                    for (i in 0 until upd.length()) {
                        if (upd.getJSONObject(i).has("error")) fail++ else ok++
                    }
                }
                message = "${fa(ok)} تنوع بروز شد" + (if (fail > 0) "، ${fa(fail)} ناموفق" else "")
                busy = null
                loadProducts(true)
                loadStats()
                onDone(fail == 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    // ── ویرایش گروهی
    fun runBulk(chosen: List<WProduct>, op: BulkOp) {
        val c = cl ?: return
        go {
            if (op is BDelete) {
                val done = ArrayList<Long>()
                var fail = 0
                for ((i, p) in chosen.withIndex()) {
                    busy = "حذف ${fa(i + 1)} از ${fa(chosen.size)}"
                    val r = c.call("DELETE", "/products/${p.id}", mapOf("force" to op.permanent.toString()))
                    if (r.ok) done.add(p.id) else fail++
                }
                products.removeAll { done.contains(it.id) }
                sel.clear()
                undoList = emptyList()
                message = "${fa(done.size)} حذف شد" + (if (fail > 0) "، ${fa(fail)} ناموفق" else "")
                loadStats()
                busy = null
                return@go
            }
            val byId = chosen.associateBy { it.id }
            val plans = chosen.mapNotNull { planBulk(it, op) }
            if (plans.isEmpty()) {
                message = "چیزی برای تغییر نبود"
                return@go
            }
            var ok = 0
            var fail = 0
            var firstErr = ""
            val reverts = ArrayList<Change>()
            val direct = plans.filter { !it.viaVariations }
            val viaVar = plans.filter { it.viaVariations }

            for (chunk in direct.chunked(50)) {
                busy = "اعمال تغییرات ${fa(ok + fail)} از ${fa(plans.size)}"
                val arr = JSONArray()
                chunk.forEach { arr.put(JSONObject(it.json.toString()).put("id", it.id)) }
                val r = c.call("POST", "/products/batch", body = JSONObject().put("update", arr).toString())
                if (!r.ok) {
                    fail += chunk.size
                    if (firstErr.isEmpty()) firstErr = r.errorText()
                    continue
                }
                val upd = JSONObject(r.body).optJSONArray("update") ?: JSONArray()
                for (i in 0 until upd.length()) {
                    val o = upd.getJSONObject(i)
                    if (o.has("error")) {
                        fail++
                        if (firstErr.isEmpty()) firstErr = o.optJSONObject("error")?.str("message") ?: ""
                    } else {
                        ok++
                        val np = parseProduct(o)
                        val idx = products.indexOfFirst { it.id == np.id }
                        if (idx >= 0) products[idx] = np
                        val old = byId[np.id]
                        val ch = chunk.firstOrNull { it.id == np.id }
                        if (old != null && ch != null) reverts.add(revertOf(old, ch))
                    }
                }
            }

            for (pl in viaVar) {
                val p = byId[pl.id] ?: continue
                busy = "تنوع‌های «${p.name}»"
                val vars = fetchVariations(c, p.id)
                val ups = ArrayList<JSONObject>()
                for (v in vars) {
                    val pj = priceJson(op, v.regularPrice, v.salePrice) ?: continue
                    ups.add(JSONObject(pj.first.toString()).put("id", v.id))
                    val rj = JSONObject()
                    val ks = pj.first.keys()
                    while (ks.hasNext()) {
                        val k = ks.next()
                        rj.put(k, if (k == "sale_price") v.salePrice else v.regularPrice)
                    }
                    reverts.add(Change(v.id, v.attrs, rj, "", parentId = p.id))
                }
                if (ups.isEmpty()) continue
                var good = true
                for (chunk in ups.chunked(50)) {
                    val arr = JSONArray()
                    chunk.forEach { arr.put(it) }
                    val r = c.call("POST", "/products/${p.id}/variations/batch", body = JSONObject().put("update", arr).toString())
                    if (!r.ok) {
                        good = false
                        if (firstErr.isEmpty()) firstErr = r.errorText()
                    }
                }
                if (good) ok++ else fail++
            }

            sel.clear()
            undoList = reverts
            message = "${fa(ok)} محصول بروز شد" + (if (fail > 0) "، ${fa(fail)} ناموفق" else "") +
                    (if (firstErr.isNotBlank()) "\n$firstErr" else "")
            if (viaVar.isNotEmpty()) loadProducts(true)
            loadStats()
            busy = null
        }
    }

    fun undo() {
        val c = cl ?: return
        val list = undoList
        if (list.isEmpty()) return
        go {
            var ok = 0
            val groups = list.groupBy { it.parentId }
            for ((pid, items) in groups) {
                for (chunk in items.chunked(50)) {
                    busy = "بازگردانی..."
                    val arr = JSONArray()
                    chunk.forEach { arr.put(JSONObject(it.json.toString()).put("id", it.id)) }
                    val path = if (pid == 0L) "/products/batch" else "/products/$pid/variations/batch"
                    val r = c.call("POST", path, body = JSONObject().put("update", arr).toString())
                    if (r.ok) {
                        val upd = JSONObject(r.body).optJSONArray("update") ?: JSONArray()
                        for (i in 0 until upd.length()) {
                            val o = upd.getJSONObject(i)
                            if (!o.has("error")) {
                                ok++
                                if (pid == 0L) {
                                    val np = parseProduct(o)
                                    val idx = products.indexOfFirst { it.id == np.id }
                                    if (idx >= 0) products[idx] = np
                                }
                            }
                        }
                    }
                }
            }
            undoList = emptyList()
            message = "${fa(ok)} مورد به حالت قبل برگشت"
            loadProducts(true)
            loadStats()
            busy = null
        }
    }

    // ── سفارش‌ها
    fun openCustomerOrders(id: Long, name: String) {
        orderCustomer = id
        orderCustomerName = name
        orderStatusF = "any"
        orderQuery = ""
        orderRange = 0
        tab = 3
        loadOrders(true)
    }

    fun clearOrderCustomer() {
        orderCustomer = 0L
        orderCustomerName = ""
        loadOrders(true)
    }

    fun loadOrders(reset: Boolean = true) {
        val c = cl ?: return
        if (!reset && (ordersLoading || !ordersHasMore)) return
        if (reset) orderJob?.cancel()
        val next = if (reset) 1 else ordersPage + 1
        ordersLoading = true
        ordersError = null
        orderJob = scope.launch {
            try {
                val q = LinkedHashMap<String, String>()
                q["per_page"] = "20"
                q["page"] = next.toString()
                q["status"] = orderStatusF
                if (orderQuery.isNotBlank()) q["search"] = orderQuery.trim()
                if (orderCustomer != 0L) q["customer"] = orderCustomer.toString()
                val days = when (orderRange) {
                    1 -> 0L
                    2 -> 6L
                    3 -> 29L
                    else -> -1L
                }
                if (days >= 0) {
                    val after = LocalDate.now().minusDays(days).atStartOfDay()
                    q["after"] = after.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                val r = c.call("GET", "/orders", q)
                if (!r.ok) throw WooException(r.errorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WOrder>()
                for (i in 0 until arr.length()) list.add(parseOrder(arr.getJSONObject(i)))
                if (reset) orders.clear()
                orders.addAll(list)
                ordersPage = next
                ordersPages = r.pages
                ordersLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ordersError = friendly(e)
                ordersLoading = false
            }
        }
    }

    fun setOrderStatus(o: WOrder, status: String, onDone: (WOrder?) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال بروزرسانی سفارش..."
            try {
                val r = c.call("PUT", "/orders/${o.id}", body = JSONObject().put("status", status).toString())
                if (!r.ok) throw WooException(r.errorText())
                val no = parseOrder(JSONObject(r.body))
                val i = orders.indexOfFirst { it.id == no.id }
                if (i >= 0) orders[i] = no
                message = "وضعیت سفارش بروز شد"
                loadStats()
                busy = null
                onDone(no)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(null)
            }
        }
    }

    fun loadNotes(orderId: Long, onDone: (List<WNote>) -> Unit) {
        val c = cl ?: return
        scope.launch {
            try {
                val r = c.call("GET", "/orders/$orderId/notes")
                val list = ArrayList<WNote>()
                if (r.ok) {
                    val arr = JSONArray(r.body)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(WNote(o.optLong("id"), clean(o.str("note")), o.optBoolean("customer_note"), orderDate(o.str("date_created"))))
                    }
                }
                onDone(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDone(emptyList())
            }
        }
    }

    fun addNote(orderId: Long, text: String, forCustomer: Boolean, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال ثبت یادداشت..."
            try {
                val body = JSONObject().put("note", text).put("customer_note", forCustomer).toString()
                val r = c.call("POST", "/orders/$orderId/notes", body = body)
                if (!r.ok) throw WooException(r.errorText())
                message = "یادداشت ثبت شد"
                busy = null
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
                onDone(false)
            }
        }
    }

    // ── مشتری‌ها
    fun loadCustomers(reset: Boolean = true) {
        val c = cl ?: return
        if (!reset && (customersLoading || !customersHasMore)) return
        if (reset) custJob?.cancel()
        val next = if (reset) 1 else custPage + 1
        customersLoading = true
        customersError = null
        custJob = scope.launch {
            try {
                val q = LinkedHashMap<String, String>()
                q["per_page"] = "20"
                q["page"] = next.toString()
                q["orderby"] = "registered_date"
                q["order"] = "desc"
                q["role"] = "all"
                if (customerQuery.isNotBlank()) q["search"] = customerQuery.trim()
                val r = c.call("GET", "/customers", q)
                if (!r.ok) throw WooException(r.errorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WCustomer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val b = o.optJSONObject("billing")
                    val nm = "${o.str("first_name")} ${o.str("last_name")}".trim()
                    list.add(
                        WCustomer(
                            o.optLong("id"),
                            nm.ifBlank { o.str("username").ifBlank { o.str("email") } },
                            o.str("email"),
                            b?.str("phone") ?: "",
                            o.optInt("orders_count"),
                            o.dbl("total_spent"),
                            orderDate(o.str("date_created"))
                        )
                    )
                }
                if (reset) customers.clear()
                customers.addAll(list)
                custPage = next
                custPages = r.pages
                customersLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                customersError = friendly(e)
                customersLoading = false
            }
        }
    }
}
