package org.antoined.grapher.fsm

import org.antoined.grapher.Part
import org.antoined.grapher.Property

/**
 * The extracted value(s) for a single [Property].
 *
 * [locatedValues] is a list to accommodate properties with `multiplicity.max > 1`.
 * For a scalar property the list has zero (absent optional) or one element.
 * Each entry carries the extracted string and the [Location] where it was found
 * (or `null` if the extractor does not track positions).
 *
 * [values] is a convenience accessor that strips location info.
 */
data class PropertyRecord(
    val property: Property,
    val locatedValues: List<Located<String>>
) {
    val values: List<String> get() = locatedValues.map { it.value }
}

/**
 * The extracted content of one occurrence of a [Part].
 *
 * Mirrors the [Part] structure recursively:
 * - [location] is where this occurrence was found in its parent scope (may be `null`
 *   if the [PartLocator] does not track positions).
 * - [properties] holds a [PropertyRecord] for each property declared in the part.
 * - [parts] holds a [PartInstances] for each child part declared in the part.
 */
data class PartRecord(
    val part: Part,
    val location: Location?,
    val properties: List<PropertyRecord>,
    val parts: List<PartInstances>
)

/**
 * All occurrences found for a single [Part] definition within one parent scope.
 *
 * - For a scalar part (`multiplicity.max == 1`): [occurrences] has 0 or 1 element.
 * - For a repeating part (`multiplicity.max > 1`): [occurrences] has one [PartRecord]
 *   per located scope.
 *
 * Carrying the [Part] reference (rather than using a `Map<String, …>`) preserves
 * declaration order and gives consumers direct access to the part's metadata
 * (multiplicity, hints, …) without a separate lookup.
 */
data class PartInstances(
    val part: Part,
    val occurrences: List<PartRecord>
)

/**
 * The top-level extraction result for a complete [org.antoined.grapher.Descriptor] run.
 *
 * - [properties] holds a [PropertyRecord] for each top-level property.
 * - [parts] holds a [PartInstances] for each top-level part.
 *
 * Declaration order is preserved in both lists.
 */
data class ExtractionRecord(
    val properties: List<PropertyRecord>,
    val parts: List<PartInstances>
)
