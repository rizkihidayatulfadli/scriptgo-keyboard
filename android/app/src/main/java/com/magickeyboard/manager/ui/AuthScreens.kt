package com.magickeyboard.manager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.PasskeyCeremony
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Google's multicolour "G", drawn as Google's sign-in branding guidelines require. */
private val GoogleLogo: ImageVector by lazy {
    val builder = ImageVector.Builder(name = "Google", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 48f, viewportHeight = 48f)
    listOf(
        0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
        0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
        0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
        0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
    ).forEach { (color, path) -> builder.addPath(pathData = addPathNodes(path), fill = SolidColor(Color(color))) }
    builder.build()
}

/** White stringResource(R.string.sg_continue_google) button (logo on a light surface, per Google's guidelines). */
@Composable
fun GoogleButton(onClick: () -> Unit, enabled: Boolean = true) {
    val c = ScriptGo.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, c.line),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF0E1116)),
    ) {
        Icon(GoogleLogo, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.sg_continue_google))
    }
}

/** A thin rule with "atau" in the middle. */
@Composable
fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = ScriptGo.colors.line)
        Text(stringResource(R.string.sg_or), style = MaterialTheme.typography.labelLarge, color = ScriptGo.colors.muted)
        HorizontalDivider(Modifier.weight(1f), color = ScriptGo.colors.line)
    }
}

/** Opens Google sign-in in the browser; it returns through AuthCallbackActivity. */
@Composable
fun rememberGoogleSignIn(onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember<() -> Unit> {
        {
            try {
                val url = SupabaseSync.get(context).googleSignInUrl()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                onError(context.getString(R.string.sg_no_browser))
            }
        }
    }
}

/**
 * Signs in with a passkey (Android 9+); [onSignedIn] runs after success, [onError] gets a message.
 * Returns null when this phone cannot use passkeys, so the button is not shown.
 */
@Composable
fun rememberPasskeySignIn(onSignedIn: () -> Unit, onError: (String) -> Unit): (() -> Unit)? {
    if (!PasskeyCeremony.supported) return null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember<() -> Unit> {
        {
            scope.launch {
                try {
                    val sync = SupabaseSync.get(context)
                    PasskeyCeremony.signIn(context, sync)
                    withContext(Dispatchers.IO) { afterSignIn(sync) }
                    onSignedIn()
                } catch (_: PasskeyCeremony.Cancelled) {
                } catch (e: Exception) {
                    onError(e.message ?: context.getString(R.string.sg_err_passkey_failed))
                }
            }
        }
    }
}

/** A quieter button under the main sign-in choices: sign in with a saved passkey. */
@Composable
fun PasskeyButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(ScriptGoIcons.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sg_sign_in_passkey))
    }
}

/** Sign-in choices for the Akun tab when signed out (mockup 2a). */
@Composable
fun SignInCard(onEmail: () -> Unit, message: String?, onError: (String) -> Unit, onSignedIn: () -> Unit = {}) {
    val google = rememberGoogleSignIn(onError)
    val passkey = rememberPasskeySignIn(onSignedIn, onError)
    BCard(Modifier.fillMaxWidth(), radius = 28.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sg_sign_in_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoTip(stringResource(R.string.sg_sign_in_title), stringResource(R.string.sg_sign_in_why))
        }
        Text(
            stringResource(R.string.sg_sign_in_short),
            style = MaterialTheme.typography.bodyMedium,
            color = ScriptGo.colors.muted,
        )
        Spacer(Modifier.height(4.dp))
        GoogleButton(onClick = google)
        OrDivider()
        Button(onClick = onEmail, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(ScriptGoIcons.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sg_continue_email))
        }
        passkey?.let { PasskeyButton(onClick = it) }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.danger) }
        Text(
            stringResource(R.string.sg_sign_in_terms),
            style = MaterialTheme.typography.bodySmall,
            color = ScriptGo.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Page frame for the sign-in steps: back button, title, body, and a bottom action. */
@Composable
private fun AuthPage(
    title: String,
    onBack: () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ScriptGo.colors.bg).imePadding()) {
        BackTopBar(title = "", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            content()
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = bottom,
        )
    }
}

/**
 * Mockup 2b: one email field; sends one email with a code and a sign-in link. Accounts that set a
 * password (Akun → Kata sandi) can switch to email and password instead.
 */
@Composable
fun EmailSignInScreen(onBack: () -> Unit, onSent: (String) -> Unit, onSignedIn: () -> Unit = {}) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usePassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val valid = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(address) && (!usePassword || password.isNotEmpty())
    val passkey = rememberPasskeySignIn(onSignedIn) { error = it }

    fun send() {
        if (!valid || busy) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (usePassword) {
                        sync.signInWithPassword(address, password)
                        afterSignIn(sync)
                    } else {
                        sync.sendCode(address)
                    }
                }
            }
            busy = false
            result.onSuccess { if (usePassword) onSignedIn() else onSent(address) }
                .onFailure { error = it.message ?: context.getString(if (usePassword) R.string.sg_sign_in_failed else R.string.sg_send_email_failed) }
        }
    }

    AuthPage(
        title = stringResource(R.string.sg_sign_in_email),
        onBack = onBack,
        bottom = {
            Button(onClick = { send() }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(
                    stringResource(
                        when {
                            busy && usePassword -> R.string.sg_signing_in
                            busy -> R.string.sg_sending
                            usePassword -> R.string.sg_sign_in
                            else -> R.string.sg_send_code
                        },
                    ),
                )
            }
            TextButton(
                onClick = { usePassword = !usePassword; error = null },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(if (usePassword) R.string.sg_forgot_password else R.string.sg_use_password)) }
            passkey?.let { PasskeyButton(onClick = it) }
        },
    ) {
        Text(
            stringResource(if (usePassword) R.string.sg_password_intro else R.string.sg_code_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = ScriptGo.colors.muted,
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim() },
            label = { Text(stringResource(R.string.sg_email)) },
            singleLine = true,
            isError = error != null && !usePassword,
            supportingText = error?.takeIf { !usePassword }?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (usePassword) ImeAction.Next else ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (usePassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(72) },
                label = { Text(stringResource(R.string.sg_password)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(R.string.sg_email_spam_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ScriptGo.colors.muted,
            )
        }
    }
}

/**
 * Mockup 2c: six code boxes (the code can also be pasted), a resend timer, and the alternative of
 * tapping the link in the same email, which returns through AuthCallbackActivity.
 */
@Composable
fun CodeScreen(email: String, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(RESEND_SECONDS) }
    var resends by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(resends) {
        wait = RESEND_SECONDS
        while (wait > 0) { delay(1000); wait-- }
    }

    fun verify(value: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    sync.verifyCode(email, value)
                    afterSignIn(sync)
                }
            }
            busy = false
            result.onSuccess { onSignedIn() }.onFailure {
                error = it.message ?: context.getString(R.string.sg_code_wrong)
                code = ""
            }
        }
    }

    AuthPage(
        title = stringResource(R.string.sg_check_email),
        onBack = onBack,
        bottom = {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { error = context.getString(R.string.sg_no_email_app) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.sg_open_email_app)) }
        },
    ) {
        Text(
            stringResource(R.string.sg_code_sent, email),
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
        )
        BasicTextField(
            value = code,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(CODE_LENGTH)
                code = digits
                if (digits.length == CODE_LENGTH) verify(digits)
            },
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(CODE_LENGTH) { i ->
                        val active = i == code.length && !busy
                        Box(
                            Modifier.weight(1f).height(56.dp).background(c.card, RoundedCornerShape(14.dp))
                                .border(if (active) 2.dp else 1.5.dp, if (active) c.accentSolid else c.line, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(code.getOrNull(i)?.toString().orEmpty(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = c.ink)
                        }
                    }
                }
            },
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
        if (busy) Text(stringResource(R.string.sg_checking_code), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (wait > 0) {
                Text(stringResource(R.string.sg_resend_in, "0:%02d".format(wait)), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
            } else {
                TextButton(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { sync.sendCode(email) } }
                        result.onSuccess { resends++ }.onFailure { error = it.message }
                    }
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sg_resend_code)) }
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.sg_change_email)) }
        }
        OrDivider()
        BCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TintIcon(ScriptGoIcons.Mail)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sg_tap_link), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.sg_tap_link_sub), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
    }
}

/** Icon on a soft accent square, used in list rows and notes. */
@Composable
fun TintIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    Box(modifier.size(40.dp).background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
    }
}

/**
 * Work after any successful sign-in: download the business profile (so a second phone does not ask
 * again) and sync. Blocking; call off the main thread.
 */
fun afterSignIn(sync: SupabaseSync) {
    runCatching { sync.uploadPendingProfile() } // filled in during onboarding, before this account existed
    runCatching { sync.loadProfile() }
    runCatching { sync.syncNow() }
}

private const val CODE_LENGTH = 6
private const val RESEND_SECONDS = 60
