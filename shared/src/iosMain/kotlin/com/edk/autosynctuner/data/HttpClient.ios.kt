package com.edk.autosynctuner.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        engine {
            configureSession {
                // Cookies are handled by the app (AutoSyncClient), not by iOS's shared cookie jar.
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
            }
        }
        config()
    }
