package org.antoined.grapher

/**
 * Cardinality constraint for a [Part] or [Property].
 *
 * @property min Minimum number of occurrences.
 * @property max Maximum number of occurrences (42 is used as a sentinel for "unbounded").
 */
enum class Multiplicity(val min: Int, val max: Int) {
    /** Zero or one occurrence — the field is optional and scalar. */
    ZERO_ONE(0, 1),
    /** Exactly one occurrence — the field is required and scalar. */
    ONE(1, 1),
    /** Any number of occurrences — the field is an optional array. */
    ZERO_MANY(0, 42),
    /** One or more occurrences — the field is a required array. */
    ONE_MANY(1, 42)
}

/**
 * Scalar value type for a [Property].
 *
 * @property jsonType The corresponding JSON Schema primitive type string.
 */
enum class Type(val jsonType: String) {
    STRING("string"),
    INT("integer"),
    NUMBER("number"),
    /** Serialised as `{ "type": "string", "format": "date" }` in JSON Schema. */
    DATE("date"),
}

/**
 * A leaf-level field in the document model.
 *
 * @property name        Field name as it will appear in the extracted document.
 * @property type        Scalar type of the value (defaults to [Type.STRING]).
 * @property multiplicity Cardinality — determines whether the field is required and/or an array.
 * @property pattern     Optional regex pattern the value must match (JSON Schema `pattern`).
 * @property hints       Free-text extraction hints surfaced to the LLM as `description`.
 */
data class Property(
    val name: String,
    val type: Type = Type.STRING,
    val multiplicity: Multiplicity = Multiplicity.ONE,
    val pattern: String? = null,
    val hints: List<String> = emptyList()
)

/**
 * A composite, optionally recursive section of the document model.
 *
 * Parts map to JSON Schema `object` nodes. A [Multiplicity] with `max > 1` wraps the
 * object in an `array`.
 *
 * @property name         Section name as it will appear in the extracted document.
 * @property multiplicity Cardinality of this section.
 * @property hints        Free-text extraction hints surfaced to the LLM as `description`.
 * @property parts        Nested sub-sections (recursive).
 * @property properties   Leaf fields belonging to this section.
 */
data class Part(
    val name: String,
    val multiplicity: Multiplicity = Multiplicity.ONE,
    val hints: List<String> = emptyList(),
    val parts: List<Part> = emptyList(),
    val properties: List<Property> = emptyList()
)

/**
 * Root descriptor for a document extraction format.
 *
 * A [Descriptor] fully specifies the expected structure of a document type, including
 * extraction hints for each field. It can be serialised to JSON Schema via
 * `format-io` to provide an LLM with both a target schema and guidance.
 *
 * @property namespace   Logical grouping (e.g. `"finance"`, `"legal"`).
 * @property name        Human-readable name of the document type.
 * @property description Short description of the document type.
 * @property version     Version string of this descriptor definition.
 * @property parts       Top-level composite sections.
 * @property properties  Top-level scalar fields.
 */
data class Descriptor(
    val namespace: String,
    val name: String,
    val description: String,
    val version: String,
    val parts: List<Part> = emptyList(),
    val properties: List<Property> = emptyList()
)
