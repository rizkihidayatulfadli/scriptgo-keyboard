@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.magickeyboard.contacts.ColumnTarget
import com.magickeyboard.contacts.ContactData
import com.magickeyboard.contacts.ContactImport
import com.magickeyboard.contacts.Csv
import com.magickeyboard.contacts.DuplicatePolicy
import com.magickeyboard.contacts.ExistingContact
import com.magickeyboard.contacts.FieldKeys
import com.magickeyboard.contacts.ImportPlan
import com.magickeyboard.contacts.PhoneNumbers
import com.magickeyboard.manager.data.FieldDefinition
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.template.FieldType
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NEW_FIELD = "__new_field__"

/** Import contacts from a CSV file: pick file → map columns → choose duplicate handling → import. */
@Composable
fun ImportCsvScreen(back: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<List<String>>?>(null) }
    var fields by remember { mutableStateOf(emptyList<FieldDefinition>()) }
    var existing by remember { mutableStateOf(emptyList<ExistingContact>()) }
    val mapping = remember { mutableStateListOf<ColumnTarget>() }
    var policy by remember { mutableStateOf(DuplicatePolicy.MERGE) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) { readText(context.contentResolver, uri)?.let(Csv::parse) }
            if (parsed == null || parsed.size < 2) {
                message = context.getString(R.string.sg_import_bad_file)
                return@launch
            }
            val (defs, contacts) = withContext(Dispatchers.IO) { repo.fieldDefinitions() to repo.existingContacts() }
            fields = defs
            existing = contacts
            mapping.clear()
            mapping.addAll(ContactImport.suggestMapping(parsed.first(), defs.associate { it.key to it.label }))
            rows = parsed
            message = null
        }
    }

    val data = rows
    val plan: ImportPlan? = remember(data, mapping.toList(), policy, existing) {
        data?.let { ContactImport.planCsv(it.drop(1), mapping.toList(), existing, policy) }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.sg_import_csv_title), back) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.sg_import_csv_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) {
                Text(stringResource(if (data == null) R.string.sg_import_pick_csv else R.string.sg_import_change_file))
            }
            if (data != null && plan != null) {
                Text(stringResource(R.string.sg_import_columns, data.size - 1), style = MaterialTheme.typography.titleMedium)
                val targets = listOf(ColumnTarget.Ignore, ColumnTarget.Name, ColumnTarget.Phone) +
                    fields.map { ColumnTarget.Field(it.key) } + ColumnTarget.Field(NEW_FIELD)
                data.first().forEachIndexed { column, header ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(header.ifEmpty { stringResource(R.string.sg_import_column_n, column + 1) }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                data.getOrNull(1)?.getOrNull(column).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        Dropdown(
                            selected = mapping.getOrElse(column) { ColumnTarget.Ignore },
                            items = targets,
                            label = { target ->
                                when (target) {
                                    ColumnTarget.Ignore -> context.getString(R.string.sg_import_ignore)
                                    ColumnTarget.Name -> context.getString(R.string.sg_var_name)
                                    ColumnTarget.Phone -> context.getString(R.string.sg_var_phone)
                                    is ColumnTarget.Field ->
                                        if (target.key == NEW_FIELD) context.getString(R.string.sg_import_new_field)
                                        else fields.firstOrNull { it.key == target.key }?.label ?: target.key
                                }
                            },
                            onSelect = { target ->
                                if (target == ColumnTarget.Field(NEW_FIELD)) {
                                    scope.launch {
                                        val created = createTextField(repo, header.ifEmpty { context.getString(R.string.sg_import_column_n, column + 1) }, fields)
                                        fields = fields + created
                                        mapping[column] = ColumnTarget.Field(created.key)
                                    }
                                } else if (column < mapping.size) {
                                    mapping[column] = target
                                }
                            },
                        )
                    }
                }
                Text(stringResource(R.string.sg_import_duplicates), style = MaterialTheme.typography.titleMedium)
                PolicyChooser(policy) { policy = it }
                Text(
                    stringResource(R.string.sg_import_plan, plan.creates, plan.updates, plan.skips) +
                        (if (plan.emptyRows > 0) " " + stringResource(R.string.sg_import_empty_rows, plan.emptyRows) else "") +
                        (if (plan.invalidPhones > 0) " " + stringResource(R.string.sg_import_invalid_phones, plan.invalidPhones) else ""),
                )
                if (ColumnTarget.Name !in mapping && ColumnTarget.Phone !in mapping) {
                    Text(stringResource(R.string.sg_import_need_column), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    enabled = !busy && plan.creates + plan.updates > 0,
                    onClick = {
                        busy = true
                        scope.launch {
                            val written = withContext(Dispatchers.IO) { repo.applyImport(plan) }
                            busy = false
                            message = context.getString(R.string.sg_import_done, written)
                            rows = null
                        }
                    },
                ) { Text(stringResource(R.string.sg_import_n, plan.creates + plan.updates)) }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** Import contacts saved on the phone. Needs the contacts permission, asked for on this screen. */
@Composable
fun ImportPhoneScreen(back: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var candidates by remember { mutableStateOf<List<ContactData>?>(null) }
    val selected = remember { mutableStateListOf<Int>() }
    var policy by remember { mutableStateOf(DuplicatePolicy.MERGE) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { readPhoneContacts(context.contentResolver) }
        candidates = loaded
        selected.clear()
        selected.addAll(loaded.indices)
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.sg_import_phone_title), back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            if (!granted) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.sg_import_permission),
                    )
                    Button(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.sg_grant_permission)) }
                }
                return@Column
            }
            val list = candidates ?: return@Column
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.sg_import_phone_none))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected.size == list.size,
                    onCheckedChange = { all ->
                        selected.clear()
                        if (all) selected.addAll(list.indices)
                    },
                )
                Text(stringResource(R.string.sg_import_select_all, selected.size, list.size))
            }
            PolicyChooser(policy) { policy = it }
            Button(
                enabled = !busy && selected.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val written = withContext(Dispatchers.IO) {
                            val chosen = selected.sorted().map { list[it] }
                            repo.applyImport(ContactImport.plan(chosen, repo.existingContacts(), policy))
                        }
                        busy = false
                        message = context.getString(R.string.sg_import_done, written)
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp),
            ) { Text(stringResource(R.string.sg_import_n, selected.size)) }
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.indices.toList()) { index ->
                    val contact = list[index]
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (index in selected) selected.remove(index) else selected.add(index)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = index in selected, onCheckedChange = null)
                        Column {
                            Text(contact.name, style = MaterialTheme.typography.titleSmall)
                            Text(contact.phones.joinToString { displayPhone(it) }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PolicyChooser(policy: DuplicatePolicy, onChange: (DuplicatePolicy) -> Unit) {
    val options = listOf(
        DuplicatePolicy.MERGE to stringResource(R.string.sg_policy_merge),
        DuplicatePolicy.OVERWRITE to stringResource(R.string.sg_policy_overwrite),
        DuplicatePolicy.SKIP to stringResource(R.string.sg_policy_skip),
    )
    Column {
        options.forEach { (option, label) ->
            Row(Modifier.fillMaxWidth().clickable { onChange(option) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = policy == option, onClick = { onChange(option) })
                Text(label)
            }
        }
    }
}

private suspend fun createTextField(repo: ManagerRepository, label: String, fields: List<FieldDefinition>): FieldDefinition {
    val field = FieldDefinition(
        id = ManagerRepository.newId(),
        key = FieldKeys.fromLabel(label, fields.map { it.key }.toSet()),
        label = label,
        type = FieldType.TEXT,
        position = fields.size,
    )
    withContext(Dispatchers.IO) { repo.saveField(field) }
    return field
}

private fun readText(resolver: ContentResolver, uri: Uri): String? =
    runCatching { resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()

/** Contacts on the phone that have at least one valid phone number, sorted by name. */
private fun readPhoneContacts(resolver: ContentResolver): List<ContactData> {
    val byContact = LinkedHashMap<Long, Pair<String, MutableList<String>>>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val name = cursor.getString(1).orEmpty()
            val phone = cursor.getString(2)?.let { PhoneNumbers.normalize(it) }
            if (phone != null) {
                val entry = byContact.getOrPut(id) { name to mutableListOf() }
                if (phone !in entry.second) entry.second += phone
            }
        }
    }
    return byContact.values
        .map { (name, phones) -> ContactData(name, phones) }
        .sortedBy { it.name.lowercase() }
}
