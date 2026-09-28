package com.magickeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magickeyboard.contacts.ContactData
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.magickeyboard.contacts.PhoneNumbers
import com.magickeyboard.contacts.Channels
import com.magickeyboard.contacts.TemplateForm
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.data.Contact
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.data.Template
import com.magickeyboard.manager.data.currencies
import com.magickeyboard.manager.data.fallbacks
import com.magickeyboard.manager.ui.CircleIconButton
import com.magickeyboard.manager.ui.EndCaretTextField
import com.magickeyboard.manager.ui.VariableBadge
import com.magickeyboard.manager.ui.variableLabel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.magickeyboard.manager.ui.FieldInput
import com.magickeyboard.manager.ui.InitialAvatar
import com.magickeyboard.manager.ui.LetterBadge
import com.magickeyboard.manager.ui.ManagerTheme
import com.magickeyboard.manager.ui.OutlineChip
import com.magickeyboard.manager.ui.SearchPill
import com.magickeyboard.manager.ui.SectionLabel
import com.magickeyboard.manager.ui.displayPhone
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.SupabaseSync
import com.magickeyboard.template.TemplateParser
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown above the keyboard when a template with variables is picked: stringResource(R.string.sg_form_who) (optional
 * contact picker), then the variable form. The keyboard types into this activity's fields; the
 * result goes back to [LatinIME], which inserts it once the original app has focus again.
 */
class TemplateFormActivity : ComponentActivity() {
    private var result: Intent? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        val triggerLength = intent.getIntExtra(EXTRA_TRIGGER_LENGTH, 0)
        if (templateId == null) {
            finish()
            return
        }
        setContent {
            ManagerTheme {
                FormRoot(
                    templateId = templateId,
                    sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE),
                    onCancel = { finish() },
                    onDone = { text, cursorFromEnd ->
                        result = Intent(this, LatinIME::class.java).setAction(ACTION_DONE)
                            .putExtra(EXTRA_TEXT, text)
                            .putExtra(EXTRA_CURSOR_FROM_END, cursorFromEnd)
                            .putExtra(EXTRA_TRIGGER_LENGTH, triggerLength)
                            .putExtra(EXTRA_TEMPLATE_ID, templateId)
                        finish()
                    },
                )
            }
        }
    }

    /** Like HeliBoard's emoji search: hand the result to the keyboard service when leaving. */
    override fun onStop() {
        result?.let {
            result = null
            startService(it)
        }
        super.onStop()
    }

    companion object {
        const val ACTION_DONE = "com.magickeyboard.TEMPLATE_FORM_DONE"
        const val EXTRA_TEMPLATE_ID = "template_id"
        const val EXTRA_TRIGGER_LENGTH = "trigger_length"
        /** Package name of the app the keyboard was typing into (only the name, never its content). */
        const val EXTRA_SOURCE_PACKAGE = "source_package"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CURSOR_FROM_END = "cursor_from_end"

        fun start(context: Context, templateId: String, triggerLength: Int, sourcePackage: String? = null) {
            context.startActivity(
                Intent(context, TemplateFormActivity::class.java)
                    .putExtra(EXTRA_TEMPLATE_ID, templateId)
                    .putExtra(EXTRA_TRIGGER_LENGTH, triggerLength)
                    .putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            )
        }
    }
}

private class FormData(val template: Template, val fields: List<FieldDefinition>, val askContact: Boolean, val trigger: Char)

@Composable
private fun FormRoot(templateId: String, sourcePackage: String?, onCancel: () -> Unit, onDone: (String, Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ManagerRepository.get(context) }
    var data by remember { mutableStateOf<FormData?>(null) }
    // null = still choosing; Skipped = continue without a contact
    var chosen by remember { mutableStateOf<ContactChoice?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            repo.template(templateId)?.let { FormData(it, repo.fieldDefinitions(), repo.askContact(), repo.triggerChar()) }
        }
        if (loaded == null) onCancel() else {
            data = loaded
            if (!loaded.askContact) chosen = ContactChoice.Skipped
        }
    }

    // Tapping the dimmed area above the panel cancels.
    Column(
        Modifier.fillMaxSize().background(Color(0x660E1116)).clickable(onClick = onCancel)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Surface(
            Modifier.fillMaxWidth().clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            val loaded = data ?: return@Surface
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Grab handle, as on a bottom sheet.
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(ScriptGo.colors.line))
                val contact = (chosen as? ContactChoice.Picked)?.contact
                SheetHeader(loaded, subtitle = contact?.let { stringResource(R.string.sg_form_for, it.name) }, onClose = onCancel)
                when (chosen) {
                    null -> ContactPicker(loaded.fields, sourcePackage, onPick = { chosen = it })
                    else -> VariableForm(loaded, contact, onDone)
                }
            }
        }
    }
}

private sealed interface ContactChoice {
    data object Skipped : ContactChoice
    data class Picked(val contact: Contact) : ContactChoice
}

/** Template letter badge, title, `/shortcut` chip, and a close button. */
@Composable
private fun SheetHeader(data: FormData, subtitle: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LetterBadge(data.template.title, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(data.template.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineChip("${data.trigger}${data.template.shortcut}")
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ScriptGo.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        CircleIconButton(ScriptGoIcons.Close, stringResource(R.string.sg_close), onClick = onClose)
    }
}

@Composable
private fun ContactPicker(fields: List<FieldDefinition>, sourcePackage: String?, onPick: (ContactChoice) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ManagerRepository.get(context) }
    val c = ScriptGo.colors
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        NewContactForm(fields, sourcePackage, initial = query, onSaved = { onPick(ContactChoice.Picked(it)) }, onBack = { adding = false })
        return
    }
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(query) { contacts = withContext(Dispatchers.IO) { repo.recentContacts(query) } }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.sg_form_who))
        SearchPill(query, { query = it }, stringResource(R.string.sg_form_search), fieldModifier = Modifier.focusRequester(focus))
        if (contacts.isEmpty()) {
            Text(
                if (query.isBlank()) stringResource(R.string.sg_form_no_contacts)
                else stringResource(R.string.sg_form_no_match, query),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 232.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(contacts, key = { it.id }) { contact ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onPick(ContactChoice.Picked(contact)) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InitialAvatar(contact.name)
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        contact.phones.firstOrNull()?.let {
                            Text(displayPhone(it), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                    Icon(ScriptGoIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(ScriptGoIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.sg_form_new_contact), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { onPick(ContactChoice.Skipped) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.sg_form_skip_contact))
        }
    }
}

/**
 * A new contact made right in the form: name, number, and the channel, filled in from the app the
 * keyboard was typing into ([sourcePackage]). What was typed in the search box becomes the name, or
 * the number when it is one.
 */
@Composable
private fun NewContactForm(
    fields: List<FieldDefinition>,
    sourcePackage: String?,
    initial: String,
    onSaved: (Contact) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ManagerRepository.get(context) }
    val scope = rememberCoroutineScope()
    val channelField = fields.firstOrNull { it.key == Channels.KEY }
    val typedNumber = initial.isNotBlank() && initial.all { it.isDigit() || it in "+-() " }
    var name by remember { mutableStateOf(if (typedNumber) "" else initial.trim()) }
    var phone by remember { mutableStateOf(if (typedNumber) initial.trim() else "") }
    var channel by remember { mutableStateOf(channelField?.let { Channels.optionFor(sourcePackage, it.options) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        val number = phone.trim()
        val normalized = if (number.isEmpty()) null else PhoneNumbers.normalize(number)
        if (number.isNotEmpty() && normalized == null) {
            error = AppLanguage.string(context, R.string.sg_contact_invalid_numbers, number)
            return
        }
        saving = true
        scope.launch {
            val contact = Contact(
                id = ManagerRepository.newId(),
                name = name,
                phones = listOfNotNull(normalized),
                fields = if (channelField != null && channel.isNotEmpty()) mapOf(Channels.KEY to channel) else emptyMap(),
            )
            val failure = withContext(Dispatchers.IO) { repo.saveContact(contact) }
            saving = false
            if (failure != null) error = failure else onSaved(contact.copy(name = contact.name.trim()))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.sg_form_new_contact))
        EndCaretTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = stringResource(R.string.sg_var_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        EndCaretTextField(
            value = phone,
            onValueChange = { phone = it; error = null },
            label = stringResource(R.string.sg_form_new_contact_phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) save() }),
            singleLine = true,
        )
        if (channelField != null) {
            FieldInput(channelField, channel) { channel = it }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.sg_back))
            }
            Button(onClick = { save() }, enabled = name.isNotBlank() && !saving, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.sg_form_new_contact_save))
            }
        }
    }
}

@Composable
private fun VariableForm(data: FormData, contact: Contact?, onDone: (String, Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ManagerRepository.get(context) }
    val c = ScriptGo.colors
    val scope = rememberCoroutineScope()
    val parsed = remember { TemplateParser.parse(data.template.body) }
    val fieldsByKey = remember { data.fields.associateBy { it.key } }
    val fieldTypes = remember { data.fields.associate { it.key to it.type } }
    val currencies = remember { data.fields.currencies() }
    val fieldFallbacks = remember { data.fields.fallbacks() }
    val contactData = remember { contact?.let { ContactData(it.name, it.phones, it.fields) } }
    val values = remember {
        mutableStateMapOf<String, String>().apply { putAll(TemplateForm.prefill(parsed.variables, contactData, fieldTypes, fieldFallbacks)) }
    }
    // Prefilled from a fallback (the template's or the field's) because this contact has nothing for them.
    val fromFallback = remember { TemplateForm.fallbackKeys(parsed.variables, contactData, fieldTypes, fieldFallbacks) }
    var saveToContact by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Nothing to ask (all variables filled or none left): insert right away.
    if (parsed.variables.isEmpty()) {
        LaunchedEffect(Unit) { submit(data, parsed, values.toMap(), fieldTypes, null, false, repo, onDone) }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val labels = remember { data.fields.associate { it.key to it.label } }
            val last = parsed.variables.lastIndex
            parsed.variables.forEachIndexed { index, variable ->
                val key = variable.key
                val field = fieldsByKey[key]
                val raw = values[key].orEmpty()
                val modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(focus) else Modifier.fillMaxWidth()
                // The keyboard's action key reads "Lanjut" (next field) and "Sisipkan" (done) on the last one.
                val imeAction = if (index == last) ImeAction.Done else ImeAction.Next
                val onImeAction = {
                    if (index == last) scope.launch { submit(data, parsed, values.toMap(), fieldTypes, contact, saveToContact, repo, onDone) }
                    else focusManager.moveFocus(FocusDirection.Next)
                    Unit
                }
                when {
                    field != null -> FieldInput(field, raw, imeAction, onImeAction, modifier) { values[key] = it }
                    variable.options != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VariableBadge(variableLabel(key, labels))
                        val emptyLabel = stringResource(R.string.sg_empty_option)
                        com.magickeyboard.manager.ui.Dropdown(
                            selected = raw,
                            items = variable.options.orEmpty(),
                            label = { it.ifEmpty { emptyLabel } },
                            onSelect = { values[key] = it },
                        )
                    }
                    else -> EndCaretTextField(
                        value = raw,
                        onValueChange = { values[key] = it },
                        label = variableLabel(key, labels),
                        keyboardOptions = KeyboardOptions(imeAction = imeAction),
                        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
                        singleLine = true,
                        modifier = modifier,
                    )
                }
                // Shown while the value is still the fallback; typing something else removes it.
                if (key in fromFallback && raw == (variable.default ?: fieldFallbacks[key])) FallbackNote()
            }
            // Live preview of the message as a chat bubble.
            val preview = runCatching { TemplateForm.render(parsed, values.toMap(), fieldTypes, LocalDateTime.now(), currencies).text }.getOrNull()
            if (!preview.isNullOrBlank()) {
                SectionLabel(stringResource(R.string.sg_preview))
                Box(
                    Modifier.align(Alignment.End).fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                        .background(c.accentSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val changes = contactData?.let { TemplateForm.changedFields(values, it, fieldTypes, fieldFallbacks) }.orEmpty()
        if (contact != null && changes.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg)
                    .clickable { saveToContact = !saveToContact }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sg_form_save_to_contact, contact.name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = saveToContact, onCheckedChange = { saveToContact = it })
            }
        }
        Button(
            onClick = { scope.launch { submit(data, parsed, values.toMap(), fieldTypes, contact, saveToContact, repo, onDone) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(ScriptGoIcons.Insert, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sg_insert))
        }
    }
}

private suspend fun submit(
    data: FormData,
    parsed: com.magickeyboard.template.ParsedTemplate,
    values: Map<String, String>,
    fieldTypes: Map<String, com.magickeyboard.template.FieldType>,
    contact: Contact?,
    saveToContact: Boolean,
    repo: ManagerRepository,
    onDone: (String, Int) -> Unit,
) {
    val result = TemplateForm.render(parsed, values, fieldTypes, LocalDateTime.now(), data.fields.currencies())
    withContext(Dispatchers.IO) {
        if (contact != null) {
            val changes = if (saveToContact) {
                TemplateForm.changedFields(values, ContactData(contact.name, contact.phones, contact.fields), fieldTypes, data.fields.fallbacks())
            } else {
                emptyMap()
            }
            repo.useContact(contact.id, changes)
        }
        repo.recordUsage(data.template.id, result.text.length, SupabaseSync.get(repo.appContext).freeLimited())
    }
    onDone(result.text, result.cursor?.let { result.text.length - it } ?: 0)
}

/** A small note under a form input whose value came from the template's fallback. */
@Composable
private fun FallbackNote() {
    val c = ScriptGo.colors
    Row(
        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(c.bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accentSolid))
        Text(stringResource(R.string.sg_form_from_fallback), style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}
