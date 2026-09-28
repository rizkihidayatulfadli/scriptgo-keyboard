@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.magickeyboard.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.SupabaseSync
import com.magickeyboard.sync.SyncException
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rewrite styles, as sent to the ai-template Edge Function, with their labels. */
private val STYLES = listOf(
    "polite" to R.string.sg_ai_style_polite,
    "shorter" to R.string.sg_ai_style_shorter,
    "casual" to R.string.sg_ai_style_casual,
    "spelling" to R.string.sg_ai_style_spelling,
)

/** Why an AI result is reported (ai_reports.reason), with their labels. */
private val REPORT_REASONS = listOf(
    "offensive" to R.string.sg_ai_report_offensive,
    "wrong" to R.string.sg_ai_report_wrong,
    "other" to R.string.sg_ai_report_other,
)

/** "✨ Buat dengan AI" on an empty template, "✨ Rapikan" once it has text. */
@Composable
fun AiButton(rewrite: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Icon(ScriptGoIcons.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(if (rewrite) R.string.sg_ai_rewrite else R.string.sg_ai_create),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Makes a template from a description ([current] == null) or rewrites [current] in a chosen style.
 * The result shows first (for a rewrite, next to the original) and only [onUse] changes the editor.
 * [fieldKeys] are sent so the model reuses the user's variables; values never leave the phone.
 */
@Composable
fun AiTemplateSheet(
    current: SupabaseSync.AiTemplate?,
    fieldKeys: List<String>,
    onUse: (SupabaseSync.AiTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = ScriptGo.colors
    val sync = SupabaseSync.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val signedIn = remember { sync.signedInEmail() != null }
    var quota by remember { mutableStateOf<SupabaseSync.AiQuota?>(null) }
    var description by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(STYLES.first().first) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SupabaseSync.AiTemplate?>(null) }

    suspend fun refreshQuota() {
        withContext(Dispatchers.IO) { runCatching { sync.aiQuota() } }.onSuccess { quota = it }
    }
    LaunchedEffect(Unit) { if (signedIn) refreshQuota() }

    fun run() {
        busy = true
        error = null
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (current == null) sync.aiCreate(description, fieldKeys)
                    else sync.aiRewrite(style, current.title, current.body, fieldKeys)
                }
            }.onSuccess { (template, left) ->
                result = template
                quota = left
            }.onFailure {
                error = (it as? SyncException)?.message ?: it.message
                refreshQuota()
            }
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadge(ScriptGoIcons.Sparkles)
                Column {
                    Text(
                        stringResource(if (current == null) R.string.sg_ai_create else R.string.sg_ai_rewrite_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                    )
                    quota?.let { q ->
                        Text(
                            stringResource(if (q.daily) R.string.sg_ai_quota_day else R.string.sg_ai_quota_total, q.remaining, q.limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                        )
                    }
                }
            }

            val left = quota?.remaining
            when {
                !signedIn -> Text(stringResource(R.string.sg_ai_sign_in), color = c.muted)
                result != null -> AiResult(
                    current,
                    result!!,
                    onUse = { onUse(result!!) },
                    onAgain = { result = null },
                    report = { reason ->
                        val shown = result!!
                        withContext(Dispatchers.IO) {
                            runCatching {
                                if (current == null) sync.reportAi("create", "", description, shown.body, reason, "")
                                else sync.reportAi("rewrite", style, current.body, shown.body, reason, "")
                            }
                        }.isSuccess
                    },
                )
                else -> {
                    if (current == null) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(500) },
                            label = { Text(stringResource(R.string.sg_ai_describe)) },
                            placeholder = { Text(stringResource(R.string.sg_ai_describe_hint)) },
                            minLines = 3,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            STYLES.forEach { (key, label) ->
                                FilterChip(
                                    selected = style == key,
                                    onClick = { style = key },
                                    enabled = !busy,
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }
                    if (left == 0) {
                        Text(
                            stringResource(if (quota?.daily == true) R.string.sg_ai_quota_empty_day else R.string.sg_ai_quota_empty_free),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = ::run,
                        enabled = !busy && left != 0 && (current != null || description.isNotBlank()),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.sg_ai_busy), modifier = Modifier.padding(start = 10.dp))
                        } else {
                            Text(stringResource(if (current == null) R.string.sg_ai_go else R.string.sg_ai_go_rewrite))
                        }
                    }
                    Text(stringResource(R.string.sg_ai_privacy), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
    }
}

/** The AI's template; for a rewrite, the original first so the two can be compared. */
@Composable
private fun AiResult(
    current: SupabaseSync.AiTemplate?,
    result: SupabaseSync.AiTemplate,
    onUse: () -> Unit,
    onAgain: () -> Unit,
    report: suspend (reason: String) -> Boolean,
) {
    val c = ScriptGo.colors
    if (current != null) {
        SectionLabel(stringResource(R.string.sg_ai_before))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(current.body, style = MaterialTheme.typography.bodyMedium, color = c.muted) }
        SectionLabel(stringResource(R.string.sg_ai_after))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (current == null) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            OutlineChip("/" + result.shortcut)
        }
        Text(result.body, style = MaterialTheme.typography.bodyMedium, color = c.ink)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.sg_ai_again)) }
        Button(onClick = onUse, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.sg_ai_use)) }
    }
    AiReport(key = result, report = report)
}

/**
 * "Laporkan hasil ini": Google Play asks apps with generative AI to let users report offensive
 * output. One tap on a reason sends the result (and what was asked) to ai_reports.
 */
@Composable
private fun AiReport(key: Any, report: suspend (reason: String) -> Boolean) {
    val c = ScriptGo.colors
    val scope = rememberCoroutineScope()
    var open by remember(key) { mutableStateOf(false) }
    var busy by remember(key) { mutableStateOf(false) }
    var sent by remember(key) { mutableStateOf<Boolean?>(null) }
    when {
        sent == true -> Text(stringResource(R.string.sg_ai_report_sent), style = MaterialTheme.typography.bodySmall, color = c.muted)
        !open -> TextButton(onClick = { open = true }) { Text(stringResource(R.string.sg_ai_report), color = c.muted) }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sg_ai_report_why), style = MaterialTheme.typography.bodyMedium, color = c.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REPORT_REASONS.forEach { (reason, label) ->
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                sent = report(reason)
                                busy = false
                            }
                        },
                    ) { Text(stringResource(label)) }
                }
            }
            if (sent == false) Text(stringResource(R.string.sg_ai_report_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
