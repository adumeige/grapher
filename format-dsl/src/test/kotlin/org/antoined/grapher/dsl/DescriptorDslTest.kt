package org.antoined.grapher.dsl

import org.antoined.grapher.Multiplicity
import org.antoined.grapher.Type
import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescriptorDslTest {

    // ── Descriptor metadata ───────────────────────────────────────────────────

    @Test fun `descriptor sets namespace, name, description and version`() {
        val d = descriptor("finance", "Invoice") {
            description = "An invoice document"
            version     = "2.0"
        }
        assertEquals("finance",              d.namespace)
        assertEquals("Invoice",              d.name)
        assertEquals("An invoice document",  d.description)
        assertEquals("2.0",                  d.version)
    }

    @Test fun `descriptor with no content produces empty lists`() {
        val d = descriptor("test", "Empty") {}
        assertTrue(d.properties.isEmpty())
        assertTrue(d.parts.isEmpty())
    }

    @Test fun `version defaults to 1_0`() {
        val d = descriptor("t", "T") {}
        assertEquals("1.0", d.version)
    }

    // ── Property block form ───────────────────────────────────────────────────

    @Test fun `property block sets type, multiplicity, pattern and hints`() {
        val d = descriptor("t", "T") {
            property("code") {
                type         = Type.STRING
                multiplicity = Multiplicity.ONE
                pattern      = "[A-Z]{3}"
                hint("ISO 3-letter code")
                hint("Always uppercase")
            }
        }
        val p = d.properties.single()
        assertEquals("code",            p.name)
        assertEquals(Type.STRING,       p.type)
        assertEquals(Multiplicity.ONE,  p.multiplicity)
        assertEquals("[A-Z]{3}",        p.pattern)
        assertEquals(listOf("ISO 3-letter code", "Always uppercase"), p.hints)
    }

    @Test fun `property block defaults to STRING and ONE when not set`() {
        val d = descriptor("t", "T") { property("value") {} }
        val p = d.properties.single()
        assertEquals(Type.STRING,      p.type)
        assertEquals(Multiplicity.ONE, p.multiplicity)
        assertNull(p.pattern)
        assertTrue(p.hints.isEmpty())
    }

    // ── Property shorthand form ───────────────────────────────────────────────

    @Test fun `property shorthand with type only defaults multiplicity to ONE`() {
        val d = descriptor("t", "T") { property("amount", Type.NUMBER) }
        val p = d.properties.single()
        assertEquals(Type.NUMBER,      p.type)
        assertEquals(Multiplicity.ONE, p.multiplicity)
    }

    @Test fun `property shorthand sets type and multiplicity`() {
        val d = descriptor("t", "T") { property("tags", Type.STRING, Multiplicity.ZERO_MANY) }
        val p = d.properties.single()
        assertEquals(Type.STRING,          p.type)
        assertEquals(Multiplicity.ZERO_MANY, p.multiplicity)
    }

    @Test fun `all four types are usable via shorthand`() {
        val d = descriptor("t", "T") {
            property("a", Type.STRING)
            property("b", Type.INT)
            property("c", Type.NUMBER)
            property("d", Type.DATE)
        }
        val byName = d.properties.associateBy { it.name }
        assertEquals(Type.STRING, byName["a"]!!.type)
        assertEquals(Type.INT,    byName["b"]!!.type)
        assertEquals(Type.NUMBER, byName["c"]!!.type)
        assertEquals(Type.DATE,   byName["d"]!!.type)
    }

    @Test fun `all four multiplicities are usable`() {
        val d = descriptor("t", "T") {
            property("a", Type.STRING, Multiplicity.ZERO_ONE)
            property("b", Type.STRING, Multiplicity.ONE)
            property("c", Type.STRING, Multiplicity.ZERO_MANY)
            property("d", Type.STRING, Multiplicity.ONE_MANY)
        }
        val byName = d.properties.associateBy { it.name }
        assertEquals(Multiplicity.ZERO_ONE,  byName["a"]!!.multiplicity)
        assertEquals(Multiplicity.ONE,       byName["b"]!!.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, byName["c"]!!.multiplicity)
        assertEquals(Multiplicity.ONE_MANY,  byName["d"]!!.multiplicity)
    }

    @Test fun `declaration order of properties is preserved`() {
        val d = descriptor("t", "T") {
            property("first")
            property("second")
            property("third")
        }
        assertEquals(listOf("first", "second", "third"), d.properties.map { it.name })
    }

    // ── Parts ─────────────────────────────────────────────────────────────────

    @Test fun `part sets name, multiplicity and hints`() {
        val d = descriptor("t", "T") {
            part("items") {
                multiplicity = Multiplicity.ONE_MANY
                hint("One per item")
                hint("Exclude taxes")
            }
        }
        val p = d.parts.single()
        assertEquals("items",                     p.name)
        assertEquals(Multiplicity.ONE_MANY,       p.multiplicity)
        assertEquals(listOf("One per item", "Exclude taxes"), p.hints)
    }

    @Test fun `part contains properties defined via block and shorthand`() {
        val d = descriptor("t", "T") {
            part("section") {
                property("label") {
                    hint("Section heading")
                }
                property("amount", Type.NUMBER, Multiplicity.ZERO_ONE)
            }
        }
        val props = d.parts.single().properties
        assertEquals(2, props.size)

        val label = props.first { it.name == "label" }
        assertEquals(Type.STRING,       label.type)
        assertEquals(listOf("Section heading"), label.hints)

        val amount = props.first { it.name == "amount" }
        assertEquals(Type.NUMBER,           amount.type)
        assertEquals(Multiplicity.ZERO_ONE, amount.multiplicity)
    }

    @Test fun `parts are nested recursively`() {
        val d = descriptor("t", "T") {
            part("chapter") {
                multiplicity = Multiplicity.ONE_MANY
                part("paragraph") {
                    multiplicity = Multiplicity.ZERO_MANY
                    hint("Body paragraph")
                    part("footnote") {
                        multiplicity = Multiplicity.ZERO_MANY
                        property("text")
                    }
                }
            }
        }
        val chapter   = d.parts.single()
        val paragraph = chapter.parts.single()
        val footnote  = paragraph.parts.single()

        assertEquals(Multiplicity.ONE_MANY,  chapter.multiplicity)
        assertEquals(Multiplicity.ZERO_MANY, paragraph.multiplicity)
        assertEquals(listOf("Body paragraph"), paragraph.hints)
        assertEquals("footnote",             footnote.name)
        assertEquals("text",                 footnote.properties.single().name)
    }

    @Test fun `part default multiplicity is ONE`() {
        val d = descriptor("t", "T") { part("s") {} }
        assertEquals(Multiplicity.ONE, d.parts.single().multiplicity)
    }

    @Test fun `declaration order of parts is preserved`() {
        val d = descriptor("t", "T") {
            part("first")  {}
            part("second") {}
            part("third")  {}
        }
        assertEquals(listOf("first", "second", "third"), d.parts.map { it.name })
    }

    @Test fun `multiple top-level parts are all registered`() {
        val d = descriptor("t", "T") {
            part("header")  { property("title") }
            part("body")    { property("content") }
            part("footer")  { property("page", Type.INT) }
        }
        assertEquals(3, d.parts.size)
        assertEquals(setOf("header", "body", "footer"), d.parts.map { it.name }.toSet())
    }

    // ── Mixed descriptor ──────────────────────────────────────────────────────

    @Test fun `descriptor combines top-level properties and parts`() {
        val d = descriptor("finance", "Invoice") {
            description = "An invoice document"
            version     = "1.0"

            property("invoiceNumber") {
                pattern = "[A-Z]{2}-\\d+"
                hint("Invoice reference number")
            }
            property("date",  Type.DATE)
            property("notes", Type.STRING, Multiplicity.ZERO_ONE)

            part("lineItems") {
                multiplicity = Multiplicity.ONE_MANY
                hint("One entry per product or service")

                property("description") { hint("Product or service name") }
                property("amount", Type.NUMBER)
            }
        }

        assertEquals(3, d.properties.size)
        assertEquals(1, d.parts.size)

        val invoiceNumber = d.properties.first { it.name == "invoiceNumber" }
        assertEquals("[A-Z]{2}-\\d+", invoiceNumber.pattern)
        assertEquals(listOf("Invoice reference number"), invoiceNumber.hints)

        val date = d.properties.first { it.name == "date" }
        assertEquals(Type.DATE, date.type)

        val notes = d.properties.first { it.name == "notes" }
        assertEquals(Multiplicity.ZERO_ONE, notes.multiplicity)

        val lineItems = d.parts.single()
        assertEquals(Multiplicity.ONE_MANY, lineItems.multiplicity)
        assertEquals(2, lineItems.properties.size)
    }

    @Test fun `dsl produces the same result as direct construction`() {
        val viaDsl = descriptor("finance", "Invoice") {
            description = "An invoice document"
            version     = "1.0"
            property("invoiceNumber") {
                pattern = "[A-Z]{2}-\\d+"
                hint("Invoice reference number")
            }
            property("date", Type.DATE)
            part("lineItems") {
                multiplicity = Multiplicity.ONE_MANY
                hint("One entry per product or service")
                property("amount", Type.NUMBER)
            }
        }

        val direct = Descriptor(
            namespace   = "finance",
            name        = "Invoice",
            description = "An invoice document",
            version     = "1.0",
            properties  = listOf(
                Property("invoiceNumber", pattern = "[A-Z]{2}-\\d+", hints = listOf("Invoice reference number")),
                Property("date", Type.DATE)
            ),
            parts = listOf(
                Part("lineItems", Multiplicity.ONE_MANY,
                    hints      = listOf("One entry per product or service"),
                    properties = listOf(Property("amount", Type.NUMBER))
                )
            )
        )

        assertEquals(direct, viaDsl)
    }
}
