package org.antoined.grapher.fsm

import org.antoined.grapher.Part
import org.antoined.grapher.Property

/**
 * Provides the raw document to begin extraction from.
 *
 * Decoupling document acquisition from extraction means the FSM engine can
 * obtain the document once without knowing its origin (file system, HTTP,
 * in-memory fixture, …).
 */
fun interface DocumentSource {
    fun load(): Document
}

/**
 * Locates all occurrences of a [Part] within a parent [Document] scope.
 *
 * - For a scalar part (`multiplicity.max == 1`): return a single-element list
 *   when present, or an empty list when the optional part is absent.
 * - For a repeating part (`multiplicity.max > 1`): return one [Located]<[Document]> per
 *   occurrence (e.g. one scoped region per invoice line item).
 *
 * Each [Located.value] is the scoped [Document] passed as [context] when the engine
 * descends into that occurrence's properties and child parts. [Located.location]
 * records where in the parent document the occurrence was found; it may be `null`
 * if the implementation does not track positions.
 */
fun interface PartLocator {
    fun locate(part: Part, context: Document): List<Located<Document>>
}

/**
 * Extracts the value(s) of a [Property] from a [Document] scope.
 *
 * Returns a [List] to accommodate properties with `multiplicity.max > 1`.
 * For scalar properties the list contains zero (absent optional) or one element.
 *
 * [Located.location] records where in the document the value was found; it may
 * be `null` if the implementation does not track positions.
 *
 * [context] is the [Document] scope produced by [PartLocator] for the
 * enclosing part, or the root document for top-level properties.
 */
fun interface PropertyExtractor {
    fun extract(property: Property, context: Document): List<Located<String>>
}
