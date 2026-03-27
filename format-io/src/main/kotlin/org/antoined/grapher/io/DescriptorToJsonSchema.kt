package org.antoined.grapher.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import org.antoined.grapher.Type

private val objectMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

private val sourceSchema: Map<String, Any> = linkedMapOf(
    "type" to "object",
    "description" to "Location in the source document where this value was found",
    "properties" to linkedMapOf(
        "startChar" to linkedMapOf("type" to "integer"),
        "endChar" to linkedMapOf("type" to "integer"),
        "startLine" to linkedMapOf("type" to "integer"),
        "endLine" to linkedMapOf("type" to "integer"),
    )
)

/**
 * Serialises this descriptor to an indented JSON Schema string (draft 2020-12).
 *
 * Convenience wrapper around [toJsonSchema].
 */
fun Descriptor.toJsonSchemaString(): String =
    objectMapper.writeValueAsString(toJsonSchema())

/**
 * Converts this descriptor to a JSON Schema 2020-12 map.
 *
 * Mapping rules:
 * - [Descriptor] → root `object` with `$schema`, `title`, `description`, and `properties`.
 * - Fields with `multiplicity.min > 0` are added to `required`.
 * - [Part] with `multiplicity.max > 1` → `array` wrapping an `object`; otherwise plain `object`.
 * - [Property] with `multiplicity.max > 1` → `array` wrapping the scalar schema.
 * - `hints` → `description` (joined with `". "`).
 * - [org.antoined.grapher.Type.DATE] → `{ "type": "string", "format": "date" }`.
 */
fun Descriptor.toJsonSchema(): Map<String, Any> {
    val properties = linkedMapOf<String, Any>()
    val required = mutableListOf<String>()

    this.properties.forEach { prop ->
        properties[prop.name] = prop.toSchema()
        properties["${prop.name}_source"] = sourceSchema
        if (prop.multiplicity.min > 0) required += prop.name
    }
    this.parts.forEach { part ->
        properties[part.name] = part.toSchema()
        if (part.multiplicity.min > 0) required += part.name
    }

    return linkedMapOf<String, Any>(
        "\$schema" to "https://json-schema.org/draft/2020-12/schema",
        "title" to name,
        "description" to description,
        "type" to "object",
        "properties" to properties
    ).also { if (required.isNotEmpty()) it["required"] = required }
}

fun Part.toJsonSchemaString(): String =
    objectMapper.writeValueAsString(toSchema())

fun Property.toJsonSchemaString(): String =
    objectMapper.writeValueAsString(toSchema())

internal fun Part.toSchema(): Map<String, Any> {
    val properties = linkedMapOf<String, Any>()
    val required = mutableListOf<String>()

    properties["_source"] = sourceSchema
    this.properties.forEach { prop ->
        properties[prop.name] = prop.toSchema()
        properties["${prop.name}_source"] = sourceSchema
        if (prop.multiplicity.min > 0) required += prop.name
    }
    this.parts.forEach { part ->
        properties[part.name] = part.toSchema()
        if (part.multiplicity.min > 0) required += part.name
    }

    val objectSchema = linkedMapOf<String, Any>("type" to "object").also { m ->
        if (hints.isNotEmpty()) m["description"] = hints.joinToString(". ")
        m["properties"] = properties
        if (required.isNotEmpty()) m["required"] = required
    }

    return if (multiplicity.max > 1) {
        linkedMapOf<String, Any>("type" to "array").also { m ->
            if (hints.isNotEmpty()) m["description"] = hints.joinToString(". ")
            if (multiplicity.min > 0) m["minItems"] = multiplicity.min
            m["items"] = objectSchema
        }
    } else {
        objectSchema
    }
}

internal fun Property.toSchema(): Map<String, Any> {
    val jsonType = if (type == Type.DATE) "string" else type.jsonType
    val itemSchema = linkedMapOf<String, Any>("type" to jsonType).also { m ->
        if (type == Type.DATE) m["format"] = "date"
        pattern?.let { m["pattern"] = it }
        if (hints.isNotEmpty()) m["description"] = hints.joinToString(". ")
    }

    return if (multiplicity.max > 1) {
        linkedMapOf<String, Any>("type" to "array").also { m ->
            if (multiplicity.min > 0) m["minItems"] = multiplicity.min
            m["items"] = itemSchema
        }
    } else {
        itemSchema
    }
}
