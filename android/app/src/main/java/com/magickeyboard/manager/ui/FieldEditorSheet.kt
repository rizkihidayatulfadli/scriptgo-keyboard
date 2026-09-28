@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.magickeyboard.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magickeyboard.contacts.Channels
import com.magickeyboard.contacts.FieldIcons
import com.magickeyboard.contacts.FieldKeys
import com.magickeyboard.contacts.UnlinkedVariable
import com.magickeyboard.manager.data.EDITABLE_FIELD_TYPES
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.data.labelRes
import com.magickeyboard.manager.ui.theme.FieldIconVectors
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.template.FieldCurrency
import com.magickeyboard.template.FieldType
import com.magickeyboard.template.formatFieldValue
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet to add or edit a custom field: its name (and the template variable it becomes), its
 * type as cards that show what a value looks like, choices as removable chips, the currency, and
 * the fallback used when a contact has no value.
 */
@Composable
fun FieldEditorSheet(
    field: FieldDefinition?,
    existingKeys: Set<String>,
    nextPosition: Int,
    onDismiss: () -> Unit,
    /** A template variable becoming a field: its key is kept, and its choices become the field's. */
    preset: UnlinkedVariable? = null,
) {
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var label by remember { mutableStateOf(field?.label ?: preset?.suggestedLabel.orEmpty()) }
    var type by remember { mutableStateOf(field?.type ?: if (preset?.options != null) FieldType.CHOICE else FieldType.TEXT) }
    val choices = remember {
        mutableStateListOf<String>().apply {
            if (field?.type == FieldType.CHOICE) addAll(field.options) else preset?.options?.let(::addAll)
        }
    }
    var newChoice by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(field?.currency ?: FieldCurrency.DEFAULT) }
    var fallback by remember { mutableStateOf(field?.fallback.orEmpty()) }
    // Empty = automatic: picked from the name and type as they are typed.
    var icon by remember { mutableStateOf(field?.icon.orEmpty()) }
    var pickingIcon by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val key = field?.key ?: preset?.key ?: FieldKeys.fromLabel(label, existingKeys)
    // The built-in Channel field: only its name and choices can change (see Channels).
    val builtIn = field?.key == Channels.KEY

    fun addChoice() {
        val text = newChoice.trim()
        if (text.isNotEmpty() && text !in choices) choices.add(text)
        newChoice = ""
    }

    // The field as currently edited, so the fallback input matches its type, choices, and currency.
    fun edited() = FieldDefinition(
        id = field?.id ?: "",
        key = key,
        label = label,
        type = type,
        options = when (type) {
            FieldType.CHOICE -> choices.toList()
            // IDR stays an empty list, as stored before currencies were added.
            FieldType.CURRENCY -> if (currency == FieldCurrency.DEFAULT) emptyList() else listOf(currency.code)
            else -> emptyList()
        },
        position = field?.position ?: nextPosition,
        // A removed choice can no longer be the fallback.
        fallback = if (type == FieldType.CHOICE && fallback !in choices) "" else fallback,
        icon = icon,
    )

    fun save() {
        addChoice()
        scope.launch {
            val saved = edited().copy(id = field?.id ?: ManagerRepository.newId())
            error = withContext(Dispatchers.IO) { repo.saveField(saved) }
            if (error == null) onDismiss()
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header: what this field is called and the variable it becomes.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable { pickingIcon = !pickingIcon }) {
                    FieldIconBadge(FieldIcons.resolve(icon, label, key, type.key), size = 52.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (field == null) R.string.sg_field_new else R.string.sg_field_edit),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sg_field_variable_label), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        VariableBadge("{$key}", Modifier.weight(1f, fill = false))
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text(stringResource(R.string.sg_field_name)) },
                supportingText = if (field != null || preset != null) ({ Text(stringResource(R.string.sg_field_key_fixed)) }) else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            IconPicker(
                icon = icon,
                auto = FieldIcons.pick(label, key, type.key),
                expanded = pickingIcon,
                onToggle = { pickingIcon = !pickingIcon },
                onPick = { icon = it },
            )

            if (builtIn) {
                Text(stringResource(R.string.sg_channel_auto), style = MaterialTheme.typography.bodySmall, color = c.muted)
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.sg_field_data_type))
                val types = (EDITABLE_FIELD_TYPES + listOfNotNull(field?.type)).distinct()
                types.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t ->
                            TypeCard(
                                icon = t.icon(),
                                label = stringResource(t.labelRes()),
                                sample = t.sample(currency),
                                selected = t == type,
                                onClick = {
                                    // A value typed for another type may not fit this one.
                                    if (t != type) fallback = ""
                                    type = t
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            if (type == FieldType.CHOICE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.sg_field_type_choice))
                    if (choices.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { choice -> ChoicePill(choice, onRemove = { choices.remove(choice) }) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newChoice,
                            onValueChange = { newChoice = it.take(40) },
                            placeholder = { Text(stringResource(R.string.sg_field_choice_add_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addChoice() }),
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.ink).clickable { addChoice() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ScriptGoIcons.Plus, contentDescription = stringResource(R.string.sg_field_choice_add), tint = c.onInk)
                        }
                    }
                }
            }

            if (type == FieldType.CURRENCY) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.sg_field_type_currency))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldCurrency.entries.forEach { option ->
                            val on = option == currency
                            Row(
                                Modifier.heightIn(min = 44.dp).clip(CircleShape)
                                    .background(if (on) c.ink else c.card)
                                    .border(1.5.dp, if (on) c.ink else c.line, CircleShape)
                                    .semantics { selected = on; role = Role.RadioButton }
                                    .clickable { currency = option }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(option.code, style = MaterialTheme.typography.labelLarge, color = if (on) c.onInk else c.ink)
                                Text(option.symbol.trim(), style = MaterialTheme.typography.bodySmall, color = if (on) c.onInk else c.muted)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fallbackLabel = stringResource(R.string.sg_field_fallback)
                FieldInput(edited().copy(label = fallbackLabel), fallback) { fallback = it.take(200) }
                Text(stringResource(R.string.sg_field_fallback_help), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, c.line),
                ) { Text(stringResource(R.string.sg_cancel)) }
                Button(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(ScriptGoIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sg_save), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (field != null && !builtIn) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, c.danger),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.danger),
                ) {
                    Icon(ScriptGoIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sg_field_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (confirmDelete && field != null) {
        ConfirmDeleteDialog(
            what = stringResource(R.string.sg_field_what, field.label),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteField(field.id) }
                    onDismiss()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The field's icon: automatic (picked from the name, [auto]) or one chosen from the gallery. The
 * gallery opens under the current icon; "Otomatis" goes back to following the name.
 */
@Composable
private fun IconPicker(icon: String, auto: String, expanded: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    val c = ScriptGo.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.sg_field_icon))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldIconBadge(icon.ifEmpty { auto })
            Text(
                stringResource(if (icon.isEmpty()) R.string.sg_field_icon_auto_hint else R.string.sg_field_icon_picked),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onToggle) {
                Text(stringResource(if (expanded) R.string.sg_field_icon_done else R.string.sg_field_icon_change))
            }
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(auto, selected = icon.isEmpty(), label = stringResource(R.string.sg_field_icon_auto), onClick = { onPick("") })
                FieldIcons.all.forEach { name -> IconTile(name, selected = icon == name, label = null, onClick = { onPick(name) }) }
            }
        }
    }
}

/** One icon in the gallery; [label] makes it a wider tile with text (the automatic choice). */
@Composable
private fun IconTile(name: String, selected: Boolean, label: String?, onClick: () -> Unit) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.height(48.dp).then(if (label == null) Modifier.size(48.dp) else Modifier)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accentSolid else c.line, shape)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(FieldIconVectors[name] ?: ScriptGoIcons.Type, contentDescription = label ?: name, tint = c.accentInk, modifier = Modifier.size(22.dp))
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = c.accentInk)
    }
}

/** A field type to pick: icon, name, and an example of how a value shows up in a message. */
@Composable
private fun TypeCard(icon: ImageVector, label: String, sample: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) c.accentSoft else c.bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accentSolid else c.line, shape)
            .semantics { this.selected = selected; role = Role.RadioButton }
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (selected) c.card else c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f))
            if (selected) Icon(ScriptGoIcons.Check, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sample, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One choice of a choice field, removable. */
@Composable
private fun ChoicePill(text: String, onRemove: () -> Unit) {
    val c = ScriptGo.colors
    val remove = stringResource(R.string.sg_field_choice_remove, text)
    Row(
        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(c.accentSoft).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.accentInk)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(ScriptGoIcons.Close, contentDescription = remove, tint = c.accentInk, modifier = Modifier.size(16.dp))
        }
    }
}

private fun FieldType.icon(): ImageVector = when (this) {
    FieldType.TEXT -> ScriptGoIcons.Type
    FieldType.LONGTEXT -> ScriptGoIcons.TextLines
    FieldType.NUMBER -> ScriptGoIcons.Hash
    FieldType.CURRENCY -> ScriptGoIcons.CreditCard
    FieldType.DATE -> ScriptGoIcons.Calendar
    FieldType.CHOICE -> ScriptGoIcons.Fields
    FieldType.PHONE -> ScriptGoIcons.Smartphone
    FieldType.EMAIL -> ScriptGoIcons.Mail
    FieldType.URL -> ScriptGoIcons.Globe
    FieldType.BOOLEAN -> ScriptGoIcons.Check
    FieldType.MULTICHOICE -> ScriptGoIcons.Fields
}

/** How a typical value of this type looks in a message, using the real formatter. */
@Composable
private fun FieldType.sample(currency: FieldCurrency): String = when (this) {
    FieldType.TEXT -> stringResource(R.string.sg_field_sample_text)
    FieldType.LONGTEXT -> stringResource(R.string.sg_field_sample_longtext)
    FieldType.NUMBER -> formatFieldValue(this, "1500")
    FieldType.CURRENCY -> formatFieldValue(this, if (currency.cents) "1500.5" else "150000", currency.code)
    FieldType.DATE -> formatFieldValue(this, "2026-09-26")
    FieldType.CHOICE -> stringResource(R.string.sg_field_sample_choice)
    FieldType.PHONE -> formatFieldValue(this, "+6281234567890")
    FieldType.EMAIL -> "budi@contoh.id"
    FieldType.URL -> "scriptgo.id"
    FieldType.BOOLEAN -> formatFieldValue(this, "true")
    FieldType.MULTICHOICE -> stringResource(R.string.sg_field_sample_choice)
}
