package com.edk.autosynctuner.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.Url
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Platform HTTP engine: OkHttp on Android, NSURLSession on iOS. */
expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * Talks to autosync.btdieselworks.com the same way a browser does: it loads the
 * HTML pages and submits the same form fields the website's own buttons send.
 * There is no official API, so if BT DieselWorks redesigns the site, Parser.kt
 * is the file to update.
 */
class AutoSyncClient(private val store: KeyValueStore) {

    companion object {
        const val BASE = "https://autosync.btdieselworks.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36 AutoSyncTuner"
    }

    private val cookies = StoredCookies(store)

    private val http = createHttpClient {
        followRedirects = false // handled below so cookies from every hop are kept
        expectSuccess = false
        install(HttpCookies) { storage = cookies }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 45_000
        }
    }

    val hasSession: Boolean get() = cookies.has("sessionid")

    // ---- Session ---------------------------------------------------------------------

    /** Signs in with the site's normal login form. Returns true on success. */
    suspend fun login(email: String, password: String): Boolean {
        val loginUrl = "$BASE/login/"
        val page = send(HttpMethod.Get, loginUrl)
        val doc = parse { Parser.parse(page.body, loginUrl) }
        val token = Parser.csrfToken(doc) ?: throw SiteChangedException("Login form changed")
        val signIn = doc.selectFirst("input[type=submit][name=sign_in]")?.attr("value") ?: "Sign In"

        val result = send(
            HttpMethod.Post, "$loginUrl?next=/users/me/", referer = loginUrl,
            form = form {
                append("csrfmiddlewaretoken", token)
                append("username", email.trim())
                append("password", password)
                append("remember_me", "on")
                append("sign_in", signIn)
            },
        )
        return result.status in 200..299 &&
            !Url(result.url).encodedPath.startsWith("/login") &&
            !parse { Parser.isLoginPage(Parser.parse(result.body)) }
    }

    suspend fun logout() {
        runCatching { send(HttpMethod.Get, "$BASE/logout/") }
        cookies.clear()
        clearCache()
    }

    // ---- Reading ---------------------------------------------------------------------

    // The vehicle list and settings tree rarely change, so they're kept in memory
    // (pull Reload to refresh). Individual settings are always loaded fresh.
    private var profileCache: Profile? = null
    private val vehicleCache = mutableMapOf<String, VehicleSettings>()

    fun clearCache() {
        profileCache = null
        vehicleCache.clear()
    }

    suspend fun profile(refresh: Boolean = false): Profile {
        if (!refresh) profileCache?.let { return it }
        val url = "$BASE/users/me/"
        val page = get(url)
        return parse { Parser.parseProfile(page.body, url) }.also { profileCache = it }
    }

    /** The vehicle last opened on this phone, so the app can go straight back to it. */
    var lastVin: String?
        get() = store.get("lastVin")
        set(value) = store.put("lastVin", value)

    suspend fun vehicle(vin: String, refresh: Boolean = false): VehicleSettings {
        if (!refresh) vehicleCache[vin]?.let { return it }
        val url = "$BASE/tuning/$vin/"
        val page = get(url)
        return parse {
            VehicleSettings(Parser.parseModules(page.body, url), Parser.parseLicenses(page.body, url))
        }.also { vehicleCache[vin] = it }
    }

    suspend fun setting(vin: String, id: String): SettingPage {
        val url = settingUrl(vin, id)
        val page = get(url)
        return parse { Parser.parseSetting(id, page.body, url) }
    }

    // ---- Writing ---------------------------------------------------------------------

    /**
     * Submits the setting form exactly like pressing Create/Update on the website,
     * then returns the page the site sends back so the caller can confirm it stuck.
     */
    suspend fun save(vin: String, page: SettingPage, value: NewValue, enabled: Boolean): SettingPage {
        val fields = form {
            append("csrfmiddlewaretoken", page.csrfToken)
            addValueField(this, page, value)
            addEnabledField(this, page, enabled)
            append("update", page.submitLabel)
        }
        return post(vin, page.id, fields)
    }

    /** Same as pressing Delete on the website: removes the custom value, back to stock. */
    suspend fun delete(vin: String, page: SettingPage): SettingPage {
        val fields = form {
            append("csrfmiddlewaretoken", page.csrfToken)
            addEnabledField(this, page, page.enabled)
            append("delete", "Delete")
        }
        return post(vin, page.id, fields)
    }

    private fun addValueField(form: ParametersBuilder, page: SettingPage, value: NewValue) {
        when (val e = page.editor) {
            is Editor.Radio -> {
                val v = (value as? NewValue.Choice)?.value ?: error("Wrong value type")
                require(e.choices.any { it.value == v }) { "Not one of the allowed choices" }
                form.append("custom_value", v)
            }
            is Editor.Number -> {
                val v = (value as? NewValue.Number)?.value ?: error("Wrong value type")
                e.min?.let { require(v >= it) { "Below the allowed minimum ($it)" } }
                e.max?.let { require(v <= it) { "Above the allowed maximum ($it)" } }
                form.append("custom_value", Parser.formatNumber(v))
            }
            is Editor.Enum -> {
                val v = (value as? NewValue.EnumChoice)?.value ?: error("Wrong value type")
                require(e.choices.any { it.value == v }) { "Not one of the allowed choices" }
                form.append("custom_type_enum", v)
            }
            is Editor.Grid -> {
                val v = (value as? NewValue.Grid)?.values ?: error("Wrong value type")
                require(v.size == e.values.size && v.indices.all { v[it].size == e.values[it].size }) {
                    "Table size changed"
                }
                v.flatten().forEach { cell ->
                    e.min?.let { require(cell >= it) { "A cell is below the minimum ($it)" } }
                    e.max?.let { require(cell <= it) { "A cell is above the maximum ($it)" } }
                }
                form.append("custom_map", Parser.formatNumberMatrix(v))
            }
            is Editor.Unsupported -> error("This setting can't be edited in the app")
        }
    }

    private fun addEnabledField(form: ParametersBuilder, page: SettingPage, enabled: Boolean) {
        when {
            page.enabledIsFixed -> form.append("enabled", "True")
            enabled -> form.append("enabled", "on") // unticked checkboxes are simply not sent
        }
    }

    private suspend fun post(vin: String, id: String, fields: Parameters): SettingPage {
        val url = settingUrl(vin, id)
        val result = send(HttpMethod.Post, url, form = fields, referer = url)
        checkLoggedIn(result)
        if (result.status !in 200..299) throw NetworkException("The website returned an error (HTTP ${result.status}).")
        return parse { Parser.parseSetting(id, result.body, result.url) }
    }

    // ---- Plumbing --------------------------------------------------------------------

    private class Fetched(val body: String, val url: String, val status: Int)

    private suspend fun get(url: String): Fetched {
        val result = send(HttpMethod.Get, url)
        checkLoggedIn(result)
        if (result.status !in 200..299) throw NetworkException("The website returned an error (HTTP ${result.status}).")
        return result
    }

    /** One request, following redirects by hand (POST → 302 → GET, like a browser). */
    private suspend fun send(
        method: HttpMethod,
        url: String,
        form: Parameters? = null,
        referer: String? = null,
    ): Fetched {
        var currentUrl = url
        var currentMethod = method
        var currentForm = form
        try {
            repeat(10) {
                val resp = http.request(currentUrl) {
                    this.method = currentMethod
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    referer?.let { header(HttpHeaders.Referrer, it) }
                    currentForm?.let { setBody(FormDataContent(it)) }
                }
                val code = resp.status.value
                val location = resp.headers[HttpHeaders.Location]
                if (code in 300..399 && location != null) {
                    currentUrl = resolve(currentUrl, location)
                    if (code in 301..303) {
                        currentMethod = HttpMethod.Get
                        currentForm = null
                    }
                } else {
                    return Fetched(resp.bodyAsText(), currentUrl, code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Couldn't reach the AutoSync website.", e)
        }
        throw NetworkException("Too many redirects from the AutoSync website.")
    }

    private suspend fun checkLoggedIn(result: Fetched) {
        if (Url(result.url).encodedPath.startsWith("/login") ||
            parse { Parser.isLoginPage(Parser.parse(result.body)) }
        ) {
            throw NotLoggedInException()
        }
    }

    private fun resolve(current: String, location: String): String = when {
        location.startsWith("http://") || location.startsWith("https://") -> location
        location.startsWith("/") -> BASE + location
        else -> current.substringBeforeLast('/') + "/" + location
    }

    private suspend fun <T> parse(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    private fun form(build: ParametersBuilder.() -> Unit): Parameters = Parameters.build(build)

    private fun settingUrl(vin: String, id: String) = "$BASE/tuning/$vin/$id/"
}

/**
 * Keeps the site's cookies (including the sign-in session) on the device so you stay
 * signed in between launches. The password itself is never stored.
 * This app only talks to one website, so cookies are simply keyed by name.
 */
private class StoredCookies(private val store: KeyValueStore) : CookiesStorage {
    private data class Entry(val name: String, val value: String, val expiresAt: Long?)

    private val lock = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    init {
        store.get("cookies")?.lines()?.forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 3) {
                val exp = parts[2].toLongOrNull()
                if (exp == null || exp > getTimeMillis()) entries[parts[0]] = Entry(parts[0], parts[1], exp)
            }
        }
    }

    fun has(name: String): Boolean = entries[name]?.let { it.value.isNotEmpty() && !it.expired() } == true

    override suspend fun get(requestUrl: Url): List<Cookie> = lock.withLock {
        entries.values.removeAll { it.expired() }
        entries.values.map { Cookie(it.name, it.value) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = lock.withLock {
        // Django deletes a cookie by sending it empty with an expiry date in the past.
        val maxAge: Int? = cookie.maxAge
        val expiresAt = if (maxAge != null && maxAge > 0) getTimeMillis() + maxAge * 1000L else cookie.expires?.timestamp
        if (cookie.value.isEmpty() || (expiresAt != null && expiresAt <= getTimeMillis())) {
            entries.remove(cookie.name)
        } else {
            entries[cookie.name] = Entry(cookie.name, cookie.value, expiresAt)
        }
        persist()
    }

    fun clear() {
        entries.clear()
        store.put("cookies", null)
    }

    override fun close() {}

    private fun Entry.expired() = expiresAt != null && expiresAt <= getTimeMillis()

    private fun persist() {
        store.put("cookies", entries.values.joinToString("\n") { "${it.name}\t${it.value}\t${it.expiresAt ?: ""}" })
    }
}
