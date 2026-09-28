package com.magickeyboard.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDateTime

/** Runs the shared fixtures in docs/, the conformance suite every engine implementation must pass. */
class FixturesTest {

    private fun load(name: String): JsonArray {
        val dir = System.getProperty("fixturesDir") ?: error("fixturesDir system property is not set")
        return Json.parseToJsonElement(File(dir, name).readText()).jsonArray
    }

    @TestFactory
    fun templateFixtures(): List<DynamicTest> = load("template-fixtures.json").map { element ->
        val case = element.jsonObject
        DynamicTest.dynamicTest(case.string("name")) {
            val parsed = TemplateParser.parse(case.string("template"))
            val expected = case.getValue("expected").jsonObject

            assertEquals(expected.getValue("variables").jsonArray.map { it.toVariable() }, parsed.variables)

            val result = TemplateRenderer.render(
                parsed = parsed,
                contact = case["contact"]?.jsonObject?.toContact(),
                inputs = case["inputs"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                now = case["now"]?.jsonPrimitive?.content?.let(LocalDateTime::parse) ?: LocalDateTime.of(2026, 1, 1, 0, 0),
                markMissing = case["markMissing"]?.jsonPrimitive?.boolean ?: false,
                fieldFallbacks = case["fieldFallbacks"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
            )
            assertEquals(expected.string("text"), result.text)
            val cursor = expected.getValue("cursor")
            assertEquals(if (cursor is JsonNull) null else cursor.jsonPrimitive.int, result.cursor)
        }
    }

    @TestFactory
    fun fieldFormatFixtures(): List<DynamicTest> = load("field-format-fixtures.json").map { element ->
        val case = element.jsonObject
        DynamicTest.dynamicTest(case.string("name")) {
            val type = FieldType.fromKey(case.string("type")) ?: error("unknown type ${case.string("type")}")
            val actual = when (val raw = case.getValue("raw")) {
                is JsonArray -> formatFieldValue(type, raw.map { it.jsonPrimitive.content })
                else -> formatFieldValue(type, raw.jsonPrimitive.content, case["currency"]?.jsonPrimitive?.content)
            }
            assertEquals(case.string("expected"), actual)
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonElement.toVariable(): Variable {
        val obj = jsonObject
        return Variable(
            key = obj.string("key"),
            default = obj["default"]?.jsonPrimitive?.content,
            options = obj["options"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    private fun JsonObject.toContact() = Contact(
        name = string("name"),
        phones = getValue("phones").jsonArray.map { it.jsonPrimitive.content },
        fields = getValue("fields").jsonObject.mapValues { it.value.jsonPrimitive.content },
    )
}
