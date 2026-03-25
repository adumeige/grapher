package org.antoined.grapher.fsm

import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part

// ── Intermediate located tree (private to this file) ─────────────────────────

private data class LocatedInstances(val part: Part, val occurrences: List<LocatedRecord>)
private data class LocatedRecord(val scope: Document, val location: Location?, val children: List<LocatedInstances>)
private data class BfsItem(
    val part: Part,
    val parentScope: Document,
    val depth: Int,
    val sink: MutableList<LocatedInstances>
)

// ── FSM engine ────────────────────────────────────────────────────────────────

/**
 * Finite-state-machine that traverses a [Descriptor] tree and drives document
 * content extraction via pluggable interfaces.
 *
 * Extraction proceeds in two distinct passes:
 *
 * **Pass 1 — BFS part location:** [PartLocator] is called for every [Part] in
 * the descriptor tree, level by level (breadth-first), until [maxDepth] is
 * reached. All locate calls at depth N complete before any at depth N+1.
 * The FSM is in state [FsmState.Locating] throughout this pass.
 *
 * **Pass 2 — DFS property extraction:** The located scopes from Pass 1 are
 * walked depth-first. [PropertyExtractor] is called at every node to fill in
 * leaf values. The FSM is in state [FsmState.Extracting] throughout this pass.
 *
 * The FSM is **single-use** and **synchronous**. Create one instance per
 * document and call [run] exactly once.
 *
 * @param descriptor        The document structure to traverse.
 * @param source            Provides the root [Document] to extract from.
 * @param partLocator       Locates part occurrences within a document scope.
 * @param propertyExtractor Extracts property values from a document scope.
 * @param maxDepth          Maximum depth for Pass 1. Parts deeper than this
 *                          produce [PartInstances] with empty [PartInstances.occurrences].
 *                          Defaults to [Int.MAX_VALUE] (no limit).
 */
class DocumentParserFsm(
    private val descriptor: Descriptor,
    private val source: DocumentSource,
    private val partLocator: PartLocator,
    private val propertyExtractor: PropertyExtractor,
    private val maxDepth: Int = Int.MAX_VALUE
) {
    private var _state: FsmState = FsmState.Idle

    /** The current state of the FSM. */
    val state: FsmState get() = _state

    /**
     * Runs both passes and returns the [ExtractionRecord].
     *
     * May only be called once — throws [IllegalStateException] if the FSM is
     * not in [FsmState.Idle].
     *
     * Any exception raised by [DocumentSource], [PartLocator], or
     * [PropertyExtractor] causes the FSM to transition to [FsmState.Failed]
     * and is rethrown wrapped in an [ExtractionException].
     */
    fun run(): ExtractionRecord {
        check(_state is FsmState.Idle) {
            "DocumentParserFsm.run() called in state $_state — FSM is single-use"
        }
        return try {
            val root = source.load()

            // Pass 1: locate all parts BFS
            _state = FsmState.Locating(descriptor, currentDepth = 0, maxDepth = maxDepth)
            val locatedParts = locateAll(root)

            // Pass 2: extract all properties DFS
            _state = FsmState.Extracting(descriptor, emptyList())
            val result = extractLocatedRoot(locatedParts, root)

            _state = FsmState.Done(result)
            result
        } catch (e: ExtractionException) {
            _state = FsmState.Failed(e)
            throw e
        } catch (e: Exception) {
            val wrapped = ExtractionException("Extraction failed: ${e.message}", e)
            _state = FsmState.Failed(wrapped)
            throw wrapped
        }
    }

    // ── Pass 1: BFS part location ─────────────────────────────────────────────

    private fun locateAll(root: Document): List<LocatedInstances> {
        val topLevelSink = mutableListOf<LocatedInstances>()
        val queue = ArrayDeque<BfsItem>()

        if (maxDepth > 0) {
            descriptor.parts.forEach { part ->
                queue.addLast(BfsItem(part, root, depth = 1, sink = topLevelSink))
            }
        }

        while (queue.isNotEmpty()) {
            val (part, parentScope, depth, sink) = queue.removeFirst()
            _state = FsmState.Locating(descriptor, currentDepth = depth, maxDepth = maxDepth)

            val locatedScopes = partLocator.locate(part, parentScope)

            val records = locatedScopes.map { (scope, location) ->
                val childSink = mutableListOf<LocatedInstances>()
                if (depth < maxDepth) {
                    part.parts.forEach { childPart ->
                        queue.addLast(BfsItem(childPart, scope, depth + 1, childSink))
                    }
                }
                LocatedRecord(scope, location, childSink)
            }
            sink.add(LocatedInstances(part, records))
        }

        return topLevelSink
    }

    // ── Pass 2: DFS property extraction ──────────────────────────────────────

    private fun extractLocatedRoot(locatedParts: List<LocatedInstances>, root: Document): ExtractionRecord {
        val topLevelProperties = descriptor.properties.map { prop ->
            PropertyRecord(prop, propertyExtractor.extract(prop, root))
        }
        val partInstances = descriptor.parts.map { part ->
            val located = locatedParts.find { it.part === part }
            if (located != null) extractLocatedInstances(located, emptyList())
            else PartInstances(part, emptyList())
        }
        return ExtractionRecord(topLevelProperties, partInstances)
    }

    private fun extractLocatedInstances(located: LocatedInstances, parentPath: List<PartFrame>): PartInstances {
        val occurrences = located.occurrences.map { record ->
            val newPath = parentPath + PartFrame(located.part, record.scope, record.location)
            _state = FsmState.Extracting(descriptor, newPath)

            val properties = located.part.properties.map { prop ->
                PropertyRecord(prop, propertyExtractor.extract(prop, record.scope))
            }
            val children = located.part.parts.map { childPart ->
                val locatedChild = record.children.find { it.part === childPart }
                if (locatedChild != null) extractLocatedInstances(locatedChild, newPath)
                else PartInstances(childPart, emptyList())
            }
            PartRecord(located.part, record.location, properties, children)
        }
        return PartInstances(located.part, occurrences)
    }
}

/**
 * Thrown by [DocumentParserFsm] when an externality ([DocumentSource],
 * [PartLocator], or [PropertyExtractor]) raises an exception during traversal.
 */
class ExtractionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
