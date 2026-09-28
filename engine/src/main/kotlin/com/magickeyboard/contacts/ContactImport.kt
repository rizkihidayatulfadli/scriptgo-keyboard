package com.magickeyboard.contacts

/** Where a CSV column goes during import. */
sealed interface ColumnTarget {
    data object Ignore : ColumnTarget
    data object Name : ColumnTarget
    data object Phone : ColumnTarget
    data class Field(val key: String) : ColumnTarget
}

/** What to do when an imported contact shares a phone number with an existing one. */
enum class DuplicatePolicy {
    /** Keep the existing contact untouched. */
    SKIP,

    /** Imported non-empty values replace existing ones. */
    OVERWRITE,

    /** Imported values only fill fields that are empty on the existing contact. */
    MERGE,
}

/** A contact as stored: [phones] are E.164, [fields] maps field keys to raw stored values. */
data class ContactData(
    val name: String,
    val phones: List<String> = emptyList(),
    val fields: Map<String, String> = emptyMap(),
)

data class ExistingContact(val id: String, val data: ContactData)

sealed interface ImportAction {
    data class Create(val contact: ContactData) : ImportAction
    data class Update(val id: String, val contact: ContactData) : ImportAction
    data class Skip(val contact: ContactData) : ImportAction
}

data class ImportPlan(
    val actions: List<ImportAction>,
    /** Rows with neither a name nor a valid phone number. */
    val emptyRows: Int,
    /** Phone values that could not be normalized and were dropped. */
    val invalidPhones: Int,
) {
    val creates get() = actions.count { it is ImportAction.Create }
    val updates get() = actions.count { it is ImportAction.Update }
    val skips get() = actions.count { it is ImportAction.Skip }
}

/** Pure planning for CSV and phone-contact imports; the app applies the resulting [ImportPlan]. */
object ContactImport {
    private val NAME_HEADERS = setOf("nama", "name", "nama lengkap", "full name", "nama customer", "nama pelanggan")
    private val PHONE_HEADERS = setOf(
        "nomor", "no hp", "nomor hp", "no. hp", "hp", "phone", "phone number", "telepon", "no telepon",
        "nomor telepon", "whatsapp", "wa", "no wa", "nomor wa", "mobile",
    )

    /**
     * Guesses a target per header: name and phone by common Indonesian and English headers, custom
     * fields by matching a field's label or key (case-insensitive). Everything else is ignored.
     */
    fun suggestMapping(header: List<String>, fieldLabelsByKey: Map<String, String>): List<ColumnTarget> {
        var nameTaken = false
        return header.map { raw ->
            val h = raw.trim().lowercase()
            val fieldKey = fieldLabelsByKey.entries
                .firstOrNull { (key, label) -> h == key.lowercase() || h == label.trim().lowercase() }
                ?.key
            when {
                h in NAME_HEADERS && !nameTaken -> ColumnTarget.Name.also { nameTaken = true }
                h in PHONE_HEADERS -> ColumnTarget.Phone
                fieldKey != null -> ColumnTarget.Field(fieldKey)
                else -> ColumnTarget.Ignore
            }
        }
    }

    /** Plans a CSV import of data [rows] (header excluded) using [mapping] per column. */
    fun planCsv(
        rows: List<List<String>>,
        mapping: List<ColumnTarget>,
        existing: List<ExistingContact>,
        policy: DuplicatePolicy,
        defaultRegion: String = PhoneNumbers.DEFAULT_REGION,
    ): ImportPlan {
        var invalidPhones = 0
        val contacts = rows.map { row ->
            var name = ""
            val phones = mutableListOf<String>()
            val fields = mutableMapOf<String, String>()
            row.forEachIndexed { column, value ->
                if (value.isEmpty()) return@forEachIndexed
                when (val target = mapping.getOrElse(column) { ColumnTarget.Ignore }) {
                    ColumnTarget.Ignore -> Unit
                    ColumnTarget.Name -> name = value
                    ColumnTarget.Phone -> {
                        val normalized = PhoneNumbers.normalize(value, defaultRegion)
                        if (normalized == null) invalidPhones++ else phones += normalized
                    }
                    is ColumnTarget.Field -> fields[target.key] = value
                }
            }
            ContactData(name, phones.distinct(), fields)
        }
        return plan(contacts, existing, policy).let { it.copy(invalidPhones = it.invalidPhones + invalidPhones) }
    }

    /**
     * Plans importing already-normalized [incoming] contacts. A contact is a duplicate when it shares
     * a phone number with an existing contact or with an earlier contact in [incoming]; duplicates
     * within the same import are always merged into the first occurrence.
     */
    fun plan(incoming: List<ContactData>, existing: List<ExistingContact>, policy: DuplicatePolicy): ImportPlan {
        var emptyRows = 0
        val existingByPhone = HashMap<String, ExistingContact>()
        existing.forEach { contact -> contact.data.phones.forEach { existingByPhone.putIfAbsent(it, contact) } }

        // Accumulates per target: an existing contact id, or a new contact slot.
        val updates = LinkedHashMap<String, ContactData>()
        val skipped = LinkedHashMap<String, ContactData>()
        val creates = mutableListOf<ContactData>()
        val createByPhone = HashMap<String, Int>()

        for (contact in incoming) {
            if (contact.name.isEmpty() && contact.phones.isEmpty()) {
                emptyRows++
                continue
            }
            val match = contact.phones.firstNotNullOfOrNull { existingByPhone[it] }
            val pending = contact.phones.firstNotNullOfOrNull { createByPhone[it] }
            when {
                match != null && policy == DuplicatePolicy.SKIP -> skipped.putIfAbsent(match.id, contact)
                match != null -> {
                    val base = updates[match.id] ?: match.data
                    updates[match.id] = combine(base, contact, policy)
                }
                pending != null -> {
                    creates[pending] = combine(creates[pending], contact, DuplicatePolicy.MERGE)
                    creates[pending].phones.forEach { createByPhone.putIfAbsent(it, pending) }
                }
                else -> {
                    creates += contact
                    contact.phones.forEach { createByPhone.putIfAbsent(it, creates.lastIndex) }
                }
            }
        }

        val actions = creates.map { ImportAction.Create(it.withFallbackName()) } +
            updates.map { (id, data) -> ImportAction.Update(id, data) } +
            skipped.values.map { ImportAction.Skip(it.withFallbackName()) }
        return ImportPlan(actions, emptyRows, invalidPhones = 0)
    }

    private fun combine(base: ContactData, incoming: ContactData, policy: DuplicatePolicy): ContactData {
        val phones = (base.phones + incoming.phones).distinct()
        return when (policy) {
            DuplicatePolicy.OVERWRITE -> ContactData(
                name = incoming.name.ifEmpty { base.name },
                phones = phones,
                fields = base.fields + incoming.fields.filterValues { it.isNotEmpty() },
            )
            else -> ContactData(
                name = base.name.ifEmpty { incoming.name },
                phones = phones,
                fields = incoming.fields.filterValues { it.isNotEmpty() } + base.fields.filterValues { it.isNotEmpty() },
            )
        }
    }

    /** A contact imported with only a phone number is named after that number. */
    private fun ContactData.withFallbackName(): ContactData =
        if (name.isNotEmpty()) this else copy(name = phones.firstOrNull()?.let(::displayPhone).orEmpty())

    private fun displayPhone(e164: String): String = if (e164.startsWith("+62")) "0" + e164.substring(3) else e164
}
