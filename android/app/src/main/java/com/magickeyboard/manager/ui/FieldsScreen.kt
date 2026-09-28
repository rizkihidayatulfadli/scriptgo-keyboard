@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magickeyboard.contacts.Channels
import com.magickeyboard.contacts.TemplateVariables
import com.magickeyboard.contacts.UnlinkedVariable
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.data.labelRes
import com.magickeyboard.template.FieldType
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Custom fields every contact can have; each field's key is a template variable. */
@Composable
fun FieldsScreen(nav: TabNav) {
    val repo = rememberRepository()
    val revision by repo.changes.collectAsState()
    var fields by remember { mutableStateOf<List<FieldDefinition>?>(null) }
    var editing by remember { mutableStateOf<FieldDefinition?>(null) }
    var creating by remember { mutableStateOf(false) }
    var unlinked by remember { mutableStateOf<List<UnlinkedVariable>>(emptyList()) }
    var converting by remember { mutableStateOf<UnlinkedVariable?>(null) }
    LaunchedEffect(revision) {
        withContext(Dispatchers.IO) {
            val definitions = repo.fieldDefinitions()
            fields = definitions
            // Variables typed straight into templates, offered as fields (the key stays the same).
            unlinked = TemplateVariables.unlinked(repo.templates().map { it.body }, definitions.map { it.key }.toSet())
        }
    }

    TabScaffold(title = stringResource(R.string.sg_fields_title), nav = nav, onAdd = { creating = true }) { padding ->
        val list = fields
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Every contact has a name and a phone; they are variables too, just not editable here.
            item {
                SectionLabelWithInfo(
                    stringResource(R.string.sg_fields_builtin),
                    infoTitle = stringResource(R.string.sg_fields_title),
                    infoText = stringResource(R.string.sg_fields_intro),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            items(BUILT_IN_VARIABLES, key = { it.first }) { (key, description) ->
                BuiltInRow(key, stringResource(description))
            }
            // Channel is built in too, but its name and choices can be edited.
            val channel = list?.firstOrNull { it.key == Channels.KEY }
            val own = list?.filter { it.key != Channels.KEY }
            if (channel != null) {
                item(key = channel.id) {
                    VariableCard(
                        icon = channel.shownIcon,
                        title = channel.label,
                        key = channel.key,
                        detail = channel.options.joinToString(", "),
                        onClick = { editing = channel },
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.sg_fields_yours), Modifier.padding(start = 4.dp, top = 8.dp)) }
            when {
                own == null -> Unit
                own.isEmpty() -> item { EmptyCard(stringResource(R.string.sg_fields_empty)) }
                else -> items(own, key = { it.id }) { field ->
                    VariableCard(
                        icon = field.shownIcon,
                        title = field.label,
                        key = field.key,
                        detail = stringResource(field.type.labelRes()) +
                            if (field.type == FieldType.CURRENCY) " (${field.currency.code})" else "",
                        onClick = { editing = field },
                    )
                }
            }
            if (unlinked.isNotEmpty()) {
                item {
                    SectionLabelWithInfo(
                        stringResource(R.string.sg_fields_from_templates),
                        infoTitle = stringResource(R.string.sg_fields_from_templates),
                        infoText = stringResource(R.string.sg_fields_from_templates_intro),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(unlinked, key = { "var:" + it.key }) { variable ->
                    UnlinkedRow(variable, onConvert = { converting = variable })
                }
            }
        }
    }

    val current = editing
    val preset = converting
    if (creating || current != null || preset != null) {
        FieldEditorSheet(
            field = current,
            existingKeys = fields.orEmpty().map { it.key }.toSet(),
            nextPosition = fields.orEmpty().size,
            preset = preset,
            onDismiss = {
                creating = false
                editing = null
                converting = null
            },
        )
    }
}

/** A template variable that is not a field yet, with a button to make it one. */
@Composable
private fun UnlinkedRow(variable: UnlinkedVariable, onConvert: () -> Unit) {
    val c = ScriptGo.colors
    BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlineChip("{${variable.key}}")
                Text(
                    pluralStringResource(R.plurals.sg_fields_used_in, variable.templateCount, variable.templateCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            OutlinedButton(onClick = onConvert) { Text(stringResource(R.string.sg_fields_make_field)) }
        }
    }
}

/** Variables every contact has, with what they hold. */
private val BUILT_IN_VARIABLES = listOf(
    "nama" to R.string.sg_builtin_name_desc,
    "nomor" to R.string.sg_builtin_phone_desc,
)

/** A built-in variable: shown like a field, but locked. */
@Composable
private fun BuiltInRow(key: String, description: String) {
    VariableCard(
        icon = if (key == "nomor") "phone" else "user",
        title = variableLabel(key),
        key = key,
        detail = description,
        onClick = null,
    )
}

/**
 * A field as a card: its icon, name, what it holds, and the variable it fills. The variable sits on
 * its own line and is cut short when long, so it never squeezes the name (as a trailing chip did).
 * Built-in variables ([onClick] null) show a lock instead of the arrow.
 */
@Composable
private fun VariableCard(icon: String, title: String, key: String, detail: String, onClick: (() -> Unit)?) {
    val c = ScriptGo.colors
    BCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldIconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                VariableBadge("{$key}", Modifier.padding(top = 2.dp))
            }
            if (onClick == null) {
                Icon(ScriptGoIcons.Lock, contentDescription = stringResource(R.string.sg_fields_builtin), tint = c.muted, modifier = Modifier.size(16.dp))
            } else {
                Icon(ScriptGoIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
