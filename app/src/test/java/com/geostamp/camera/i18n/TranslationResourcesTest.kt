package com.geostamp.camera.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards every translation against the English source: no missing strings, and the same format placeholders,
 * so String.format can never crash or drop a value in another language.
 */
class TranslationResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfcx%]")

    @Test
    fun allPlannedLanguagesExist() {
        val expected = setOf("hi", "es", "fr", "de", "pt", "ja", "ko", "zh-rCN", "ar")
        val present = res.listFiles()!!.filter { File(it, "strings.xml").exists() }.map { it.name.removePrefix("values-") }.toSet()
        assertTrue("missing ${expected - present}", present.containsAll(expected))
    }

    @Test
    fun everyTranslationHasEveryStringWithMatchingPlaceholders() {
        val english = load(File(res, "values/strings.xml"))
        val translatable = english.filterValues { !it.untranslatable }
        res.listFiles()!!.filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }.forEach { dir ->
            val translated = load(File(dir, "strings.xml"))
            val missing = translatable.keys - translated.keys
            assertTrue("${dir.name} is missing $missing", missing.isEmpty())
            val extra = translated.keys - english.keys
            assertTrue("${dir.name} has unknown $extra", extra.isEmpty())
            translated.forEach { (name, value) ->
                val source = english.getValue(name)
                value.texts.forEach { text ->
                    assertEquals("${dir.name}/$name", placeholders(source.texts.last()), placeholders(text))
                }
            }
        }
    }

    private fun placeholders(text: String): List<String> =
        placeholder.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()

    private data class Resource(val texts: List<String>, val untranslatable: Boolean)

    private fun load(file: File): Map<String, Resource> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = document.documentElement
        val result = mutableMapOf<String, Resource>()
        for (i in 0 until root.childNodes.length) {
            val node = root.childNodes.item(i) as? Element ?: continue
            val texts = when (node.tagName) {
                "string" -> listOf(node.textContent)
                "plurals" -> (0 until node.getElementsByTagName("item").length).map { node.getElementsByTagName("item").item(it).textContent }
                else -> continue
            }
            result[node.getAttribute("name")] = Resource(texts, node.getAttribute("translatable") == "false")
        }
        return result
    }
}
