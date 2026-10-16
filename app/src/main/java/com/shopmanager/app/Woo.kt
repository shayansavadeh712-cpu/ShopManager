package com.shopmanager.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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

data class WCat(val id: Long, val name: String, val count: Int = 0)

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
    val catIds: List<Long> = emptyList(),
    val catNames: List<String> = emptyList(),
    val image: String = "",
    val description: String = "",
    val shortDescription: String = "",
    val featured: Boolean = false,
    val weight: String = "",
    val permalink: String = "",
    val totalSales: Int = 0
) {
    val priceEditable: Boolean get() = type == "simple" || type == "external"
}

data class WOrder(
    val id: Long,
    val number: String,
    val status: String,
    val total: String,
    val date: String,
    val customer: String,
    val phone: String,
    val items: List<String>,
    val payment: String,
    val note: String
)

data class ShopStats(
    val products: Int,
    val published: Int,
    val drafts: Int,
    val outOfStock: Int,
    val sales: Double,
    val ordersMonth: Int,
    val orderTotals: Map<String, Int>
)

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

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
    val img = if (imgs != null && imgs.length() > 0) imgs.getJSONObject(0).str("src") else ""
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
        catIds = ids,
        catNames = names,
        image = img,
        description = o.str("description"),
        shortDescription = o.str("short_description"),
        featured = o.optBoolean("featured", false),
        weight = o.str("weight"),
        permalink = o.str("permalink"),
        totalSales = o.optInt("total_sales", 0)
    )
}

fun WProduct.toJson(isNew: Boolean, imageChanged: Boolean): JSONObject {
    val o = JSONObject()
    o.put("name", name)
    o.put("status", status)
    o.put("sku", sku)
    if (priceEditable) {
        o.put("regular_price", regularPrice)
        o.put("sale_price", salePrice)
    }
    o.put("manage_stock", manageStock)
    if (manageStock) o.put("stock_quantity", stockQty ?: 0)
    o.put("stock_status", stockStatus)
    o.put("featured", featured)
    o.put("description", description)
    o.put("short_description", shortDescription)
    o.put("weight", weight)
    val arr = JSONArray()
    catIds.forEach { arr.put(JSONObject().put("id", it)) }
    o.put("categories", arr)
    if (isNew) o.put("type", "simple")
    if (imageChanged) {
        val imgs = JSONArray()
        if (image.isNotBlank()) imgs.put(JSONObject().put("src", image))
        o.put("images", imgs)
    }
    return o
}

fun parseOrder(o: JSONObject): WOrder {
    val b = o.optJSONObject("billing")
    val name = if (b != null) "${b.str("first_name")} ${b.str("last_name")}".trim() else ""
    val li = o.optJSONArray("line_items") ?: JSONArray()
    val items = ArrayList<String>()
    for (i in 0 until li.length()) {
        val x = li.getJSONObject(i)
        items.add("${clean(x.str("name"))} × ${x.optInt("quantity")}")
    }
    return WOrder(
        id = o.optLong("id"),
        number = o.str("number").ifBlank { o.optLong("id").toString() },
        status = o.str("status"),
        total = o.str("total"),
        date = orderDate(o.str("date_created")),
        customer = name.ifBlank { "مهمان" },
        phone = b?.str("phone") ?: "",
        items = items,
        payment = o.str("payment_method_title"),
        note = o.str("customer_note")
    )
}

fun orderDate(s: String): String = try {
    val d = java.time.LocalDate.parse(s.take(10))
    val t = if (s.length >= 16) " " + fa(s.substring(11, 16)) else ""
    fmtDay(d.toEpochDay()) + t
} catch (e: Exception) {
    s
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

sealed interface BulkOp
data class BPrice(val sale: Boolean, val mode: Mode, val value: Double, val round: Int) : BulkOp
data class BSalePct(val percent: Double, val round: Int) : BulkOp
data object BClearSale : BulkOp
data class BStatus(val status: String) : BulkOp
data class BStockStatus(val status: String) : BulkOp
data class BQty(val add: Boolean, val value: Int) : BulkOp
data class BCats(val ids: List<Long>, val replace: Boolean) : BulkOp
data class BFeatured(val on: Boolean) : BulkOp
data class BDelete(val permanent: Boolean) : BulkOp

class Change(val id: Long, val name: String, val json: JSONObject, val desc: String)

fun planBulk(p: WProduct, op: BulkOp): Change? {
    val j = JSONObject()
    var desc = ""
    when (op) {
        is BPrice -> {
            if (!p.priceEditable) return null
            val old = (if (op.sale) p.salePrice else p.regularPrice).toDoubleOrNull()
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
                val reg = p.regularPrice.toDoubleOrNull() ?: return null
                if (nv >= reg) return null
            }
            if (old != null && old == nv) return null
            j.put(if (op.sale) "sale_price" else "regular_price", fmtPrice(nv))
            desc = "از ${if (old != null) money(old) else "—"} به ${money(nv)}"
        }
        is BSalePct -> {
            if (!p.priceEditable) return null
            val reg = p.regularPrice.toDoubleOrNull() ?: return null
            val nv = roundStep(reg * (1 - op.percent / 100.0), op.round)
            if (nv <= 0 || nv >= reg) return null
            if (p.salePrice.toDoubleOrNull() == nv) return null
            j.put("sale_price", fmtPrice(nv))
            desc = "حراج ${money(nv)} (از ${money(reg)})"
        }
        is BClearSale -> {
            if (!p.priceEditable || p.salePrice.isBlank()) return null
            j.put("sale_price", "")
            desc = "پایان حراج"
        }
        is BStatus -> {
            if (p.status == op.status) return null
            j.put("status", op.status)
            desc = "وضعیت: ${statusFa(op.status)}"
        }
        is BStockStatus -> {
            if (p.stockStatus == op.status) return null
            j.put("stock_status", op.status)
            desc = "موجودی: ${stockFa(op.status)}"
        }
        is BQty -> {
            val cur = p.stockQty ?: 0
            val nq = (if (op.add) cur + op.value else op.value).coerceAtLeast(0)
            if (p.manageStock && p.stockQty == nq) return null
            j.put("manage_stock", true)
            j.put("stock_quantity", nq)
            desc = "تعداد ${fa(cur)} به ${fa(nq)}"
        }
        is BCats -> {
            val ids = if (op.replace) op.ids else (p.catIds + op.ids).distinct()
            if (ids == p.catIds) return null
            val arr = JSONArray()
            ids.forEach { arr.put(JSONObject().put("id", it)) }
            j.put("categories", arr)
            desc = "${fa(ids.size)} دسته"
        }
        is BFeatured -> {
            if (p.featured == op.on) return null
            j.put("featured", op.on)
            desc = if (op.on) "ویژه" else "حذف از ویژه‌ها"
        }
        is BDelete -> return null
    }
    return Change(p.id, p.name, j, desc)
}

fun oldValue(p: WProduct, k: String): Any = when (k) {
    "regular_price" -> p.regularPrice
    "sale_price" -> p.salePrice
    "status" -> p.status
    "stock_status" -> p.stockStatus
    "manage_stock" -> p.manageStock
    "stock_quantity" -> p.stockQty ?: JSONObject.NULL
    "featured" -> p.featured
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

// ───────────────────────── وضعیت و عملیات فروشگاه ─────────────────────────

class WooState(ctx: Context, private val scope: CoroutineScope) {
    private val sp = ctx.getSharedPreferences("woo", Context.MODE_PRIVATE)
    private var key = ""
    private var secret = ""
    private var cl: WooClient? = null
    private var booted = false
    private var prodJob: Job? = null
    private var orderJob: Job? = null

    var siteUrl by mutableStateOf(sp.getString("url", "") ?: "")
        private set
    var currency by mutableStateOf(sp.getString("cur", "تومان") ?: "تومان")
        private set
    var connected by mutableStateOf(false)
        private set

    var shopTab by mutableIntStateOf(0)

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
    val selected = mutableStateListOf<Long>()
    val categories = mutableStateListOf<WCat>()

    var busy by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var undoList by mutableStateOf<List<Change>>(emptyList())
        private set
    var stats by mutableStateOf<ShopStats?>(null)
        private set

    val orders = mutableStateListOf<WOrder>()
    var ordersLoading by mutableStateOf(false)
    var ordersError by mutableStateOf<String?>(null)
    var orderStatusF by mutableStateOf("any")
    private var ordersPage = 0
    private var ordersPages = 0
    val ordersHasMore: Boolean get() = ordersPage in 1 until ordersPages

    init {
        key = Vault.dec(sp.getString("k", "") ?: "")
        secret = Vault.dec(sp.getString("s", "") ?: "")
        if (siteUrl.isNotEmpty() && key.isNotEmpty() && secret.isNotEmpty()) {
            cl = WooClient(siteUrl, key, secret)
            connected = true
        }
    }

    // اتصال
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

    fun saveCurrency(c: String) {
        currency = c.trim().ifBlank { "تومان" }
        sp.edit().putString("cur", currency).apply()
    }

    fun disconnect() {
        sp.edit().clear().apply()
        key = ""
        secret = ""
        cl = null
        siteUrl = ""
        connected = false
        booted = false
        products.clear()
        orders.clear()
        categories.clear()
        selected.clear()
        stats = null
        undoList = emptyList()
    }

    fun bootstrap() {
        if (booted || !connected) return
        booted = true
        loadCategories()
        loadStats()
        loadProducts(true)
    }

    fun refreshAll() {
        loadCategories()
        loadStats()
        loadProducts(true)
        if (orders.isNotEmpty()) loadOrders(true)
    }

    // محصولات
    fun loadProducts(reset: Boolean = true) {
        val c = cl ?: return
        if (!reset && (loading || page >= pages)) return
        if (reset) {
            prodJob?.cancel()
            selected.clear()
        }
        val next = if (reset) 1 else page + 1
        loading = true
        error = null
        prodJob = scope.launch {
            try {
                val q = LinkedHashMap<String, String>()
                q["per_page"] = "30"
                q["page"] = next.toString()
                q["status"] = statusF
                if (query.isNotBlank()) q["search"] = query.trim()
                if (stockF.isNotEmpty()) q["stock_status"] = stockF
                if (catF != 0L) q["category"] = catF.toString()
                when (sort) {
                    1 -> { q["orderby"] = "title"; q["order"] = "asc" }
                    2 -> { q["orderby"] = "price"; q["order"] = "asc" }
                    3 -> { q["orderby"] = "price"; q["order"] = "desc" }
                    4 -> { q["orderby"] = "popularity"; q["order"] = "desc" }
                    else -> { q["orderby"] = "date"; q["order"] = "desc" }
                }
                val r = c.call("GET", "/products", q)
                if (!r.ok) throw WooException(r.errorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WProduct>()
                for (i in 0 until arr.length()) list.add(parseProduct(arr.getJSONObject(i)))
                if (reset) products.clear()
                products.addAll(list)
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

    fun loadCategories() {
        val c = cl ?: return
        scope.launch {
            try {
                val r = c.call("GET", "/products/categories", mapOf("per_page" to "100", "hide_empty" to "false"))
                if (r.ok) {
                    val arr = JSONArray(r.body)
                    val list = ArrayList<WCat>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(WCat(o.optLong("id"), clean(o.str("name")), o.optInt("count")))
                    }
                    categories.clear()
                    categories.addAll(list)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

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
                val st = coroutineScope {
                    val all = async { count(c, mapOf("status" to "any")) }
                    val pub = async { count(c, mapOf("status" to "publish")) }
                    val dr = async { count(c, mapOf("status" to "draft")) }
                    val oos = async { count(c, mapOf("status" to "any", "stock_status" to "outofstock")) }
                    val sales = async {
                        try { c.call("GET", "/reports/sales", mapOf("period" to "month")) } catch (e: Exception) { null }
                    }
                    val totals = async {
                        try { c.call("GET", "/reports/orders/totals") } catch (e: Exception) { null }
                    }
                    var salesV = 0.0
                    var ordersV = 0
                    val sr = sales.await()
                    if (sr != null && sr.ok) {
                        try {
                            val o = JSONArray(sr.body).getJSONObject(0)
                            salesV = o.str("total_sales").toDoubleOrNull() ?: 0.0
                            ordersV = o.optInt("total_orders")
                        } catch (e: Exception) {
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
                    ShopStats(all.await(), pub.await(), dr.await(), oos.await(), salesV, ordersV, map)
                }
                stats = st
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    fun save(p: WProduct, imageChanged: Boolean, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال ذخیره..."
            try {
                val isNew = p.id == 0L
                val body = p.toJson(isNew, imageChanged).toString()
                val r = if (isNew) c.call("POST", "/products", body = body)
                else c.call("PUT", "/products/${p.id}", body = body)
                if (!r.ok) throw WooException(r.errorText())
                val np = parseProduct(JSONObject(r.body))
                val i = products.indexOfFirst { it.id == np.id }
                if (i >= 0) products[i] = np else products.add(0, np)
                message = if (isNew) "محصول ساخته شد" else "ذخیره شد"
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

    fun deleteProduct(p: WProduct, permanent: Boolean, onDone: (Boolean) -> Unit) {
        val c = cl ?: return
        scope.launch {
            busy = "در حال حذف..."
            try {
                val r = c.call("DELETE", "/products/${p.id}", mapOf("force" to permanent.toString()))
                if (!r.ok) throw WooException(r.errorText())
                products.removeAll { it.id == p.id }
                selected.remove(p.id)
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

    fun toggleSelect(id: Long) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
    }

    fun runBulk(chosen: List<WProduct>, op: BulkOp) {
        val c = cl ?: return
        scope.launch {
            try {
                if (op is BDelete) {
                    val done = ArrayList<Long>()
                    var fail = 0
                    for ((i, p) in chosen.withIndex()) {
                        busy = "حذف ${fa(i + 1)} از ${fa(chosen.size)}"
                        val r = c.call("DELETE", "/products/${p.id}", mapOf("force" to op.permanent.toString()))
                        if (r.ok) done.add(p.id) else fail++
                    }
                    products.removeAll { done.contains(it.id) }
                    selected.clear()
                    undoList = emptyList()
                    message = "${fa(done.size)} حذف شد" + (if (fail > 0) "، ${fa(fail)} ناموفق" else "")
                    loadStats()
                    busy = null
                    return@launch
                }
                val byId = chosen.associateBy { it.id }
                val plans = chosen.mapNotNull { planBulk(it, op) }
                if (plans.isEmpty()) {
                    message = "چیزی برای تغییر نبود"
                    return@launch
                }
                var ok = 0
                var fail = 0
                val reverts = ArrayList<Change>()
                for (chunk in plans.chunked(50)) {
                    busy = "اعمال تغییرات ${fa(ok + fail)} از ${fa(plans.size)}"
                    val arr = JSONArray()
                    chunk.forEach {
                        val j = JSONObject(it.json.toString())
                        j.put("id", it.id)
                        arr.put(j)
                    }
                    val r = c.call("POST", "/products/batch", body = JSONObject().put("update", arr).toString())
                    if (!r.ok) {
                        fail += chunk.size
                        continue
                    }
                    val upd = JSONObject(r.body).optJSONArray("update") ?: JSONArray()
                    for (i in 0 until upd.length()) {
                        val o = upd.getJSONObject(i)
                        if (o.has("error")) {
                            fail++
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
                selected.clear()
                undoList = reverts
                message = "${fa(ok)} محصول بروز شد" + (if (fail > 0) "، ${fa(fail)} ناموفق" else "")
                loadStats()
                busy = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
            }
        }
    }

    fun undo() {
        val c = cl ?: return
        val list = undoList
        if (list.isEmpty()) return
        scope.launch {
            try {
                var ok = 0
                for (chunk in list.chunked(50)) {
                    busy = "بازگردانی..."
                    val arr = JSONArray()
                    chunk.forEach {
                        val j = JSONObject(it.json.toString())
                        j.put("id", it.id)
                        arr.put(j)
                    }
                    val r = c.call("POST", "/products/batch", body = JSONObject().put("update", arr).toString())
                    if (r.ok) {
                        val upd = JSONObject(r.body).optJSONArray("update") ?: JSONArray()
                        for (i in 0 until upd.length()) {
                            val o = upd.getJSONObject(i)
                            if (!o.has("error")) {
                                ok++
                                val np = parseProduct(o)
                                val idx = products.indexOfFirst { it.id == np.id }
                                if (idx >= 0) products[idx] = np
                            }
                        }
                    }
                }
                undoList = emptyList()
                message = "${fa(ok)} محصول به حالت قبل برگشت"
                loadStats()
                busy = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = friendly(e)
                busy = null
            }
        }
    }

    // سفارش‌ها
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
}
