@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.magickeyboard.contacts.PhoneNumbers
import com.magickeyboard.manager.data.Contact
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.ui.theme.FieldIconVectors
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.template.FieldType
import com.magickeyboard.template.formatFieldValue
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ContactsScreen(navigate: (Screen) -> Unit, nav: TabNav) {
    val repo = rememberRepository()
    val revision by repo.changes.collectAsState()
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(revision, query) { contacts = withContext(Dispatchers.IO) { repo.contacts(query) } }

    TabScaffold(
        title = stringResource(R.string.sg_tab_contacts),
        nav = nav,
        onAdd = { navigate(Screen.EditContact(null)) },
        actions = {
            Box {
                CircleIconButton(ScriptGoIcons.Import, stringResource(R.string.sg_contacts_import), onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sg_contacts_from_csv)) },
                        onClick = {
                            menuOpen = false
                            navigate(Screen.ImportCsv)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sg_contacts_from_phone)) },
                        onClick = {
                            menuOpen = false
                            navigate(Screen.ImportPhone)
                        },
                    )
                }
            }
        },
    ) { padding ->
        val list = contacts
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SearchPill(query, { query = it }, stringResource(R.string.sg_contacts_search)) }
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    EmptyCard(
                        if (query.isBlank()) stringResource(R.string.sg_contacts_empty)
                        else stringResource(R.string.sg_contacts_no_match)
                    )
                }
                else -> items(list, key = { it.id }) { contact ->
                    ListCard(
                        title = contact.name,
                        subtitle = contact.phones.firstOrNull()?.let(::displayPhone),
                        onClick = { navigate(Screen.EditContact(contact.id)) },
                        leading = { InitialAvatar(contact.name) },
                    )
                }
            }
        }
    }
}

@Composable
fun ContactEditorScreen(id: String?, back: () -> Unit) {
    val repo = rememberRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    val contactId = remember { id ?: ManagerRepository.newId() }
    var name by remember { mutableStateOf("") }
    var phones by remember { mutableStateOf("") }
    val values = remember { mutableStateMapOf<String, String>() }
    var fields by remember { mutableStateOf(emptyList<FieldDefinition>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (contact, defs) = withContext(Dispatchers.IO) { (id?.let(repo::contact)) to repo.fieldDefinitions() }
        fields = defs
        if (contact != null) {
            name = contact.name
            phones = contact.phones.joinToString("\n") { displayPhone(it) }
            values.putAll(contact.fields)
        }
        loaded = true
    }

    fun save() {
        val lines = phones.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val normalized = lines.map { it to PhoneNumbers.normalize(it) }
        val invalid = normalized.filter { it.second == null }.map { it.first }
        if (invalid.isNotEmpty()) {
            error = context.getString(R.string.sg_contact_invalid_numbers, invalid.joinToString())
            return
        }
        scope.launch {
            val contact = Contact(
                id = contactId,
                name = name,
                phones = normalized.mapNotNull { it.second }.distinct(),
                fields = values.toMap(),
            )
            error = withContext(Dispatchers.IO) { repo.saveContact(contact) }
            if (error == null) back()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(stringResource(if (id == null) R.string.sg_contact_new else R.string.sg_contact_edit), back) {
                if (id != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.sg_delete)) }
                TextButton(onClick = ::save, enabled = loaded) { Text(stringResource(R.string.sg_save)) }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ContactHeader(name, phones.lines().firstOrNull { it.isNotBlank() }.orEmpty())

            // The two values every contact has, then the custom fields, each with its icon and label.
            SectionLabel(stringResource(R.string.sg_contact_section_main), Modifier.padding(start = 4.dp))
            BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledRow("user", stringResource(R.string.sg_var_name)) {
                        EndCaretTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = null,
                            placeholder = stringResource(R.string.sg_contact_name_hint),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            singleLine = true,
                        )
                    }
                    LabeledRow("phone", stringResource(R.string.sg_contact_phones)) {
                        EndCaretTextField(
                            value = phones,
                            onValueChange = { phones = it },
                            label = null,
                            placeholder = stringResource(R.string.sg_contact_phones_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.sg_contact_section_details), Modifier.padding(start = 4.dp, top = 4.dp))
            if (fields.isEmpty()) {
                EmptyCard(stringResource(R.string.sg_contact_add_fields_hint))
            } else {
                BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        fields.forEach { field ->
                            LabeledRow(field.shownIcon, field.label) {
                                FieldInput(field, values[field.key].orEmpty(), labelAbove = true) { values[field.key] = it }
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = stringResource(R.string.sg_contact_what),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteContact(contactId) }
                    back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The contact at a glance: a large initial avatar, the name, and the first number as typed. */
@Composable
private fun ContactHeader(name: String, phone: String) {
    val c = ScriptGo.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = c.onAccent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name.ifBlank { stringResource(R.string.sg_contact_new) },
                style = MaterialTheme.typography.headlineSmall,
                color = if (name.isBlank()) c.muted else c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
        }
    }
}

/** One value of the contact: its field icon, its name always shown above, and the input. */
@Composable
private fun LabeledRow(icon: String, label: String, input: @Composable () -> Unit) {
    val c = ScriptGo.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.padding(top = 34.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(FieldIconVectors[icon] ?: ScriptGoIcons.Type, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            input()
        }
    }
}

/**
 * Edits one custom field's raw stored value, with an input suited to its type. [imeAction] and
 * [onImeAction] let a form step to the next field from the keyboard ("Lanjut") or submit on the last.
 */
@Composable
internal fun FieldInput(
    field: FieldDefinition,
    raw: String,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxWidth(),
    /** The caller shows the field's name above (the contact editor), so the input shows a hint instead. */
    labelAbove: Boolean = false,
    onChange: (String) -> Unit,
) {
    val actions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }, onGo = { onImeAction() })
    val label = field.label.takeUnless { labelAbove }
    val hint = if (labelAbove) stringResource(R.string.sg_field_value_hint, field.label) else null
    when (field.type) {
        FieldType.CHOICE -> Column {
            val emptyLabel = stringResource(R.string.sg_empty_option)
            if (!labelAbove) Text(field.label, style = MaterialTheme.typography.labelLarge)
            Dropdown(
                selected = raw,
                items = listOf("") + field.options,
                label = { it.ifEmpty { emptyLabel } },
                onSelect = onChange,
            )
        }
        FieldType.DATE -> DateInput(label, raw, onChange)
        FieldType.NUMBER, FieldType.CURRENCY -> EndCaretTextField(
            value = raw,
            onValueChange = { onChange(numericInput(field, it)) },
            label = label,
            placeholder = hint,
            supportingText = if (raw.isNotEmpty()) {
                stringResource(R.string.sg_contact_shown_as, formatFieldValue(field.type, raw, field.currency.code))
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.type == FieldType.CURRENCY && field.currency.cents) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = imeAction,
            ),
            keyboardActions = actions,
            singleLine = true,
            modifier = modifier,
        )
        FieldType.LONGTEXT -> EndCaretTextField(
            value = raw,
            onValueChange = onChange,
            label = label,
            placeholder = hint,
            minLines = 3,
            modifier = modifier,
        )
        else -> EndCaretTextField(
            value = raw,
            onValueChange = onChange,
            label = label,
            placeholder = hint,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = actions,
            singleLine = true,
            modifier = modifier,
        )
    }
}

/** Keeps a typed number in its stored form: a dot for decimals, and whole amounts for IDR-like currencies. */
private fun numericInput(field: FieldDefinition, typed: String): String {
    val dotted = typed.replace(',', '.')
    return when {
        field.type != FieldType.CURRENCY -> dotted
        field.currency.cents -> dotted.filter { it.isDigit() || it == '-' || it == '.' }
        else -> typed.filter { it.isDigit() || it == '-' }
    }
}

/**
 * A text field for a String value whose caret starts at the end of what is already there (a
 * prefilled "Kak" is continued, not typed in front of), and keeps the user's caret while editing.
 */
@Composable
internal fun EndCaretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    supportingText: String? = null,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    var state by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Follow changes made from outside (a filter, a contact picked later) and put the caret at the end.
    if (state.text != value) state = TextFieldValue(value, TextRange(value.length))
    OutlinedTextField(
        value = state,
        onValueChange = {
            state = it
            if (it.text != value) onValueChange(it.text)
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier,
    )
}

@Composable
private fun DateInput(label: String?, raw: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }) {
            Text(if (raw.isEmpty()) stringResource(R.string.sg_pick_date) else formatFieldValue(FieldType.DATE, raw))
        }
    }
    if (open) {
        val initial = runCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onChange("")
                    open = false
                }) { Text(stringResource(R.string.sg_clear)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
