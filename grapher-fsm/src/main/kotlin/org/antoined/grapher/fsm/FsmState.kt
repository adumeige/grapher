package org.antoined.grapher.fsm

import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part

/**
 * One entry in the active traversal breadcrumb — which [Part] is currently
 * being processed, the [Document] scope it was located in, and the [Location]
 * within the parent where this occurrence was found (may be `null`).
 */
data class PartFrame(val part: Part, val scope: Document, val location: Location?)

/**
 * Explicit states of [DocumentParserFsm].
 *
 * ## Transitions
 * ```
 * Idle ──run()──► Locating ──pass 1 done──► Extracting ──pass 2 done──► Done
 *                     │                          │
 *                  exception                 exception
 *                     └──────────────────────────┴──► Failed
 * ```
 * [Done] and [Failed] are terminal — calling [DocumentParserFsm.run] again
 * throws [IllegalStateException].
 */
sealed class FsmState {

    /** Initial state — [DocumentParserFsm.run] has not yet been called. */
    data object Idle : FsmState()

    /**
     * Pass 1 active — BFS part location in progress.
     *
     * [currentDepth] is the BFS level currently being processed (1-based).
     * [maxDepth] is the configured depth limit passed to [DocumentParserFsm].
     * Updated as each level completes so observers can track coarse progress.
     */
    data class Locating(
        val descriptor: Descriptor,
        val currentDepth: Int,
        val maxDepth: Int
    ) : FsmState()

    /**
     * Pass 2 active — DFS property extraction in progress.
     *
     * [path] is the current traversal stack from root to the node being
     * processed — empty at the start of Pass 2, growing by one [PartFrame]
     * with each descent into a located part occurrence.
     */
    data class Extracting(
        val descriptor: Descriptor,
        val path: List<PartFrame>
    ) : FsmState()

    /** Terminal success — [result] holds the complete extraction. */
    data class Done(val result: ExtractionRecord) : FsmState()

    /** Terminal failure — [cause] is the exception that aborted the traversal. */
    data class Failed(val cause: Throwable) : FsmState()
}
