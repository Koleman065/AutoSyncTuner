package com.edk.autosynctuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.edk.autosynctuner.data.AutoSyncClient
import com.edk.autosynctuner.data.KeyValueStore
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(client: AutoSyncClient, store: KeyValueStore, onSignedIn: () -> Unit, onTryDemo: () -> Unit) {
    var email by remember { mutableStateOf(store.get("email").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || email.isBlank() || password.isEmpty()) return
        busy = true; error = null
        scope.launch {
            try {
                if (client.login(email, password)) {
                    store.put("email", email.trim())
                    onSignedIn()
                } else {
                    error = "Sign-in failed. Check your email and password."
                }
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                busy = false
            }
        }
    }

    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("AutoSync Tuner", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in with your AutoSync Manager account (autosync.btdieselworks.com).",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            OutlinedButton(onClick = onTryDemo, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Try demo (no account needed)")
            }
            Text(
                "This app is not made by or affiliated with BT DieselWorks. It uses your AutoSync Manager " +
                    "account the same way the website does. " +
                    "Your password is only sent to the AutoSync website and is not saved on the phone. " +
                    "The app stays signed in until you sign out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
