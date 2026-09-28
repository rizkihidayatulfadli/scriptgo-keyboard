package com.magickeyboard.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.PasskeyCeremony
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Page frame for the account detail screens: title bar, scrolling content, pinned action. */
@Composable
internal fun DetailPage(title: String, onBack: () -> Unit, bottom: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(ScriptGo.colors.bg).imePadding()) {
        BackTopBar(title = title, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp), content = bottom)
    }
}

/** Akun → Data pribadi: the name on the account, the email, and how the account signs in. */
@Composable
fun PersonalDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var name by remember { mutableStateOf(sync.fullName()) }
    var providers by remember { mutableStateOf(sync.linkedProviders()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { sync.loadPersonal() } }
        if (name.isEmpty()) name = sync.fullName()
        providers = sync.linkedProviders()
    }

    DetailPage(
        title = stringResource(R.string.sg_personal_data),
        onBack = onBack,
        bottom = {
            Button(
                onClick = {
                    busy = true
                    message = null
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { sync.savePersonal(name) } }
                        busy = false
                        result.onSuccess { message = context.getString(R.string.sg_saved) }.onFailure { error = it.message }
                    }
                },
                enabled = !busy && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (busy) R.string.sg_saving else R.string.sg_save)) }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(120) },
            label = { Text(stringResource(R.string.sg_full_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = sync.signedInEmail().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sg_email)) },
            supportingText = { Text(stringResource(R.string.sg_email_readonly)) },
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel(stringResource(R.string.sg_sign_in_methods))
        BCard(Modifier.fillMaxWidth(), radius = 22.dp) {
            val methods = buildList {
                if ("google" in providers) add(context.getString(R.string.sg_method_google))
                if ("email" in providers || providers.isEmpty()) add(context.getString(R.string.sg_method_email))
                if (sync.hasPassword()) add(context.getString(R.string.sg_method_password))
            }
            methods.forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ScriptGoIcons.Check, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                    Text("  $method", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        message?.let { Text(it, color = c.accentInk, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Akun → Kata sandi: sets or changes a password, so the account can also sign in with email and
 * password. Forgetting it is fine: signing in with the emailed code always works.
 */
@Composable
fun PasswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val hadPassword = remember { sync.hasPassword() }
    val longEnough = password.length >= 8
    val mixed = password.any { it.isLetter() } && password.any { it.isDigit() }
    val matches = password == confirm && confirm.isNotEmpty()

    DetailPage(
        title = stringResource(if (hadPassword) R.string.sg_password_change else R.string.sg_password_create),
        onBack = onBack,
        bottom = {
            Button(
                onClick = {
                    busy = true
                    message = null
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { sync.setPassword(password) } }
                        busy = false
                        result.onSuccess {
                            message = context.getString(R.string.sg_password_saved)
                            password = ""
                            confirm = ""
                        }.onFailure { error = it.message }
                    }
                },
                enabled = !busy && longEnough && mixed && matches,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (busy) R.string.sg_saving else R.string.sg_password_save)) }
        },
    ) {
        Text(
            stringResource(R.string.sg_password_optional),
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
        )
        val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
        val eye = @Composable {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) ScriptGoIcons.Lock else ScriptGoIcons.KeyRound, contentDescription = stringResource(if (visible) R.string.sg_hide else R.string.sg_show))
            }
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(72) },
            label = { Text(stringResource(R.string.sg_password_new)) },
            singleLine = true,
            visualTransformation = transformation,
            trailingIcon = eye,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.take(72) },
            label = { Text(stringResource(R.string.sg_password_repeat)) },
            singleLine = true,
            visualTransformation = transformation,
            isError = confirm.isNotEmpty() && !matches,
            supportingText = { if (confirm.isNotEmpty() && !matches) Text(stringResource(R.string.sg_password_mismatch)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Rule(ok = longEnough, text = stringResource(R.string.sg_password_rule_length))
        Rule(ok = mixed, text = stringResource(R.string.sg_password_rule_mixed))
        message?.let { Text(it, color = c.accentInk, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Rule(ok: Boolean, text: String) {
    val c = ScriptGo.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) ScriptGoIcons.Check else ScriptGoIcons.Close, contentDescription = null, tint = if (ok) c.accentInk else c.muted, modifier = Modifier.size(16.dp))
        Text("  $text", style = MaterialTheme.typography.bodySmall, color = if (ok) c.ink else c.muted)
    }
}

/**
 * Akun → Passkey: sign in with the phone's fingerprint, face, or screen lock instead of a code or
 * password. Lists the account's passkeys, adds one, and removes one (for example a lost phone).
 */
@Composable
fun PasskeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    val passkeys = remember { mutableStateListOf<SupabaseSync.Passkey>() }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SupabaseSync.Passkey?>(null) }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) { runCatching { sync.passkeys() } }
        loading = false
        result.onSuccess { passkeys.clear(); passkeys.addAll(it) }.onFailure { error = it.message }
    }
    LaunchedEffect(Unit) { reload() }

    DetailPage(
        title = stringResource(R.string.sg_passkey),
        onBack = onBack,
        bottom = {
            Button(
                onClick = {
                    busy = true
                    message = null
                    error = null
                    scope.launch {
                        try {
                            PasskeyCeremony.register(context, sync)
                            message = context.getString(R.string.sg_passkey_added)
                            reload()
                        } catch (_: PasskeyCeremony.Cancelled) {
                        } catch (e: Exception) {
                            error = e.message ?: context.getString(R.string.sg_err_passkey_failed)
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(ScriptGoIcons.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (busy) R.string.sg_passkey_adding else R.string.sg_passkey_add))
            }
        },
    ) {
        Text(stringResource(R.string.sg_passkey_intro), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        when {
            loading -> Text(stringResource(R.string.sg_loading), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            passkeys.isEmpty() -> Text(stringResource(R.string.sg_passkey_none), style = MaterialTheme.typography.bodyMedium)
            else -> BCard(Modifier.fillMaxWidth(), radius = 22.dp) {
                passkeys.forEachIndexed { index, passkey ->
                    if (index > 0) HorizontalDivider(color = c.line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ScriptGoIcons.Fingerprint, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(passkey.name.ifBlank { stringResource(R.string.sg_passkey) }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.sg_passkey_created, passkey.createdAt.take(10)),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted,
                            )
                        }
                        IconButton(onClick = { deleting = passkey }, enabled = !busy) {
                            Icon(ScriptGoIcons.Trash, contentDescription = stringResource(R.string.sg_delete), tint = c.danger)
                        }
                    }
                }
            }
        }
        message?.let { Text(it, color = c.accentInk, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodyMedium) }
    }

    deleting?.let { passkey ->
        ConfirmDeleteDialog(
            what = passkey.name.ifBlank { context.getString(R.string.sg_passkey) },
            onConfirm = {
                deleting = null
                busy = true
                message = null
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { sync.deletePasskey(passkey.id) } }
                    busy = false
                    result.onSuccess { passkeys.remove(passkey) }.onFailure { error = it.message }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}
