package com.magickeyboard.contacts

import com.magickeyboard.template.Contact
import com.magickeyboard.template.FieldType
import com.magickeyboard.template.ParsedTemplate
import com.magickeyboard.template.RenderResult
import com.magickeyboard.template.TemplateRenderer
import com.magickeyboard.template.Variable
import com.magickeyboard.template.formatFieldValue
import java.time.LocalDateTime

/**
 * The variable form shown before inserting a template. Form values are raw: for a variable whose
 * key is a custom field they are stored field values (`150000`), formatted only when rendering
 * (`Rp150.000`); other variables hold plain text. `fieldFallbacks` maps a field key to the raw
 * value its field uses when a contact has none (`FieldDefinition.fallback`); a template's own
 * fallback (`{kota|Bandung}`) or options win over it.
 */
object TemplateForm {
    /**
     * Initial raw values: the contact's value, else the default, else the first option, else the
     * field's fallback, else empty.
     */
    fun prefill(
        variables: List<Variable>,
        contact: ContactData?,
        fieldTypes: Map<String, FieldType>,
        fieldFallbacks: Map<String, String> = emptyMap(),
    ): Map<String, String> =
        variables.associate { variable ->
            val key = variable.key
            val fromContact = when {
                contact == null -> null
                key == "nama" -> contact.name
                key == "nomor" -> contact.phones.firstOrNull()?.let { formatFieldValue(FieldType.PHONE, it) }
                key in fieldTypes -> contact.fields[key]
                else -> null
            }?.takeIf { it.isNotEmpty() }
            key to (fromContact ?: TemplateRenderer.prefill(variable, contact = null, fieldFallbacks = fieldFallbacks))
        }

    /**
     * Keys whose prefilled value is a fallback, the template's (`{kota|Bandung}`) or the field's,
     * because [contact] has nothing for them, so the form can say so. Only variables a contact can
     * fill count: `nama`, `nomor`, and custom fields.
     */
    fun fallbackKeys(
        variables: List<Variable>,
        contact: ContactData?,
        fieldTypes: Map<String, FieldType>,
        fieldFallbacks: Map<String, String> = emptyMap(),
    ): Set<String> {
        if (contact == null) return emptySet()
        return variables.filter { variable ->
            val key = variable.key
            val fromContact = when {
                key == "nama" -> contact.name
                key == "nomor" -> contact.phones.firstOrNull()
                key in fieldTypes -> contact.fields[key]
                else -> return@filter false
            }
            val fromField = variable.default == null && variable.options == null && !fieldFallbacks[key].isNullOrEmpty()
            fromContact.isNullOrEmpty() && (!variable.default.isNullOrEmpty() || fromField)
        }.map { it.key }.toSet()
    }

    /**
     * Renders with the form's raw [values], formatting custom-field values by their type.
     * [currencies] maps a currency field's key to its currency code; missing means IDR.
     */
    fun render(
        parsed: ParsedTemplate,
        values: Map<String, String>,
        fieldTypes: Map<String, FieldType>,
        now: LocalDateTime,
        currencies: Map<String, String> = emptyMap(),
    ): RenderResult {
        val inputs = values.mapValues { (key, raw) -> fieldTypes[key]?.let { formatFieldValue(it, raw, currencies[key]) } ?: raw }
        return TemplateRenderer.render(parsed, contact = null as Contact?, inputs = inputs, now = now)
    }

    /**
     * Custom-field values to save back to [contact]: non-empty form values for field keys that
     * differ from what the contact has. `nama` and `nomor` are never written back, and neither is
     * a field's fallback left as is for a contact without a value.
     */
    fun changedFields(
        values: Map<String, String>,
        contact: ContactData,
        fieldTypes: Map<String, FieldType>,
        fieldFallbacks: Map<String, String> = emptyMap(),
    ): Map<String, String> =
        values.filter { (key, raw) ->
            val stored = contact.fields[key]
            key in fieldTypes && raw.isNotEmpty() && stored != raw && !(stored.isNullOrEmpty() && raw == fieldFallbacks[key])
        }
}
