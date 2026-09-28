package com.magickeyboard.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class SourceVariablesTest {
    @Test
    fun `finds variables with their ranges`() {
        val template = "Halo {nama|Kak}, kurir {Kurir:JNE,J&T} {@kursor}"
        val found = TemplateParser.sourceVariables(template)
        assertEquals(
            listOf(
                SourceVariable(5, 15, "nama", builtin = false, default = "Kak"),
                SourceVariable(23, 38, "kurir", builtin = false, options = listOf("JNE", "J&T")),
                SourceVariable(39, 48, "@kursor", builtin = true),
            ),
            found,
        )
        assertEquals(listOf("{nama|Kak}", "{Kurir:JNE,J&T}", "{@kursor}"), found.map { template.substring(it.start, it.end) })
    }

    @Test
    fun `escapes, invalid keys, unknown built-ins and unclosed braces are not variables`() {
        assertEquals(emptyList<SourceVariable>(), TemplateParser.sourceVariables("\\{nama\\} {ada spasi} {@cuaca} {kota"))
    }

    @Test
    fun `agrees with parse on every fixture`() {
        val dir = System.getProperty("fixturesDir") ?: error("fixturesDir system property is not set")
        for (element in Json.parseToJsonElement(File(dir, "template-fixtures.json").readText()).jsonArray) {
            val template = element.jsonObject.getValue("template").jsonPrimitive.content
            val keys = TemplateParser.sourceVariables(template).filterNot { it.builtin }.map { it.key }.distinct()
            assertEquals(TemplateParser.parse(template).variables.map { it.key }, keys, template)
        }
    }
}
