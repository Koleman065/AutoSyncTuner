package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.NotLoggedInException
import com.edk.autosynctuner.data.NetworkException
import com.edk.autosynctuner.data.SiteChangedException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val error: Throwable) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val reload: () -> Unit)

/** Runs [block] once (and again on reload); sends you to sign-in if the session expired. */
@Composable
fun <T> rememberLoad(vararg keys: Any, onSignedOut: () -> Unit, block: suspend (refresh: Boolean) -> T): Loader<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        state = Load.Loading
        state = try {
            Load.Ready(block(attempt > 0))
        } catch (e: NotLoggedInException) {
            onSignedOut(); Load.Failed(e)
        } catch (e: Exception) {
            Load.Failed(e)
        }
    }
    return Loader(state) { attempt++ }
}

fun friendlyError(e: Throwable): String = when (e) {
    is NotLoggedInException -> "Your sign-in expired. Please sign in again."
    is SiteChangedException -> "The AutoSync website looks different than expected (${e.message}). The app may need an update."
    is NetworkException -> "${e.message} Check your connection and try again."
    is IllegalArgumentException -> e.message ?: "That value isn't allowed."
    else -> e.message ?: (e::class.simpleName ?: "Unexpected error")
}

@Composable
fun <T> LoadView(loader: Loader<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Failed -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(friendlyError(s.error), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = loader.reload) { Text("Try again") }
        }
        is Load.Ready -> content(s.value)
    }
}
