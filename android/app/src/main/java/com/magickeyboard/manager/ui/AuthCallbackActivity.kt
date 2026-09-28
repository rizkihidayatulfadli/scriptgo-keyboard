package com.magickeyboard.manager.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles `scriptgo://login-callback` from Google sign-in in the browser and from the link in the
 * sign-in email: exchanges the PKCE code for a session, then returns to the Akun tab.
 */
class AuthCallbackActivity : ComponentActivity() {
    private var url by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a configuration change the exchange already ran (a PKCE code works once).
        if (savedInstanceState == null) url = intent?.dataString
        setContent {
            ManagerTheme {
                var error by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(url) {
                    val link = url ?: return@LaunchedEffect
                    url = null
                    val sync = SupabaseSync.get(this@AuthCallbackActivity)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            sync.completeCallback(link)
                            afterSignIn(sync)
                        }
                    }
                    result.onSuccess { openAccount() }.onFailure { error = it.message ?: this@AuthCallbackActivity.getString(R.string.sg_sign_in_failed) }
                }
                Box(Modifier.fillMaxSize().background(ScriptGo.colors.bg).padding(24.dp), contentAlignment = Alignment.Center) {
                    BCard(Modifier.fillMaxWidth(), radius = 28.dp) {
                        val message = error
                        if (message == null) {
                            CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.CenterHorizontally), color = ScriptGo.colors.accentSolid)
                            Text(stringResource(R.string.sg_finishing_sign_in), style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            Text(stringResource(R.string.sg_sign_in_not_done), style = MaterialTheme.typography.titleLarge)
                            Text(message, style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.muted)
                            Button(onClick = { openAccount() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.sg_back_to_app)) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        url = intent.dataString
    }

    private fun openAccount() {
        startActivity(
            Intent(this, ManagerActivity::class.java)
                .putExtra(ManagerActivity.EXTRA_SIGNED_IN, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
