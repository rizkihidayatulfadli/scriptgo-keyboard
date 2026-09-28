@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magickeyboard.keyboard.SpaceHold
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.data.Template
import com.magickeyboard.manager.data.UsageTotals
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.SupabaseSync
import com.magickeyboard.template.SourceVariable
import com.magickeyboard.template.TemplateParser
import com.magickeyboard.template.TemplateRenderer
import com.magickeyboard.template.TimeSaved
import helium314.keyboard.latin.R
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TemplatesScreen(navigate: (Screen) -> Unit, nav: TabNav, openKeyboardSettings: () -> Unit) {
    val repo = rememberRepository()
    val revision by repo.changes.collectAsState()
    var query by remember { mutableStateOf("") }
    var templates by remember { mutableStateOf<List<Template>?>(null) }
    var totals by remember { mutableStateOf(UsageTotals(0, 0)) }
    var trigger by remember { mutableStateOf(ManagerRepository.DEFAULT_TRIGGER) }
    var askContact by remember { mutableStateOf(true) }
    var editingTrigger by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(revision, query) {
        withContext(Dispatchers.IO) { Triple(repo.templates(query), repo.usageTotals(), repo.triggerChar()) to repo.askContact() }.let {
            templates = it.first.first
            totals = it.first.second
            trigger = it.first.third
            askContact = it.second
        }
    }

    TabScaffold(
        title = stringResource(R.string.sg_tab_templates),
        nav = nav,
        onAdd = { navigate(Screen.EditTemplate(null)) },
        leading = { ScriptGoMark(Modifier.size(34.dp)) },
        actions = {
            CircleIconButton(ScriptGoIcons.Settings, stringResource(R.string.sg_template_settings), onClick = { editingTrigger = true })
            CircleIconButton(ScriptGoIcons.Keyboard, stringResource(R.string.sg_keyboard_settings), onClick = openKeyboardSettings)
        },
    ) { padding ->
        val list = templates
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SearchPill(query, { query = it }, stringResource(R.string.sg_templates_search)) }
            item { TimeSavedCard(totals, trigger) }
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    EmptyCard(
                        if (query.isBlank()) stringResource(R.string.sg_templates_empty, trigger)
                        else stringResource(R.string.sg_templates_no_match)
                    )
                }
                else -> items(list, key = { it.id }) { template ->
                    TemplateCard(template, trigger, onClick = { navigate(Screen.EditTemplate(template.id)) })
                }
            }
        }
    }

    if (editingTrigger) {
        SettingsDialog(
            currentTrigger = trigger,
            currentAskContact = askContact,
            onDismiss = { editingTrigger = false },
            currentSpaceHold = repo.spaceHoldEnabled(),
            currentSpaceHoldMs = repo.spaceHoldMs(),
            onSave = { chosen, ask, hold, holdMs ->
                editingTrigger = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.setTriggerChar(chosen)
                        repo.setAskContact(ask)
                        repo.setSpaceHold(hold, holdMs)
                    }
                }
            },
        )
    }
}

@Composable
private fun TimeSavedCard(totals: UsageTotals, trigger: Char) {
    val c = ScriptGo.colors
    BCard(Modifier.fillMaxWidth(), radius = 28.dp, contentPadding = PaddingValues(22.dp)) {
        if (totals.uses == 0L) {
            SectionLabel(stringResource(R.string.sg_time_saved))
            Text(stringResource(R.string.sg_time_saved_later), style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(
                stringResource(R.string.sg_time_saved_hint, trigger),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
        } else {
            SectionLabel(stringResource(R.string.sg_time_saved))
            GradientText(AppLanguage.duration(LocalContext.current, TimeSaved.seconds(totals.chars)), MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.sg_template_uses, totals.uses), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}

/**
 * Keyboard settings of the manager: the character that starts a shortcut (letters, digits, and
 * spaces are not allowed) and whether picking a template first asks "Untuk siapa?".
 */
@Composable
private fun SettingsDialog(
    currentTrigger: Char,
    currentAskContact: Boolean,
    currentSpaceHold: Boolean,
    currentSpaceHoldMs: Int,
    onDismiss: () -> Unit,
    onSave: (Char, Boolean, Boolean, Int) -> Unit,
) {
    var value by remember { mutableStateOf(currentTrigger.toString()) }
    var ask by remember { mutableStateOf(currentAskContact) }
    var hold by remember { mutableStateOf(currentSpaceHold) }
    var holdMs by remember { mutableIntStateOf(currentSpaceHoldMs) }
    val candidate = value.trim().singleOrNull()
    val valid = candidate != null && !candidate.isLetterOrDigit() && !candidate.isWhitespace()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sg_keyboard_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sg_trigger_intro))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(1) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { if (!valid) Text(stringResource(R.string.sg_trigger_invalid)) },
                )
                Row(Modifier.clickable { ask = !ask }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ask, onCheckedChange = { ask = it })
                    Text(stringResource(R.string.sg_ask_contact))
                }
                Row(Modifier.clickable { hold = !hold }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hold, onCheckedChange = { hold = it })
                    Text(stringResource(R.string.sg_space_hold))
                }
                if (hold) {
                    Text(
                        stringResource(R.string.sg_space_hold_time, String.format(Locale.US, "%.2f", holdMs / 1000f).trimEnd('0').trimEnd('.').let { if (AppLanguage.current(LocalContext.current) == AppLanguage.ENGLISH) it else it.replace('.', ',') }),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = holdMs.toFloat(),
                        onValueChange = { holdMs = (it / 250).roundToInt() * 250 },
                        valueRange = SpaceHold.MIN_MS.toFloat()..SpaceHold.MAX_MS.toFloat(),
                        steps = (SpaceHold.MAX_MS - SpaceHold.MIN_MS) / 250 - 1,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { candidate?.let { onSave(it, ask, hold, holdMs) } }, enabled = valid) { Text(stringResource(R.string.sg_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sg_cancel)) } },
    )
}

@Composable
fun TemplateEditorScreen(id: String?, back: () -> Unit) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    val templateId = remember { id ?: ManagerRepository.newId() }
    var title by remember { mutableStateOf("") }
    var shortcut by remember { mutableStateOf("") }
    var shortcutEdited by remember { mutableStateOf(id != null) }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var tags by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(emptyList<FieldDefinition>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askFallback by remember { mutableStateOf<Pair<String, String>?>(null) }
    var creatingVariable by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    var editingVariable by remember { mutableStateOf<SourceVariable?>(null) }

    LaunchedEffect(Unit) {
        val (template, defs) = withContext(Dispatchers.IO) { (id?.let(repo::template)) to repo.fieldDefinitions() }
        fields = defs
        if (template != null) {
            title = template.title
            shortcut = template.shortcut
            body = TextFieldValue(template.body)
            tags = template.tags.joinToString(", ")
        }
        loaded = true
    }

    fun insert(snippet: String) {
        val start = body.selection.min
        val end = body.selection.max
        val text = body.text.substring(0, start) + snippet + body.text.substring(end)
        body = TextFieldValue(text, TextRange(start + snippet.length))
    }

    fun save() {
        scope.launch {
            val template = Template(
                id = templateId,
                title = title,
                shortcut = shortcut.trim().removePrefix("/"),
                body = body.text,
                tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
            // The free plan allows 20 templates (PRD §9); edits to existing ones always save.
            error = withContext(Dispatchers.IO) { repo.saveTemplate(template, SupabaseSync.get(repo.appContext).freeLimited()) }
            if (error == null) back()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(stringResource(if (id == null) R.string.sg_template_new else R.string.sg_template_edit), back) {
                if (id != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.sg_delete)) }
                TextButton(onClick = ::save, enabled = loaded) { Text(stringResource(R.string.sg_save)) }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (!shortcutEdited) shortcut = suggestShortcut(it)
                },
                label = { Text(stringResource(R.string.sg_template_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = shortcut,
                onValueChange = {
                    shortcut = it.lowercase().replace(' ', '-')
                    shortcutEdited = true
                },
                label = { Text(stringResource(R.string.sg_template_shortcut)) },
                prefix = { Text("/") },
                supportingText = { Text(stringResource(R.string.sg_template_shortcut_hint, "/$shortcut")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // AI makes the first draft of an empty template, or polishes one that has text.
            AiButton(rewrite = body.text.isNotBlank(), onClick = { aiOpen = true })
            val colors = ScriptGo.colors
            val badges = remember(colors) { VariableBadgeTransformation(colors) }
            OutlinedTextField(
                value = body,
                onValueChange = { next ->
                    // A tap inside a variable (the caret lands within its braces) opens its settings.
                    val caret = next.selection
                    val tapped = if (next.text == body.text && caret.collapsed) {
                        TemplateParser.sourceVariables(next.text).firstOrNull { caret.start > it.start && caret.start < it.end }
                    } else null
                    if (tapped != null) {
                        body = next.copy(selection = TextRange(tapped.end))
                        editingVariable = tapped
                    } else {
                        body = next
                    }
                },
                label = { Text(stringResource(R.string.sg_template_body)) },
                // Variables show as named badges instead of {braces}.
                visualTransformation = badges,
                supportingText = { Text(stringResource(R.string.sg_template_body_tap_hint)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            editingVariable?.let { v ->
                EditVariableDialog(
                    variable = v,
                    label = variableLabel(v.key, fields.associate { it.key to it.label }),
                    onSave = { raw ->
                        val text = body.text
                        // Only if the text still has this variable where it was.
                        if (v.end <= text.length) {
                            val replacement = raw.orEmpty()
                            body = TextFieldValue(text.replaceRange(v.start, v.end, replacement), TextRange(v.start + replacement.length))
                        }
                        editingVariable = null
                    },
                    onDismiss = { editingVariable = null },
                )
            }
            Text(stringResource(R.string.sg_template_insert_variable), style = MaterialTheme.typography.labelLarge)
            val labels = fields.associate { it.key to it.label }
            val variables = listOf("nama", "nomor") + fields.map { it.key } + listOf("@tanggal", "@jam", "@hari", "@kursor")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                variables.forEach { key ->
                    val label = variableLabel(key, labels)
                    // Built-ins (date, time, cursor) have nothing to fall back on; contact values may be empty.
                    VariableChip(label) { if (key.startsWith("@")) insert("{$key}") else askFallback = key to label }
                }
                VariableChip(stringResource(R.string.sg_var_new_chip)) { creatingVariable = true }
            }
            askFallback?.let { (key, label) ->
                InsertVariableDialog(key, label, onInsert = { insert(it); askFallback = null }, onDismiss = { askFallback = null })
            }
            if (creatingVariable) {
                NewVariableDialog(
                    takenKeys = fields.map { it.key }.toSet(),
                    onInsert = { insert(it); creatingVariable = false },
                    onDismiss = { creatingVariable = false },
                )
            }
            TemplatePreview(body.text)
            // Tags sit below the preview, as in the extension.
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.sg_template_tags)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (aiOpen) {
        AiTemplateSheet(
            current = body.text.takeIf { it.isNotBlank() }?.let { SupabaseSync.AiTemplate(title, shortcut, it) },
            fieldKeys = fields.map { it.key },
            onUse = { result ->
                if (body.text.isBlank()) {
                    title = result.title
                    shortcut = result.shortcut.replace('_', '-')
                    shortcutEdited = true
                }
                body = TextFieldValue(result.body, TextRange(result.body.length))
                aiOpen = false
            },
            onDismiss = { aiOpen = false },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = stringResource(R.string.sg_template_what),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteTemplate(templateId) }
                    back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TemplatePreview(body: String) {
    val parsed = remember(body) { TemplateParser.parse(body) }
    val preview = remember(parsed) { TemplateRenderer.render(parsed, contact = null, inputs = emptyMap(), now = LocalDateTime.now()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sg_preview), style = MaterialTheme.typography.labelLarge)
            Text(preview.text.ifEmpty { stringResource(R.string.sg_empty_option) })
            if (parsed.variables.isNotEmpty()) {
                Text(
                    stringResource(R.string.sg_template_asked, parsed.variables.joinToString { it.key }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** "Ongkir Jabodetabek" → "ongkir-jabodetabek". */
private fun suggestShortcut(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24)

/**
 * One template in the list. The title and a preview get the full width, and the shortcut sits on
 * its own line below, so a long title or shortcut never squeezes the other.
 */
@Composable
private fun TemplateCard(template: Template, trigger: Char, onClick: () -> Unit) {
    val c = ScriptGo.colors
    val title = template.title.ifBlank { template.shortcut }
    BCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LetterBadge(title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val preview = template.body.replace('\n', ' ').trim()
                if (preview.isNotEmpty()) {
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                OutlineChip("$trigger${template.shortcut}", Modifier.padding(top = 4.dp))
            }
        }
    }
}
