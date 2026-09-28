package com.magickeyboard.manager.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Questions answered right in the app, before writing to us. */
private val FAQ = listOf(
    R.string.sg_faq_use_q to R.string.sg_faq_use_a,
    R.string.sg_faq_keyboard_q to R.string.sg_faq_keyboard_a,
    R.string.sg_faq_variables_q to R.string.sg_faq_variables_a,
    R.string.sg_faq_privacy_q to R.string.sg_faq_privacy_a,
    R.string.sg_faq_laptop_q to R.string.sg_faq_laptop_a,
    R.string.sg_faq_password_q to R.string.sg_faq_password_a,
)

/** Kinds of message, as stored in support_messages.kind. */
private val KINDS = listOf(
    "help" to R.string.sg_help_kind_help,
    "idea" to R.string.sg_help_kind_idea,
    "bug" to R.string.sg_help_kind_bug,
)

/**
 * Akun → Pusat bantuan: common questions, then a form to ask for help, suggest something, or
 * report a problem. Messages go to the support_messages table (insert only); the app version and
 * phone model come along so problems are easier to reproduce.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    val signedIn = remember { sync.signedInEmail() != null }
    var kind by remember { mutableStateOf("help") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val emailOk = email.isBlank() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())

    DetailPage(
        title = stringResource(R.string.sg_help_center),
        onBack = onBack,
        bottom = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                        val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { sync.sendSupport(kind, message, email, version, device, AppLanguage.current(context)) }
                        }
                        busy = false
                        result.onSuccess {
                            sent = true
                            message = ""
                        }.onFailure { error = it.message ?: context.getString(R.string.sg_save_failed) }
                    }
                },
                enabled = !busy && message.isNotBlank() && emailOk,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (busy) R.string.sg_sending else R.string.sg_help_send)) }
        },
    ) {
        Text(stringResource(R.string.sg_help_intro), style = MaterialTheme.typography.bodyMedium, color = c.muted)

        SectionLabel(stringResource(R.string.sg_help_faq))
        BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
            FAQ.forEachIndexed { index, (question, answer) ->
                if (index > 0) HorizontalDivider(color = c.line)
                FaqItem(stringResource(question), stringResource(answer))
            }
        }

        SectionLabel(stringResource(R.string.sg_help_write))
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(c.bg)
                .border(1.dp, c.line, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KINDS.forEach { (code, label) ->
                val on = code == kind
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(if (on) c.card else c.bg)
                        .semantics { selected = on; role = Role.RadioButton }
                        .clickable { kind = code; sent = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.muted)
                }
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(4000); sent = false },
            label = { Text(stringResource(R.string.sg_help_message)) },
            placeholder = {
                Text(
                    stringResource(
                        when (kind) {
                            "idea" -> R.string.sg_help_message_hint_idea
                            "bug" -> R.string.sg_help_message_hint_bug
                            else -> R.string.sg_help_message_hint_help
                        },
                    ),
                )
            },
            minLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!signedIn) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(254) },
                label = { Text(stringResource(R.string.sg_help_reply_email)) },
                isError = !emailOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.sg_help_device_note), style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (sent) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(ScriptGoIcons.Check, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sg_help_sent), style = MaterialTheme.typography.bodyMedium, color = c.accentInk)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
    }
}

/** One question; tapping it shows or hides the answer. */
@Composable
private fun FaqItem(question: String, answer: String) {
    val c = ScriptGo.colors
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.bg), contentAlignment = Alignment.Center) {
                Icon(
                    ScriptGoIcons.ChevronDown,
                    contentDescription = null,
                    tint = c.muted,
                    modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f),
                )
            }
        }
        AnimatedVisibility(visible = open) {
            Text(answer, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
