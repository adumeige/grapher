package org.antoined.grapher.dsl

import org.antoined.grapher.Descriptor
import org.antoined.grapher.Multiplicity
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import org.antoined.grapher.Type

/**
 * DSL scope marker — prevents accidentally calling an outer builder's methods
 * from inside a nested block.
 */
@DslMarker
annotation class GrapherDsl

// ── Property ──────────────────────────────────────────────────────────────────

/**
 * Builder for [Property], used inside [PartBuilder] and [DescriptorBuilder].
 *
 * ```kotlin
 * property("invoiceNumber") {
 *     pattern = "[A-Z]{2}-\\d+"
 *     hint("Invoice reference — format: CC-NNNNN")
 * }
 * ```
 */
@GrapherDsl
class PropertyBuilder(val name: String) {
    /** Scalar value type. Defaults to [Type.STRING]. */
    var type: Type = Type.STRING
    /** Cardinality of this field. Defaults to [Multiplicity.ONE]. */
    var multiplicity: Multiplicity = Multiplicity.ONE
    /** Optional regex the value must satisfy (mapped to JSON Schema `pattern`). */
    var pattern: String? = null

    private val hints = mutableListOf<String>()

    /** Adds an extraction hint surfaced to the LLM as `description`. */
    fun hint(text: String) { hints += text }

    internal fun build() = Property(name, type, multiplicity, pattern, hints.toList())
}

// ── Part ──────────────────────────────────────────────────────────────────────

/**
 * Builder for [Part], used inside [PartBuilder] (nested) and [DescriptorBuilder].
 *
 * ```kotlin
 * part("lineItems") {
 *     multiplicity = Multiplicity.ONE_MANY
 *     hint("One entry per product or service")
 *
 *     property("description") { hint("Product or service name") }
 *     property("amount", Type.NUMBER)
 * }
 * ```
 */
@GrapherDsl
class PartBuilder(val name: String) {
    /** Cardinality of this section. Defaults to [Multiplicity.ONE]. */
    var multiplicity: Multiplicity = Multiplicity.ONE

    private val hints      = mutableListOf<String>()
    private val parts      = mutableListOf<Part>()
    private val properties = mutableListOf<Property>()

    /** Adds an extraction hint surfaced to the LLM as `description`. */
    fun hint(text: String) { hints += text }

    /**
     * Adds a property with full configuration via a builder block.
     *
     * ```kotlin
     * property("code") {
     *     type = Type.STRING
     *     pattern = "[A-Z]{3}"
     *     hint("ISO 3-letter code")
     * }
     * ```
     */
    fun property(name: String, block: PropertyBuilder.() -> Unit = {}) {
        properties += PropertyBuilder(name).apply(block).build()
    }

    /**
     * Shorthand for a property whose only relevant attributes are type and multiplicity.
     *
     * ```kotlin
     * property("amount", Type.NUMBER)
     * property("tags",   Type.STRING, Multiplicity.ZERO_MANY)
     * ```
     */
    fun property(name: String, type: Type, multiplicity: Multiplicity = Multiplicity.ONE) {
        properties += Property(name, type, multiplicity)
    }

    /** Adds a nested sub-section. */
    fun part(name: String, block: PartBuilder.() -> Unit) {
        parts += PartBuilder(name).apply(block).build()
    }

    internal fun build() = Part(name, multiplicity, hints.toList(), parts.toList(), properties.toList())
}

// ── Descriptor ────────────────────────────────────────────────────────────────

/**
 * Builder for [Descriptor], created by the top-level [descriptor] function.
 */
@GrapherDsl
class DescriptorBuilder(val namespace: String, val name: String) {
    /** Short description of the document type. */
    var description: String = ""
    /** Version of this descriptor definition. */
    var version: String = "1.0"

    private val parts      = mutableListOf<Part>()
    private val properties = mutableListOf<Property>()

    /**
     * Adds a top-level property with full configuration via a builder block.
     *
     * ```kotlin
     * property("invoiceNumber") {
     *     pattern = "[A-Z]{2}-\\d+"
     *     hint("Invoice reference number")
     * }
     * ```
     */
    fun property(name: String, block: PropertyBuilder.() -> Unit = {}) {
        properties += PropertyBuilder(name).apply(block).build()
    }

    /**
     * Shorthand for a top-level property with only type and multiplicity.
     *
     * ```kotlin
     * property("date",  Type.DATE)
     * property("notes", Type.STRING, Multiplicity.ZERO_ONE)
     * ```
     */
    fun property(name: String, type: Type, multiplicity: Multiplicity = Multiplicity.ONE) {
        properties += Property(name, type, multiplicity)
    }

    /** Adds a top-level section. */
    fun part(name: String, block: PartBuilder.() -> Unit) {
        parts += PartBuilder(name).apply(block).build()
    }

    internal fun build() = Descriptor(namespace, name, description, version, parts.toList(), properties.toList())
}

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Creates a [Descriptor] using the grapher DSL.
 *
 * ```kotlin
 * val invoice = descriptor("finance", "Invoice") {
 *     description = "An invoice document"
 *     version     = "1.0"
 *
 *     property("invoiceNumber") {
 *         pattern = "[A-Z]{2}-\\d+"
 *         hint("Invoice reference number")
 *     }
 *     property("date",  Type.DATE)
 *     property("notes", Type.STRING, Multiplicity.ZERO_ONE)
 *
 *     part("lineItems") {
 *         multiplicity = Multiplicity.ONE_MANY
 *         hint("One entry per product or service")
 *
 *         property("description") { hint("Product or service name") }
 *         property("amount", Type.NUMBER)
 *
 *         part("taxes") {
 *             multiplicity = Multiplicity.ZERO_MANY
 *             property("rate",  Type.NUMBER)
 *             property("label", Type.STRING)
 *         }
 *     }
 * }
 * ```
 *
 * @param namespace Logical grouping (e.g. `"finance"`, `"legal"`).
 * @param name      Human-readable name of the document type.
 * @param block     DSL configuration block.
 */
fun descriptor(namespace: String, name: String, block: DescriptorBuilder.() -> Unit): Descriptor =
    DescriptorBuilder(namespace, name).apply(block).build()
