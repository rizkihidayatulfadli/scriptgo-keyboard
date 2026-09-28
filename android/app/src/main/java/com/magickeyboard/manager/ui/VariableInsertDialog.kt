package com.magickeyboard.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.magickeyboard.contacts.FieldKeys
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.template.SourceVariable
import com.magickeyboard.template.VariableSnippet
import helium314.keyboard.latin.R

/**
 * Asks for an optional fallback before inserting a contact variable such as `{nama}`, so the user
 * never has to type `{nama|Kak}` by hand. [key] is the variable, [label] its human name.
 */
@Composable
fun InsertVariableDialog(key: String, label: String, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    var fallback by remember { mutableStateOf("") }
    val snippet = VariableSnippet.build(key, fallback = fallback)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sg_var_insert_title, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sg_var_insert_intro), style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.muted)
                OutlinedTextField(
                    value = fallback,
                    onValueChange = { fallback = it.take(80) },
                    label = { Text(stringResource(R.string.sg_var_fallback)) },
                    placeholder = { Text(stringResource(if (key == "nama") R.string.sg_var_fallback_hint_name else R.string.sg_var_fallback_hint)) },
                    supportingText = { Text(stringResource(R.string.sg_var_fallback_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(snippet) }) { Text(stringResource(R.string.sg_insert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sg_cancel)) } },
    )
}

/**
 * A variable that only lives in this template (not a contact field): a free answer with an
 * optional fallback, or a dropdown of options. It is asked every time the template is used.
 */
@Composable
fun NewVariableDialog(takenKeys: Set<String>, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf(false) }
    var fallback by remember { mutableStateOf("") }
    var options by remember { mutableStateOf("") }
    val key = FieldKeys.fromLabel(name, takenKeys)
    val optionList = options.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val valid = name.isNotBlank() && key.isNotEmpty() && (!choice || optionList.isNotEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sg_var_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.sg_var_new_name)) },
                    placeholder = { Text(stringResource(R.string.sg_var_new_name_hint)) },
                    supportingText = { if (name.isNotBlank()) VariableBadge(variableLabel(key)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !choice, onClick = { choice = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.sg_var_kind_text))
                    }
                    SegmentedButton(selected = choice, onClick = { choice = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.sg_var_kind_choice))
                    }
                }
                if (choice) {
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text(stringResource(R.string.sg_field_options)) },
                        placeholder = { Text(stringResource(R.string.sg_var_options_hint)) },
                        supportingText = { Text(stringResource(R.string.sg_var_options_help)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = fallback,
                        onValueChange = { fallback = it.take(80) },
                        label = { Text(stringResource(R.string.sg_var_fallback)) },
                        supportingText = { Text(stringResource(R.string.sg_var_fallback_help)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.sg_var_new_note), style = MaterialTheme.typography.bodySmall, color = ScriptGo.colors.muted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInsert(VariableSnippet.build(key, fallback = fallback.takeIf { !choice }, options = if (choice) optionList else emptyList())) },
                enabled = valid,
            ) { Text(stringResource(R.string.sg_insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sg_cancel)) } },
    )
}

/**
 * A variable tapped in the message: change its fallback (or its choices, for a choice variable),
 * or remove it. [onSave] gets the new `{...}` source, or null to remove the variable. Built-ins
 * such as `{@tanggal}` have nothing to set.
 */
@Composable
fun EditVariableDialog(variable: SourceVariable, label: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    val choice = !variable.options.isNullOrEmpty()
    var fallback by remember(variable) { mutableStateOf(variable.default.orEmpty()) }
    var options by remember(variable) { mutableStateOf(variable.options.orEmpty().joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sg_var_edit_title, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    variable.builtin -> Text(stringResource(R.string.sg_var_edit_builtin), style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.muted)
                    choice -> OutlinedTextField(
                        value = options,
                        onValueChange = { options = it.take(500) },
                        label = { Text(stringResource(R.string.sg_var_options)) },
                        supportingText = { Text(stringResource(R.string.sg_var_options_help)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> OutlinedTextField(
                        value = fallback,
                        onValueChange = { fallback = it.take(80) },
                        label = { Text(stringResource(R.string.sg_var_fallback)) },
                        supportingText = { Text(stringResource(R.string.sg_var_fallback_help)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { onSave(null) }) {
                    Text(stringResource(R.string.sg_var_remove), color = ScriptGo.colors.danger)
                }
            }
        },
        confirmButton = {
            if (!variable.builtin) {
                TextButton(onClick = {
                    onSave(
                        if (choice) VariableSnippet.build(variable.key, options = options.lines())
                        else VariableSnippet.build(variable.key, fallback = fallback.trim()),
                    )
                }) { Text(stringResource(R.string.sg_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (variable.builtin) R.string.sg_close else R.string.sg_cancel)) } },
    )
}

