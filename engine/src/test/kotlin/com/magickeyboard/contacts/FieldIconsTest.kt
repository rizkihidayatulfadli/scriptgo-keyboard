package com.magickeyboard.contacts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

class FieldIconsTest {
    @TestFactory
    fun fixtures(): List<DynamicTest> {
        val dir = System.getProperty("fixturesDir") ?: error("fixturesDir system property is not set")
        val cases = Json.parseToJsonElement(File(dir, "field-icon-fixtures.json").readText()).jsonObject.getValue("cases").jsonArray
        return cases.map { element ->
            val case = element.jsonObject.mapValues { it.value.jsonPrimitive.content }
            DynamicTest.dynamicTest("${case["label"]} (${case["type"]}, stored '${case["stored"]}')") {
                assertEquals(case["icon"], FieldIcons.resolve(case.getValue("stored"), case.getValue("label"), case.getValue("key"), case.getValue("type")))
            }
        }
    }

    @Test
    fun everyPickableIconIsInTheGallery() {
        val all = FieldIcons.all.toSet()
        assertEquals(FieldIcons.all.size, all.size)
        FieldIconCatalog.icons.forEach { (icon, _) -> assertTrue(icon in all, icon) }
        FieldIconCatalog.typeDefaults.values.forEach { assertTrue(it in all, it) }
    }

    @Test
    fun normalizesAccentsAndSeparators() {
        assertEquals("tgl lahir anak", FieldIcons.normalize("Tgl_Lahir (anak)"))
        assertEquals("area", FieldIcons.normalize("  Área  "))
    }
}
