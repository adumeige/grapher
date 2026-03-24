package org.antoined.grapher.io

import org.antoined.grapher.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// Navigation helpers to avoid repetitive unchecked-cast chains.
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.props() = this["properties"] as Map<String, Any>
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.required() = this["required"] as? List<String>
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.items() = this["items"] as Map<String, Any>

private fun descriptor(vararg properties: Property, parts: List<Part> = emptyList()) = Descriptor(
    namespace = "test", name = "Test", description = "test", version = "1.0",
    properties = properties.toList(), parts = parts
)

class DescriptorToJsonSchemaTest {

    // ── Root schema ───────────────────────────────────────────────────────────

    @Test fun `root has schema dialect, title, description, and type object`() {
        val schema = descriptor().toJsonSchema()
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"])
        assertEquals("Test",   schema["title"])
        assertEquals("test",   schema["description"])
        assertEquals("object", schema["type"])
    }

    @Test fun `descriptor with no properties or parts has empty properties and no required`() {
        val schema = descriptor()
        assertNull(schema.toJsonSchema().required(), "required must be absent when nothing is required")
        assertEquals(emptyMap<String, Any>(), schema.toJsonSchema().props())
    }

    // ── Type mapping ──────────────────────────────────────────────────────────

    @Test fun `STRING type maps to string`() {
        val schema = descriptor(Property("v", Type.STRING, Multiplicity.ONE)).toJsonSchema()
        assertEquals("string", schema.props()["v"].let { (it as Map<*, *>)["type"] })
    }

    @Test fun `INT type maps to integer`() {
        val schema = descriptor(Property("v", Type.INT, Multiplicity.ONE)).toJsonSchema()
        assertEquals("integer", schema.props()["v"].let { (it as Map<*, *>)["type"] })
    }

    @Test fun `NUMBER type maps to number`() {
        val schema = descriptor(Property("v", Type.NUMBER, Multiplicity.ONE)).toJsonSchema()
        assertEquals("number", schema.props()["v"].let { (it as Map<*, *>)["type"] })
    }

    @Test fun `DATE type maps to string with format date`() {
        @Suppress("UNCHECKED_CAST")
        val propSchema = descriptor(Property("v", Type.DATE, Multiplicity.ONE))
            .toJsonSchema().props()["v"] as Map<String, Any>
        assertEquals("string", propSchema["type"])
        assertEquals("date",   propSchema["format"])
    }

    // ── Property multiplicity ─────────────────────────────────────────────────

    @Test fun `ZERO_ONE property is scalar and not required`() {
        val schema = descriptor(Property("v", Type.STRING, Multiplicity.ZERO_ONE)).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val propSchema = schema.props()["v"] as Map<String, Any>
        assertEquals("string", propSchema["type"])
        assertFalse(propSchema.containsKey("items"), "ZERO_ONE must not wrap in array")
        assertFalse(schema.required().orEmpty().contains("v"), "ZERO_ONE must not be required")
    }

    @Test fun `ONE property is scalar and required`() {
        val schema = descriptor(Property("v", Type.STRING, Multiplicity.ONE)).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val propSchema = schema.props()["v"] as Map<String, Any>
        assertEquals("string", propSchema["type"])
        assertFalse(propSchema.containsKey("items"), "ONE must not wrap in array")
        assertContains(schema.required()!!, "v")
    }

    @Test fun `ZERO_MANY property is array without minItems and not required`() {
        val schema = descriptor(Property("v", Type.STRING, Multiplicity.ZERO_MANY)).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val propSchema = schema.props()["v"] as Map<String, Any>
        assertEquals("array", propSchema["type"])
        assertEquals("string", propSchema.items()["type"])
        assertFalse(propSchema.containsKey("minItems"), "ZERO_MANY must not have minItems")
        assertFalse(schema.required().orEmpty().contains("v"), "ZERO_MANY must not be required")
    }

    @Test fun `ONE_MANY property is array with minItems 1 and required`() {
        val schema = descriptor(Property("v", Type.STRING, Multiplicity.ONE_MANY)).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val propSchema = schema.props()["v"] as Map<String, Any>
        assertEquals("array",  propSchema["type"])
        assertEquals("string", propSchema.items()["type"])
        assertEquals(1,        propSchema["minItems"])
        assertContains(schema.required()!!, "v")
    }

    // ── Part multiplicity ─────────────────────────────────────────────────────

    @Test fun `ZERO_ONE part is object and not required`() {
        val schema = descriptor(parts = listOf(Part("s", Multiplicity.ZERO_ONE))).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val partSchema = schema.props()["s"] as Map<String, Any>
        assertEquals("object", partSchema["type"])
        assertFalse(partSchema.containsKey("items"), "ZERO_ONE part must not be wrapped in array")
        assertFalse(schema.required().orEmpty().contains("s"), "ZERO_ONE part must not be required")
    }

    @Test fun `ONE part is object and required`() {
        val schema = descriptor(parts = listOf(Part("s", Multiplicity.ONE))).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val partSchema = schema.props()["s"] as Map<String, Any>
        assertEquals("object", partSchema["type"])
        assertFalse(partSchema.containsKey("items"), "ONE part must not be wrapped in array")
        assertContains(schema.required()!!, "s")
    }

    @Test fun `ZERO_MANY part is array without minItems and not required`() {
        val schema = descriptor(parts = listOf(Part("s", Multiplicity.ZERO_MANY))).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val partSchema = schema.props()["s"] as Map<String, Any>
        assertEquals("array",  partSchema["type"])
        assertEquals("object", partSchema.items()["type"])
        assertFalse(partSchema.containsKey("minItems"), "ZERO_MANY part must not have minItems")
        assertFalse(schema.required().orEmpty().contains("s"), "ZERO_MANY part must not be required")
    }

    @Test fun `ONE_MANY part is array with minItems 1 and required`() {
        val schema = descriptor(parts = listOf(Part("s", Multiplicity.ONE_MANY))).toJsonSchema()
        @Suppress("UNCHECKED_CAST")
        val partSchema = schema.props()["s"] as Map<String, Any>
        assertEquals("array",  partSchema["type"])
        assertEquals("object", partSchema.items()["type"])
        assertEquals(1,        partSchema["minItems"])
        assertContains(schema.required()!!, "s")
    }

    // ── Hints ─────────────────────────────────────────────────────────────────

    @Test fun `property hints are joined into description`() {
        val prop = Property("v", hints = listOf("Extract carefully", "Use ISO format"))
        @Suppress("UNCHECKED_CAST")
        val propSchema = descriptor(prop).toJsonSchema().props()["v"] as Map<String, Any>
        assertEquals("Extract carefully. Use ISO format", propSchema["description"])
    }

    @Test fun `property with no hints has no description field`() {
        val prop = Property("v")
        @Suppress("UNCHECKED_CAST")
        val propSchema = descriptor(prop).toJsonSchema().props()["v"] as Map<String, Any>
        assertFalse(propSchema.containsKey("description"))
    }

    @Test fun `part hints appear on the array wrapper not only on items`() {
        val part = Part("s", Multiplicity.ONE_MANY, hints = listOf("One per section"))
        @Suppress("UNCHECKED_CAST")
        val partSchema = descriptor(parts = listOf(part)).toJsonSchema().props()["s"] as Map<String, Any>
        assertEquals("One per section", partSchema["description"])
        assertEquals("One per section", partSchema.items()["description"])
    }

    @Test fun `part with no hints has no description field`() {
        val part = Part("s", Multiplicity.ONE)
        @Suppress("UNCHECKED_CAST")
        val partSchema = descriptor(parts = listOf(part)).toJsonSchema().props()["s"] as Map<String, Any>
        assertFalse(partSchema.containsKey("description"))
    }

    // ── Pattern ───────────────────────────────────────────────────────────────

    @Test fun `pattern is included in property schema`() {
        val prop = Property("code", pattern = "[A-Z]{2}-\\d+")
        @Suppress("UNCHECKED_CAST")
        val propSchema = descriptor(prop).toJsonSchema().props()["code"] as Map<String, Any>
        assertEquals("[A-Z]{2}-\\d+", propSchema["pattern"])
    }

    @Test fun `absent pattern leaves no pattern key`() {
        val prop = Property("v")
        @Suppress("UNCHECKED_CAST")
        val propSchema = descriptor(prop).toJsonSchema().props()["v"] as Map<String, Any>
        assertFalse(propSchema.containsKey("pattern"))
    }

    // ── Nested parts ─────────────────────────────────────────────────────────

    @Test fun `nested part properties appear under items properties`() {
        val inner = Part(
            name = "line",
            multiplicity = Multiplicity.ONE_MANY,
            properties = listOf(
                Property("amount", Type.NUMBER, Multiplicity.ONE),
                Property("label",  Type.STRING, Multiplicity.ZERO_ONE)
            )
        )
        val schema = descriptor(parts = listOf(inner)).toJsonSchema()

        @Suppress("UNCHECKED_CAST")
        val lineSchema = schema.props()["line"] as Map<String, Any>
        val lineItems  = lineSchema.items()
        val lineProps  = lineItems.props()

        assertEquals("number", (lineProps["amount"] as Map<*, *>)["type"])
        assertEquals("string", (lineProps["label"]  as Map<*, *>)["type"])

        @Suppress("UNCHECKED_CAST")
        val lineRequired = lineItems.required() ?: emptyList()
        assertContains(lineRequired, "amount")
        assertFalse(lineRequired.contains("label"), "ZERO_ONE inner property must not be required")
    }

    @Test fun `deeply nested parts resolve correctly`() {
        val leaf = Part("leaf", Multiplicity.ONE,
            properties = listOf(Property("val", Type.INT, Multiplicity.ONE)))
        val mid  = Part("mid",  Multiplicity.ONE, parts = listOf(leaf))
        val schema = descriptor(parts = listOf(mid)).toJsonSchema()

        @Suppress("UNCHECKED_CAST")
        val midSchema  = schema.props()["mid"]  as Map<String, Any>
        val leafSchema = midSchema.props()["leaf"] as Map<String, Any>
        assertEquals("integer", (leafSchema.props()["val"] as Map<*, *>)["type"])
    }

    // ── Mixed required ────────────────────────────────────────────────────────

    @Test fun `required list contains only min-1 fields across properties and parts`() {
        val schema = Descriptor(
            namespace = "t", name = "T", description = "t", version = "1.0",
            properties = listOf(
                Property("req",  Type.STRING, Multiplicity.ONE),
                Property("opt",  Type.STRING, Multiplicity.ZERO_ONE),
                Property("many", Type.STRING, Multiplicity.ONE_MANY)
            ),
            parts = listOf(
                Part("reqPart",  Multiplicity.ONE),
                Part("optPart",  Multiplicity.ZERO_ONE),
                Part("manyPart", Multiplicity.ZERO_MANY)
            )
        ).toJsonSchema()

        val required = schema.required()!!
        assertContains(required, "req")
        assertContains(required, "many")
        assertContains(required, "reqPart")
        assertFalse(required.contains("opt"),      "ZERO_ONE property must not be required")
        assertFalse(required.contains("optPart"),  "ZERO_ONE part must not be required")
        assertFalse(required.contains("manyPart"), "ZERO_MANY part must not be required")
    }
}
