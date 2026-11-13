package com.edk.autosynctuner.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps the site's session cookie in app-private storage so you stay signed in
 * between launches (like ticking "Remember me" on the website). The password
 * itself is never stored.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        val now = System.currentTimeMillis()
        prefs.all.forEach { (key, value) ->
            val raw = value as? String ?: return@forEach
            val url = HttpUrl.Builder().scheme("https").host(raw.substringBefore('|')).build()
            Cookie.parse(url, raw.substringAfter('|'))
                ?.takeIf { it.expiresAt > now }
                ?.let { cookies[key] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val edit = prefs.edit()
        for (c in cookies) {
            val key = "${c.domain}|${c.path}|${c.name}"
            if (c.expiresAt < System.currentTimeMillis()) {
                this.cookies.remove(key); edit.remove(key)
            } else {
                this.cookies[key] = c
                // Browser-session cookies are kept too, until you sign out in the app.
                edit.putString(key, "${c.domain}|${c}")
            }
        }
        edit.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().clear().apply()
    }

    @Synchronized
    fun hasCookie(name: String): Boolean = cookies.values.any { it.name == name }
}
