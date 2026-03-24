package org.antoined.grapher.io

import org.antoined.grapher.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DescriptorReaderTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /** All four Type enum values on properties. */
    private val allTypesJson = """
        {
          "namespace": "test", "name": "AllTypes",
          "description": "covers all types", "version": "1.0",
          "properties": [
            { "name": "text",     "type": "STRING",  "multiplicity": "ONE" },
            { "name": "count",    "type": "INT",     "multiplicity": "ONE" },
            { "name": "price",    "type": "NUMBER",  "multiplicity": "ONE" },
            { "name": "birthday", "type": "DATE",    "multiplicity": "ONE" }
          ]
        }
    """.trimIndent()

    /** All four Multiplicity enum values on properties. */
    private val propertyMultiplicitiesJson = """
        {
          "namespace": "test", "name": "PropMult",
          "description": "covers all property multiplicities", "version": "1.0",
          "properties": [
            { "name": "maybeOne",   "type": "STRING", "multiplicity": "ZERO_ONE"  },
            { "name": "exactOne",   "type": "STRING", "multiplicity": "ONE"       },
            { "name": "anyCount",   "type": "STRING", "multiplicity": "ZERO_MANY" },
            { "name": "atLeastOne", "type": "STRING", "multiplicity": "ONE_MANY"  }
          ]
        }
    """.trimIndent()

    /** All four Multiplicity enum values on parts. */
    private val partMultiplicitiesJson = """
        {
          "namespace": "test", "name": "PartMult",
          "description": "covers all part multiplicities", "version": "1.0",
          "parts": [
            { "name": "optSection",  "multiplicity": "ZERO_ONE"  },
            { "name": "reqSection",  "multiplicity": "ONE"       },
            { "name": "anySection",  "multiplicity": "ZERO_MANY" },
            { "name": "someSection", "multiplicity": "ONE_MANY"  }
          ]
        }
    """.trimIndent()

    /** Nested parts with multiple hints and a patterned property. */
    private val nestedJson = """
        {
          "namespace": "docs", "name": "NestedDoc",
          "description": "nested parts, hints, pattern", "version": "1.0",
          "parts": [
            {
              "name": "chapter",
              "multiplicity": "ONE_MANY",
              "hints": ["Main chapter", "Repeat per chapter"],
              "parts": [
                {
                  "name": "paragraph",
                  "multiplicity": "ZERO_MANY",
                  "hints": ["Body paragraph"],
                  "properties": [
                    {
                      "name": "code",
                      "type": "STRING",
                      "multiplicity": "ONE",
                      "pattern": "[A-Z]{3}-\\d+",
                      "hints": ["Reference code"]
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    /** All optional fields omitted — tests default values. */
    private val defaultsJson = """
        {
          "namespace": "test", "name": "Defaults",
          "description": "tests default field values", "version": "1.0",
          "properties": [ { "name": "value" } ],
          "parts":      [ { "name": "section" } ]
        }
    """.trimIndent()

    // ── JSON tests ────────────────────────────────────────────────────────────

    @Test fun `json - all four types are parsed`() {
        val d = descriptorFromJson(allTypesJson)
        assertEquals(4, d.properties.size)
        val byName = d.properties.associateBy { it.name }
        assertEquals(Type.STRING,  byName["text"]!!.type)
        assertEquals(Type.INT,     byName["count"]!!.type)
        assertEquals(Type.NUMBER,  byName["price"]!!.type)
        assertEquals(Type.DATE,    byName["birthday"]!!.type)
    }

    @Test fun `json - all four property multiplicities are parsed`() {
        val d = descriptorFromJson(propertyMultiplicitiesJson)
        val byName = d.properties.associateBy { it.name }
        assertEquals(Multiplicity.ZERO_ONE,  byName["maybeOne"]!!.multiplicity)
        assertEquals(Multiplicity.ONE,       byName["exactOne"]!!.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, byName["anyCount"]!!.multiplicity)
        assertEquals(Multiplicity.ONE_MANY,  byName["atLeastOne"]!!.multiplicity)
    }

    @Test fun `json - all four part multiplicities are parsed`() {
        val d = descriptorFromJson(partMultiplicitiesJson)
        val byName = d.parts.associateBy { it.name }
        assertEquals(Multiplicity.ZERO_ONE,  byName["optSection"]!!.multiplicity)
        assertEquals(Multiplicity.ONE,       byName["reqSection"]!!.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, byName["anySection"]!!.multiplicity)
        assertEquals(Multiplicity.ONE_MANY,  byName["someSection"]!!.multiplicity)
    }

    @Test fun `json - nested parts with hints and pattern`() {
        val d = descriptorFromJson(nestedJson)
        val chapter = d.parts.single()
        assertEquals("chapter", chapter.name)
        assertEquals(Multiplicity.ONE_MANY, chapter.multiplicity)
        assertEquals(listOf("Main chapter", "Repeat per chapter"), chapter.hints)

        val paragraph = chapter.parts.single()
        assertEquals("paragraph", paragraph.name)
        assertEquals(Multiplicity.ZERO_MANY, paragraph.multiplicity)
        assertEquals(listOf("Body paragraph"), paragraph.hints)

        val code = paragraph.properties.single()
        assertEquals("code", code.name)
        assertEquals(Type.STRING, code.type)
        assertEquals("[A-Z]{3}-\\d+", code.pattern)
        assertEquals(listOf("Reference code"), code.hints)
    }

    @Test fun `json - omitted optional fields use defaults`() {
        val d = descriptorFromJson(defaultsJson)
        val prop = d.properties.single()
        assertEquals(Type.STRING, prop.type)
        assertEquals(Multiplicity.ONE, prop.multiplicity)
        assertNull(prop.pattern)
        assertEquals(emptyList(), prop.hints)

        val part = d.parts.single()
        assertEquals(Multiplicity.ONE, part.multiplicity)
        assertEquals(emptyList(), part.hints)
        assertEquals(emptyList(), part.parts)
        assertEquals(emptyList(), part.properties)
    }

    // ── YAML fixtures ────────────────────────────────────────────────────────

    private val allTypesYaml = """
        namespace: test
        name: AllTypes
        description: covers all types
        version: "1.0"
        properties:
          - name: text
            type: STRING
            multiplicity: ONE
          - name: count
            type: INT
            multiplicity: ONE
          - name: price
            type: NUMBER
            multiplicity: ONE
          - name: birthday
            type: DATE
            multiplicity: ONE
    """.trimIndent()

    private val propertyMultiplicitiesYaml = """
        namespace: test
        name: PropMult
        description: covers all property multiplicities
        version: "1.0"
        properties:
          - name: maybeOne
            type: STRING
            multiplicity: ZERO_ONE
          - name: exactOne
            type: STRING
            multiplicity: ONE
          - name: anyCount
            type: STRING
            multiplicity: ZERO_MANY
          - name: atLeastOne
            type: STRING
            multiplicity: ONE_MANY
    """.trimIndent()

    private val partMultiplicitiesYaml = """
        namespace: test
        name: PartMult
        description: covers all part multiplicities
        version: "1.0"
        parts:
          - name: optSection
            multiplicity: ZERO_ONE
          - name: reqSection
            multiplicity: ONE
          - name: anySection
            multiplicity: ZERO_MANY
          - name: someSection
            multiplicity: ONE_MANY
    """.trimIndent()

    private val nestedYaml = """
        namespace: docs
        name: NestedDoc
        description: nested parts, hints, pattern
        version: "1.0"
        parts:
          - name: chapter
            multiplicity: ONE_MANY
            hints:
              - Main chapter
              - Repeat per chapter
            parts:
              - name: paragraph
                multiplicity: ZERO_MANY
                hints:
                  - Body paragraph
                properties:
                  - name: code
                    type: STRING
                    multiplicity: ONE
                    pattern: "[A-Z]{3}-\\d+"
                    hints:
                      - Reference code
    """.trimIndent()

    private val defaultsYaml = """
        namespace: test
        name: Defaults
        description: tests default field values
        version: "1.0"
        properties:
          - name: value
        parts:
          - name: section
    """.trimIndent()

    // ── YAML tests ────────────────────────────────────────────────────────────

    @Test fun `yaml - all four types are parsed`() {
        val d = descriptorFromYaml(allTypesYaml)
        val byName = d.properties.associateBy { it.name }
        assertEquals(Type.STRING,  byName["text"]!!.type)
        assertEquals(Type.INT,     byName["count"]!!.type)
        assertEquals(Type.NUMBER,  byName["price"]!!.type)
        assertEquals(Type.DATE,    byName["birthday"]!!.type)
    }

    @Test fun `yaml - all four property multiplicities are parsed`() {
        val d = descriptorFromYaml(propertyMultiplicitiesYaml)
        val byName = d.properties.associateBy { it.name }
        assertEquals(Multiplicity.ZERO_ONE,  byName["maybeOne"]!!.multiplicity)
        assertEquals(Multiplicity.ONE,       byName["exactOne"]!!.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, byName["anyCount"]!!.multiplicity)
        assertEquals(Multiplicity.ONE_MANY,  byName["atLeastOne"]!!.multiplicity)
    }

    @Test fun `yaml - all four part multiplicities are parsed`() {
        val d = descriptorFromYaml(partMultiplicitiesYaml)
        val byName = d.parts.associateBy { it.name }
        assertEquals(Multiplicity.ZERO_ONE,  byName["optSection"]!!.multiplicity)
        assertEquals(Multiplicity.ONE,       byName["reqSection"]!!.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, byName["anySection"]!!.multiplicity)
        assertEquals(Multiplicity.ONE_MANY,  byName["someSection"]!!.multiplicity)
    }

    @Test fun `yaml - nested parts with hints and pattern`() {
        val d = descriptorFromYaml(nestedYaml)
        val chapter = d.parts.single()
        assertEquals(listOf("Main chapter", "Repeat per chapter"), chapter.hints)

        val paragraph = chapter.parts.single()
        assertEquals(Multiplicity.ZERO_MANY, paragraph.multiplicity)

        val code = paragraph.properties.single()
        assertEquals("[A-Z]{3}-\\d+", code.pattern)
        assertEquals(listOf("Reference code"), code.hints)
    }

    @Test fun `yaml - omitted optional fields use defaults`() {
        val d = descriptorFromYaml(defaultsYaml)
        val prop = d.properties.single()
        assertEquals(Type.STRING, prop.type)
        assertEquals(Multiplicity.ONE, prop.multiplicity)
        assertNull(prop.pattern)
        assertEquals(emptyList(), prop.hints)

        val part = d.parts.single()
        assertEquals(Multiplicity.ONE, part.multiplicity)
        assertEquals(emptyList(), part.hints)
        assertEquals(emptyList(), part.parts)
        assertEquals(emptyList(), part.properties)
    }
}
