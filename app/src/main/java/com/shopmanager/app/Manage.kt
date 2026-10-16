package com.shopmanager.app

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ───────────────────────── مدل‌ها ─────────────────────────

/** ردیف قابل‌ویرایش ویژگی (برای محصول متغیر) */
data class AttrRow(
    val id: Long = 0,
    val name: String = "",
    val optionsText: String = "",
    val variation: Boolean = true,
    val visible: Boolean = true
)

fun splitOptions(s: String): List<String> =
    s.split('،', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun WAttr.toRow(): AttrRow = AttrRow(id, name, options.joinToString("، "), variation, visible)

fun AttrRow.toAttr(): WAttr = WAttr(id, name.trim(), splitOptions(optionsText), variation, visible)

data class WVariation(
    val id: Long,
    val sku: String,
    val regular: String,
    val sale: String,
    val status: String,
    val manageStock: Boolean,
    val qty: Int?,
    val stockStatus: String,
    val attrs: List<Pair<String, String>>
) {
    val title: String
        get() = attrs.joinToString(" / ") { it.first + ": " + it.second.ifBlank { "همه" } }
            .ifBlank { "تنوع #$id" }
}

fun parseVariation(o: JSONObject): WVariation {
    val at = o.optJSONArray("attributes") ?: JSONArray()
    val list = ArrayList<Pair<String, String>>()
    for (i in 0 until at.length()) {
        val a = at.getJSONObject(i)
        list.add(clean(a.str("name")) to clean(a.str("option")))
    }
    return WVariation(
        id = o.optLong("id"),
        sku = o.str("sku"),
        regular = o.str("regular_price"),
        sale = o.str("sale_price"),
        status = o.str("status").ifBlank { "publish" },
        manageStock = o.optBoolean("manage_stock", false),
        qty = if (o.isNull("stock_quantity")) null else o.optInt("stock_quantity"),
        stockStatus = o.str("stock_status").ifBlank { "instock" },
        attrs = list
    )
}

data class WCustomer(
    val id: Long,
    val first: String,
    val last: String,
    val email: String,
    val username: String,
    val phone: String,
    val city: String,
    val date: String,
    val orders: Int,
    val spent: String
) {
    val fullName: String get() = "$first $last".trim().ifBlank { username.ifBlank { email } }
}

fun parseCustomer(o: JSONObject): WCustomer {
    val b = o.optJSONObject("billing")
    return WCustomer(
        id = o.optLong("id"),
        first = o.str("first_name"),
        last = o.str("last_name"),
        email = o.str("email"),
        username = o.str("username"),
        phone = b?.str("phone") ?: "",
        city = b?.str("city") ?: "",
        date = orderDate(o.str("date_created")),
        orders = o.optInt("orders_count", 0),
        spent = o.str("total_spent")
    )
}

data class WPost(
    val id: Long = 0,
    val title: String = "",
    val status: String = "publish",
    val date: String = "",
    val link: String = "",
    val excerpt: String = "",
    val content: String = ""
)

private fun JSONObject.rendered(k: String): String {
    val x = optJSONObject(k) ?: return ""
    val raw = x.optString("raw", "")
    return if (raw.isNotBlank()) raw else x.optString("rendered", "")
}

fun parsePost(o: JSONObject): WPost = WPost(
    id = o.optLong("id"),
    title = clean(o.rendered("title")),
    status = o.str("status").ifBlank { "publish" },
    date = orderDate(o.str("date")),
    link = o.str("link"),
    excerpt = clean(o.rendered("excerpt")),
    content = o.rendered("content")
)

fun postStatusFa(s: String): String = when (s) {
    "publish" -> "منتشر شده"
    "draft" -> "پیش‌نویس"
    "pending" -> "در انتظار بررسی"
    "private" -> "خصوصی"
    "future" -> "زمان‌بندی شده"
    "trash" -> "زباله"
    else -> s
}

// ───────────────────────── کلاینت REST وردپرس (برای مقاله‌ها) ─────────────────────────

class WpClient(site: String, private val user: String, private val pass: String) {
    private val base = WooClient.normalize(site) + "/wp-json/wp/v2"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun call(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): ApiResult = withContext(Dispatchers.IO) {
        val qs = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val conn = URL(base + path + qs).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 40000
            conn.setRequestProperty("Accept", "application/json")
            if (user.isNotBlank() && pass.isNotBlank()) {
                val cred = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
            ApiResult(code, text, total, pages)
        } finally {
            conn.disconnect()
        }
    }
}

fun ApiResult.wpErrorText(): String {
    val msg = try {
        JSONObject(body).optString("message", "")
    } catch (e: Exception) {
        ""
    }
    return when {
        code == 401 || code == 403 ->
            "دسترسی رد شد؛ نام کاربری و Application Password را بررسی کن (بعضی هاست‌ها هدر Authorization را حذف می‌کنند)." +
                (if (msg.isNotBlank()) "\n$msg" else "")
        code == 404 && msg.isBlank() -> "REST وردپرس پیدا نشد؛ آدرس سایت را بررسی کن"
        msg.isNotBlank() -> clean(msg)
        else -> "خطای سرور ($code)"
    }
}

// ───────────────────────── وضعیت و عملیات مدیریتی ─────────────────────────

class ManageState(ctx: Context, private val scope: CoroutineScope, private val w: WooState) {
    private val sp = ctx.getSharedPreferences("wp", Context.MODE_PRIVATE)

    // ---- ابزار مشترک ----
    private fun launchOp(label: String, onDone: (Boolean) -> Unit, block: suspend () -> String?) {
        scope.launch {
            w.busy = label
            try {
                val msg = block()
                if (msg != null) w.message = msg
                w.busy = null
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                w.message = friendly(e)
                w.busy = null
                onDone(false)
            }
        }
    }

    private suspend fun req(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null
    ): String {
        val c = w.api ?: throw WooException("به فروشگاه وصل نیستی")
        val r = c.call(method, path, query, body?.toString())
        if (!r.ok) throw WooException(r.errorText())
        return r.body
    }

    fun clear() {
        variations.clear()
        customers.clear()
        custOrders.clear()
        posts.clear()
        sp.edit().clear().apply()
        wpUser = ""
        wpPass = ""
    }

    // ---- دسته‌بندی‌ها ----
    fun saveCategory(id: Long, name: String, parent: Long, desc: String, onDone: (Boolean) -> Unit) {
        launchOp("در حال ذخیره...", onDone) {
            val b = JSONObject().put("name", name).put("parent", parent).put("description", desc)
            if (id == 0L) req("POST", "/products/categories", body = b)
            else req("PUT", "/products/categories/$id", body = b)
            w.loadCategories()
            if (id == 0L) "دسته ساخته شد" else "دسته ذخیره شد"
        }
    }

    fun deleteCategory(id: Long, onDone: (Boolean) -> Unit) {
        launchOp("در حال حذف...", onDone) {
            req("DELETE", "/products/categories/$id", mapOf("force" to "true"))
            w.loadCategories()
            if (w.catF == id) w.catF = 0L
            w.loadProducts(true)
            "دسته حذف شد"
        }
    }

    // ---- تنوع‌های محصول متغیر ----
    val variations = mutableStateListOf<WVariation>()
    var varLoading by mutableStateOf(false)
        private set
    private var varJob: Job? = null

    fun loadVariations(pid: Long) {
        varJob?.cancel()
        variations.clear()
        varJob = scope.launch {
            varLoading = true
            try {
                val out = ArrayList<WVariation>()
                var page = 1
                while (page <= 5) {
                    val body = req(
                        "GET", "/products/$pid/variations",
                        mapOf("per_page" to "100", "page" to page.toString())
                    )
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) out.add(parseVariation(arr.getJSONObject(i)))
                    if (arr.length() < 100) break
                    page++
                }
                variations.clear()
                variations.addAll(out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                w.message = friendly(e)
            }
            varLoading = false
        }
    }

    fun saveVariation(pid: Long, v: WVariation, onDone: (Boolean) -> Unit) {
        launchOp("در حال ذخیره...", onDone) {
            val b = JSONObject()
                .put("regular_price", v.regular)
                .put("sale_price", v.sale)
                .put("sku", v.sku)
                .put("status", v.status)
                .put("manage_stock", v.manageStock)
                .put("stock_status", v.stockStatus)
            if (v.manageStock) b.put("stock_quantity", v.qty ?: 0)
            val body = req("PUT", "/products/$pid/variations/${v.id}", body = b)
            val nv = parseVariation(JSONObject(body))
            val i = variations.indexOfFirst { it.id == nv.id }
            if (i >= 0) variations[i] = nv
            "تنوع ذخیره شد"
        }
    }

    fun deleteVariation(pid: Long, id: Long, onDone: (Boolean) -> Unit) {
        launchOp("در حال حذف...", onDone) {
            req("DELETE", "/products/$pid/variations/$id", mapOf("force" to "true"))
            variations.removeAll { it.id == id }
            "تنوع حذف شد"
        }
    }

    /** اول ویژگی‌ها روی محصول ذخیره می‌شود، بعد همهٔ ترکیب‌های جدید ساخته می‌شود. */
    fun generateVariations(pid: Long, attrs: List<WAttr>, price: String, onDone: (Boolean) -> Unit) {
        launchOp("در حال ساخت تنوع‌ها...", onDone) {
            val va = attrs.filter { it.variation && it.name.isNotBlank() && it.options.isNotEmpty() }
            if (va.isEmpty()) throw WooException("حداقل یک ویژگی با گزینه‌هایش را وارد کن")
            // ذخیرهٔ ویژگی‌ها روی محصول
            req("PUT", "/products/$pid", body = JSONObject().put("attributes", attrsToJson(attrs.filter { it.name.isNotBlank() && it.options.isNotEmpty() })))
            // همهٔ ترکیب‌ها
            var combos: List<List<Pair<WAttr, String>>> = listOf(emptyList())
            for (a in va) {
                combos = combos.flatMap { prefix -> a.options.map { opt -> prefix + (a to opt) } }
            }
            val existing = HashSet<String>()
            for (v in variations) {
                existing.add(v.attrs.map { it.first + "=" + it.second }.sorted().joinToString("|"))
            }
            val fresh = combos.filter { c ->
                val key = c.map { it.first.name + "=" + it.second }.sorted().joinToString("|")
                !existing.contains(key)
            }
            val toCreate = fresh.take(100)
            if (toCreate.isEmpty()) {
                "همهٔ ترکیب‌ها از قبل وجود دارند"
            } else {
                val arr = JSONArray()
                for (cmb in toCreate) {
                    val o = JSONObject().put("regular_price", en(price).trim())
                    val at = JSONArray()
                    for ((a, opt) in cmb) {
                        val x = JSONObject().put("option", opt)
                        if (a.id > 0) x.put("id", a.id) else x.put("name", a.name)
                        at.put(x)
                    }
                    o.put("attributes", at)
                    arr.put(o)
                }
                req("POST", "/products/$pid/variations/batch", body = JSONObject().put("create", arr))
                val left = fresh.size - toCreate.size
                loadVariations(pid)
                "${fa(toCreate.size)} تنوع ساخته شد" + if (left > 0) " (${fa(left)} ترکیب دیگر باقی ماند؛ دوباره بساز)" else ""
            }
        }
    }

    /** قیمت اصلی (و در صورت پر بودن، قیمت حراج) همهٔ تنوع‌ها */
    fun setAllVariationPrices(pid: Long, regular: String, sale: String, onDone: (Boolean) -> Unit) {
        launchOp("در حال بروزرسانی قیمت‌ها...", onDone) {
            val ids = variations.map { it.id }
            val r = en(regular).trim()
            val s = en(sale).trim()
            if (ids.isEmpty()) throw WooException("تنوعی برای تغییر نیست")
            for (chunk in ids.chunked(100)) {
                val arr = JSONArray()
                for (id in chunk) {
                    val o = JSONObject().put("id", id)
                    if (r.isNotEmpty()) o.put("regular_price", r)
                    if (s.isNotEmpty()) o.put("sale_price", s)
                    arr.put(o)
                }
                req("POST", "/products/$pid/variations/batch", body = JSONObject().put("update", arr))
            }
            loadVariations(pid)
            "قیمت همهٔ تنوع‌ها بروز شد"
        }
    }

    // ---- مشتری‌ها (کاربران فروشگاه) ----
    val customers = mutableStateListOf<WCustomer>()
    var custLoading by mutableStateOf(false)
        private set
    var custError by mutableStateOf<String?>(null)
        private set
    var custQuery by mutableStateOf("")
    var custTotal by mutableIntStateOf(0)
        private set
    private var custPage = 0
    private var custPages = 0
    private var custJob: Job? = null
    val custHasMore: Boolean get() = custPage in 1 until custPages

    fun loadCustomers(reset: Boolean = true) {
        if (!reset && (custLoading || !custHasMore)) return
        if (reset) custJob?.cancel()
        val next = if (reset) 1 else custPage + 1
        custLoading = true
        custError = null
        custJob = scope.launch {
            try {
                val c = w.api ?: throw WooException("به فروشگاه وصل نیستی")
                val q = LinkedHashMap<String, String>()
                q["per_page"] = "20"
                q["page"] = next.toString()
                q["role"] = "all"
                q["orderby"] = "registered_date"
                q["order"] = "desc"
                if (custQuery.isNotBlank()) q["search"] = custQuery.trim()
                val r = c.call("GET", "/customers", q)
                if (!r.ok) throw WooException(r.errorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WCustomer>()
                for (i in 0 until arr.length()) list.add(parseCustomer(arr.getJSONObject(i)))
                if (reset) customers.clear()
                customers.addAll(list)
                custPage = next
                custPages = r.pages
                custTotal = r.total
                custLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                custError = friendly(e)
                custLoading = false
            }
        }
    }

    fun saveCustomer(
        c: WCustomer,
        first: String,
        last: String,
        email: String,
        phone: String,
        username: String,
        password: String,
        onDone: (Boolean) -> Unit
    ) {
        launchOp("در حال ذخیره...", onDone) {
            val b = JSONObject()
                .put("first_name", first)
                .put("last_name", last)
                .put("email", email)
                .put("billing", JSONObject().put("first_name", first).put("last_name", last).put("phone", phone))
            if (c.id == 0L) {
                if (username.isNotBlank()) b.put("username", username)
                if (password.isNotBlank()) b.put("password", password)
                req("POST", "/customers", body = b)
            } else {
                req("PUT", "/customers/${c.id}", body = b)
            }
            loadCustomers(true)
            if (c.id == 0L) "مشتری ساخته شد" else "مشتری ذخیره شد"
        }
    }

    fun deleteCustomer(id: Long, onDone: (Boolean) -> Unit) {
        launchOp("در حال حذف...", onDone) {
            req("DELETE", "/customers/$id", mapOf("force" to "true"))
            customers.removeAll { it.id == id }
            "مشتری حذف شد"
        }
    }

    val custOrders = mutableStateListOf<WOrder>()
    var custOrdersLoading by mutableStateOf(false)
        private set

    fun loadCustomerOrders(id: Long) {
        custOrders.clear()
        scope.launch {
            custOrdersLoading = true
            try {
                val body = req("GET", "/orders", mapOf("customer" to id.toString(), "per_page" to "10"))
                val arr = JSONArray(body)
                val list = ArrayList<WOrder>()
                for (i in 0 until arr.length()) list.add(parseOrder(arr.getJSONObject(i)))
                custOrders.clear()
                custOrders.addAll(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                w.message = friendly(e)
            }
            custOrdersLoading = false
        }
    }

    // ---- مقاله‌ها (نوشته‌های وردپرس) ----
    var wpUser by mutableStateOf(sp.getString("user", "") ?: "")
        private set
    private var wpPass: String = Vault.dec(sp.getString("pass", "") ?: "")
    val wpReady: Boolean get() = wpUser.isNotBlank() && wpPass.isNotBlank()

    val posts = mutableStateListOf<WPost>()
    var postsLoading by mutableStateOf(false)
        private set
    var postsError by mutableStateOf<String?>(null)
        private set
    var postQuery by mutableStateOf("")
    var postStatus by mutableStateOf("any")
    var postsTotal by mutableIntStateOf(0)
        private set
    private var postPage = 0
    private var postPages = 0
    private var postJob: Job? = null
    val postsHasMore: Boolean get() = postPage in 1 until postPages

    private fun wpClient(): WpClient =
        if (wpReady) WpClient(w.siteUrl, wpUser, wpPass) else WpClient(w.siteUrl, "", "")

    private suspend fun wpReq(method: String, path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null): String {
        val r = wpClient().call(method, path, query, body?.toString())
        if (!r.ok) throw WooException(r.wpErrorText())
        return r.body
    }

    fun saveWpLogin(user: String, pass: String, onResult: (String?) -> Unit) {
        val u = user.trim()
        val p = pass.trim()
        if (u.isEmpty() || p.isEmpty()) {
            onResult("نام کاربری و Application Password را وارد کن")
            return
        }
        scope.launch {
            try {
                val r = WpClient(w.siteUrl, u, p).call("GET", "/users/me", mapOf("context" to "edit"))
                if (!r.ok) {
                    onResult(r.wpErrorText())
                    return@launch
                }
                sp.edit().putString("user", u).putString("pass", Vault.enc(p)).apply()
                wpPass = p
                wpUser = u
                loadPosts(true)
                onResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(friendly(e))
            }
        }
    }

    fun clearWpLogin() {
        sp.edit().clear().apply()
        wpUser = ""
        wpPass = ""
        postStatus = "any"
        loadPosts(true)
    }

    fun loadPosts(reset: Boolean = true) {
        if (!w.connected) return
        if (!reset && (postsLoading || !postsHasMore)) return
        if (reset) postJob?.cancel()
        val next = if (reset) 1 else postPage + 1
        postsLoading = true
        postsError = null
        postJob = scope.launch {
            try {
                val q = LinkedHashMap<String, String>()
                q["per_page"] = "15"
                q["page"] = next.toString()
                if (wpReady) {
                    q["context"] = "edit"
                    q["status"] = postStatus
                } else {
                    q["status"] = "publish"
                }
                if (postQuery.isNotBlank()) q["search"] = postQuery.trim()
                val r = wpClient().call("GET", "/posts", q)
                if (!r.ok) throw WooException(r.wpErrorText())
                val arr = JSONArray(r.body)
                val list = ArrayList<WPost>()
                for (i in 0 until arr.length()) list.add(parsePost(arr.getJSONObject(i)))
                if (reset) posts.clear()
                posts.addAll(list)
                postPage = next
                postPages = r.pages
                postsTotal = r.total
                postsLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                postsError = friendly(e)
                postsLoading = false
            }
        }
    }

    fun savePost(p: WPost, onDone: (Boolean) -> Unit) {
        launchOp("در حال ذخیره مقاله...", onDone) {
            if (!wpReady) throw WooException("اول وارد حساب وردپرس شو")
            val b = JSONObject()
                .put("title", p.title)
                .put("content", p.content)
                .put("excerpt", p.excerpt)
                .put("status", p.status)
            if (p.id == 0L) wpReq("POST", "/posts", body = b)
            else wpReq("POST", "/posts/${p.id}", body = b)
            loadPosts(true)
            if (p.id == 0L) "مقاله ساخته شد" else "مقاله ذخیره شد"
        }
    }

    fun deletePost(id: Long, permanent: Boolean, onDone: (Boolean) -> Unit) {
        launchOp("در حال حذف...", onDone) {
            if (!wpReady) throw WooException("اول وارد حساب وردپرس شو")
            wpReq("DELETE", "/posts/$id", mapOf("force" to permanent.toString()))
            posts.removeAll { it.id == id }
            if (permanent) "مقاله برای همیشه حذف شد" else "مقاله به زباله منتقل شد"
        }
    }
}
