package org.antoined.grapher.fsm

/**
 * Opaque handle to a raw document (or scoped section of one) being processed.
 *
 * The FSM engine never inspects the contents — it only threads [Document]
 * instances through to [PartLocator] and [PropertyExtractor]. Implementations
 * are free to carry any internal representation (byte array, plain text,
 * parsed JSON tree, …) and cast internally.
 */
interface Document

/**
 * An opaque position or span within a raw document.
 *
 * Implementations are free to represent character offsets, line/column ranges,
 * bounding boxes, XPath expressions, or any other coordinate system relevant
 * to the underlying document format.
 */
interface Location

/**
 * A value paired with the [Location] in the document where it was found.
 *
 * [location] is `null` when the implementation does not track positions.
 */
data class Located<out T>(val value: T, val location: Location?)
