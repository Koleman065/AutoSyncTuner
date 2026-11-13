package com.edk.autosynctuner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Talks to autosync.btdieselworks.com the same way a browser does: it loads the
 * HTML pages and submits the same form fields the website's own buttons send.
 * There is no official API, so if BT DieselWorks redesigns the site, Parser.kt
 * is the file to update.
 */
class AutoSyncClient(context: Context) {

    companion object {
        const val BASE = "https://autosync.btdieselworks.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36"
    }

    private val cookieJar = PersistentCookieJar(context.applicationContext)

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val hasSession: Boolean get() = cookieJar.hasCookie("sessionid")

    // ---- Session ---------------------------------------------------------------------

    /** Signs in with the site's normal login form. Returns true on success. */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val loginUrl = "$BASE/login/"
        val page = get(loginUrl, allowLoginPage = true)
        val doc = Jsoup.parse(page.body, loginUrl)
        val token = Parser.csrfToken(doc) ?: throw SiteChangedException("Login form changed")
        val signInValue = doc.selectFirst("input[type=submit][name=sign_in]")?.attr("value") ?: "Sign In"

        val form = FormBody.Builder()
            .add("csrfmiddlewaretoken", token)
            .add("username", email.trim())
            .add("password", password)
            .add("remember_me", "on")
            .add("sign_in", signInValue)
            .build()
        val req = Request.Builder()
            .url("$loginUrl?next=/users/me/")
            .header("Referer", loginUrl)
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val finalPath = resp.request.url.encodedPath
            val html = resp.body?.string().orEmpty()
            resp.isSuccessful && !finalPath.startsWith("/login") &&
                !Parser.isLoginPage(Jsoup.parse(html))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { http.newCall(Request.Builder().url("$BASE/logout/").build()).execute().close() }
        cookieJar.clear()
    }

    // ---- Reading ---------------------------------------------------------------------

    suspend fun vehicles(): List<Vehicle> = withContext(Dispatchers.IO) {
        val url = "$BASE/users/me/"
        Parser.parseVehicles(get(url).body, url)
    }

    suspend fun modules(vin: String): List<Module> = withContext(Dispatchers.IO) {
        val url = "$BASE/tuning/$vin/"
        Parser.parseModules(get(url).body, url)
    }

    suspend fun setting(vin: String, id: String): SettingPage = withContext(Dispatchers.IO) {
        val url = settingUrl(vin, id)
        Parser.parseSetting(id, get(url).body, url)
    }

    // ---- Writing ---------------------------------------------------------------------

    /**
     * Submits the setting form exactly like pressing Create/Update on the website,
     * then returns the page the site sends back so the caller can confirm it stuck.
     */
    suspend fun save(vin: String, page: SettingPage, value: NewValue, enabled: Boolean): SettingPage =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().add("csrfmiddlewaretoken", page.csrfToken)
            addValueField(form, page, value)
            addEnabledField(form, page, enabled)
            form.add("update", page.submitLabel)
            post(vin, page.id, form.build())
        }

    /** Same as pressing Delete on the website: removes the custom value, back to stock. */
    suspend fun delete(vin: String, page: SettingPage): SettingPage = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("csrfmiddlewaretoken", page.csrfToken)
        addEnabledField(form, page, page.enabled)
        form.add("delete", "Delete")
        post(vin, page.id, form.build())
    }

    private fun addValueField(form: FormBody.Builder, page: SettingPage, value: NewValue) {
        when (val e = page.editor) {
            is Editor.Radio -> {
                val v = (value as? NewValue.Choice)?.value ?: error("Wrong value type")
                require(e.choices.any { it.value == v }) { "Not one of the allowed choices" }
                form.add("custom_value", v)
            }
            is Editor.Number -> {
                val v = (value as? NewValue.Number)?.value ?: error("Wrong value type")
                e.min?.let { require(v >= it) { "Below the allowed minimum ($it)" } }
                e.max?.let { require(v <= it) { "Above the allowed maximum ($it)" } }
                form.add("custom_value", Parser.formatNumber(v))
            }
            is Editor.Enum -> {
                val v = (value as? NewValue.EnumChoice)?.value ?: error("Wrong value type")
                require(e.choices.any { it.value == v }) { "Not one of the allowed choices" }
                form.add("custom_type_enum", v)
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
                form.add("custom_map", Parser.formatNumberMatrix(v))
            }
            is Editor.Unsupported -> error("This setting can't be edited in the app")
        }
    }

    private fun addEnabledField(form: FormBody.Builder, page: SettingPage, enabled: Boolean) {
        when {
            page.enabledIsFixed -> form.add("enabled", "True")
            enabled -> form.add("enabled", "on") // unticked checkboxes are simply not sent
        }
    }

    private fun post(vin: String, id: String, body: FormBody): SettingPage {
        val url = settingUrl(vin, id)
        val req = Request.Builder().url(url).header("Referer", url).post(body).build()
        http.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            checkLoggedIn(resp, html)
            if (!resp.isSuccessful) throw java.io.IOException("Site returned HTTP ${resp.code}")
            val finalUrl = resp.request.url.toString()
            return Parser.parseSetting(id, html, finalUrl)
        }
    }

    // ---- Plumbing --------------------------------------------------------------------

    private data class Page(val body: String, val url: String)

    private fun get(url: String, allowLoginPage: Boolean = false): Page {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            if (!allowLoginPage) checkLoggedIn(resp, html)
            if (!resp.isSuccessful) throw java.io.IOException("Site returned HTTP ${resp.code}")
            return Page(html, resp.request.url.toString())
        }
    }

    private fun checkLoggedIn(resp: Response, html: String) {
        if (resp.request.url.encodedPath.startsWith("/login") || Parser.isLoginPage(Jsoup.parse(html))) {
            throw NotLoggedInException()
        }
    }

    private fun settingUrl(vin: String, id: String) = "$BASE/tuning/$vin/$id/"
}
