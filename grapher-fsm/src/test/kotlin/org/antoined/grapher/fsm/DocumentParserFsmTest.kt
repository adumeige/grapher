package org.antoined.grapher.fsm

import org.antoined.grapher.Descriptor
import org.antoined.grapher.Multiplicity
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import org.antoined.grapher.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ── Test doubles ──────────────────────────────────────────────────────────────

private object RootDoc : Document
private class ScopedDoc(val label: String) : Document

private fun descriptor(
    vararg properties: Property,
    parts: List<Part> = emptyList()
) = Descriptor("t", "T", "test", "1.0", parts = parts, properties = properties.toList())

private fun fsm(
    descriptor: Descriptor        = descriptor(),
    source: DocumentSource        = DocumentSource { RootDoc },
    locator: PartLocator          = PartLocator { _, _ -> emptyList() },
    extractor: PropertyExtractor  = PropertyExtractor { _, _ -> emptyList() },
    maxDepth: Int                 = Int.MAX_VALUE
) = DocumentParserFsm(descriptor, source, locator, extractor, maxDepth)

// ── Tests ─────────────────────────────────────────────────────────────────────

class DocumentParserFsmTest {

    // 1 ── Empty descriptor ────────────────────────────────────────────────────

    @Test fun `empty descriptor produces empty extraction record and Done state`() {
        val f = fsm()
        val result = f.run()
        assertTrue(result.properties.isEmpty())
        assertTrue(result.parts.isEmpty())
        assertIs<FsmState.Done>(f.state)
    }

    // 2 ── Top-level properties ────────────────────────────────────────────────

    @Test fun `top-level properties are extracted with correct values`() {
        val d = descriptor(
            Property("name",   Type.STRING, Multiplicity.ONE),
            Property("amount", Type.NUMBER, Multiplicity.ONE)
        )
        val result = fsm(
            descriptor = d,
            extractor  = PropertyExtractor { prop, _ -> listOf(Located("value-of-${prop.name}", null)) }
        ).run()

        assertEquals(2, result.properties.size)
        assertEquals(listOf("value-of-name"),   result.properties[0].values)
        assertEquals(listOf("value-of-amount"), result.properties[1].values)
    }

    // 3 ── Single scalar part ──────────────────────────────────────────────────

    @Test fun `scalar part produces one PartRecord with extracted properties`() {
        val scope = ScopedDoc("section-scope")
        val part  = Part("section", Multiplicity.ONE,
            properties = listOf(Property("code", Type.STRING, Multiplicity.ONE)))
        val d = descriptor(parts = listOf(part))

        val result = fsm(
            descriptor = d,
            locator    = PartLocator { _, _ -> listOf(Located(scope, null)) },
            extractor  = PropertyExtractor { prop, ctx ->
                listOf(Located("${prop.name}@${(ctx as ScopedDoc).label}", null))
            }
        ).run()

        val instances = result.parts.single()
        assertEquals(1, instances.occurrences.size)
        assertEquals(listOf("code@section-scope"), instances.occurrences[0].properties[0].values)
    }

    // 4 ── Repeating part ──────────────────────────────────────────────────────

    @Test fun `ONE_MANY part produces one PartRecord per located scope`() {
        val part  = Part("item", Multiplicity.ONE_MANY,
            properties = listOf(Property("label", Type.STRING, Multiplicity.ONE)))
        val d = descriptor(parts = listOf(part))

        val result = fsm(
            descriptor = d,
            locator    = PartLocator { _, _ -> listOf(Located(ScopedDoc("a"), null), Located(ScopedDoc("b"), null)) },
            extractor  = PropertyExtractor { _, ctx -> listOf(Located((ctx as ScopedDoc).label, null)) }
        ).run()

        val instances = result.parts.single()
        assertEquals(2, instances.occurrences.size)
        assertEquals(listOf("a"), instances.occurrences[0].properties[0].values)
        assertEquals(listOf("b"), instances.occurrences[1].properties[0].values)
    }

    // 5 ── Absent optional part ────────────────────────────────────────────────

    @Test fun `ZERO_ONE absent part produces empty occurrences and state is Done`() {
        val d = descriptor(parts = listOf(Part("optional", Multiplicity.ZERO_ONE)))
        val f = fsm(descriptor = d, locator = PartLocator { _, _ -> emptyList() })
        val result = f.run()
        assertTrue(result.parts.single().occurrences.isEmpty())
        assertIs<FsmState.Done>(f.state)
    }

    // 6 ── Deeply nested parts ─────────────────────────────────────────────────

    @Test fun `three-level nested parts resolve to correct PartRecord tree`() {
        val leaf = Part("tax",      Multiplicity.ZERO_MANY,
            properties = listOf(Property("rate", Type.NUMBER, Multiplicity.ONE)))
        val mid  = Part("lineItem", Multiplicity.ONE_MANY, parts = listOf(leaf))
        val root = Part("invoice",  Multiplicity.ONE,      parts = listOf(mid))
        val d    = descriptor(parts = listOf(root))

        val result = fsm(
            descriptor = d,
            locator = PartLocator { part, _ ->
                when (part.name) {
                    "invoice"  -> listOf(Located(ScopedDoc("invoice"), null))
                    "lineItem" -> listOf(Located(ScopedDoc("line"), null))
                    "tax"      -> listOf(Located(ScopedDoc("tax-scope"), null))
                    else       -> emptyList()
                }
            },
            extractor = PropertyExtractor { _, ctx -> listOf(Located((ctx as ScopedDoc).label, null)) }
        ).run()

        val taxRecord = result.parts.single()          // invoice PartInstances
            .occurrences.single()                      // invoice PartRecord
            .parts.single()                            // lineItem PartInstances
            .occurrences.single()                      // lineItem PartRecord
            .parts.single()                            // tax PartInstances
            .occurrences.single()                      // tax PartRecord

        assertEquals("tax", taxRecord.part.name)
        assertEquals(listOf("tax-scope"), taxRecord.properties[0].values)
    }

    // 7 ── State transitions ───────────────────────────────────────────────────

    @Test fun `state is Idle before run and Done after successful run`() {
        val f = fsm()
        assertIs<FsmState.Idle>(f.state)
        f.run()
        assertIs<FsmState.Done>(f.state)
    }

    @Test fun `state is Locating during PartLocator call in Pass 1`() {
        val part = Part("section", Multiplicity.ONE)
        val d    = descriptor(parts = listOf(part))

        var stateAtLocate: FsmState? = null
        lateinit var fRef: DocumentParserFsm
        fRef = DocumentParserFsm(d, { RootDoc }, { _, _ ->
            stateAtLocate = fRef.state
            listOf(Located(ScopedDoc("s"), null))
        }, { _, _ -> emptyList() })
        fRef.run()

        val locating = assertIs<FsmState.Locating>(stateAtLocate)
        assertEquals(d, locating.descriptor)
        assertEquals(1, locating.currentDepth)
    }

    @Test fun `Extracting path has one PartFrame while processing a part occurrence`() {
        val prop  = Property("v", Type.STRING, Multiplicity.ONE)
        val part  = Part("section", Multiplicity.ONE, properties = listOf(prop))
        val d     = descriptor(parts = listOf(part))
        val scope = ScopedDoc("s")

        var capturedPath: List<PartFrame>? = null
        lateinit var fRef: DocumentParserFsm
        fRef = DocumentParserFsm(d, { RootDoc },
            { _, _ -> listOf(Located(scope, null)) },
            { _, _ ->
                capturedPath = (fRef.state as? FsmState.Extracting)?.path
                emptyList()
            }
        )
        fRef.run()

        val path = capturedPath!!
        assertEquals(1, path.size)
        assertEquals(part,  path[0].part)
        assertEquals(scope, path[0].scope)
    }

    // 8 ── Single-use enforcement ──────────────────────────────────────────────

    @Test fun `calling run() twice throws IllegalStateException`() {
        val f = fsm()
        f.run()
        assertFailsWith<IllegalStateException> { f.run() }
    }

    @Test fun `calling run() on a Failed FSM also throws IllegalStateException`() {
        val f = fsm(source = DocumentSource { throw RuntimeException("boom") })
        assertFailsWith<ExtractionException> { f.run() }
        assertIs<FsmState.Failed>(f.state)
        assertFailsWith<IllegalStateException> { f.run() }
    }

    // 9 ── DocumentSource exception handling ───────────────────────────────────

    @Test fun `DocumentSource exception transitions to Failed and wraps in ExtractionException`() {
        val cause = RuntimeException("load error")
        val f     = fsm(source = DocumentSource { throw cause })

        val ex = assertFailsWith<ExtractionException> { f.run() }
        assertEquals(cause, ex.cause)
        assertIs<FsmState.Failed>(f.state)
    }

    // 10 ── PartLocator exception handling ─────────────────────────────────────

    @Test fun `PartLocator exception transitions to Failed and wraps in ExtractionException`() {
        val part  = Part("section", Multiplicity.ONE)
        val d     = descriptor(parts = listOf(part))
        val cause = RuntimeException("locate error")

        val f = fsm(descriptor = d, locator = PartLocator { _, _ -> throw cause })

        val ex = assertFailsWith<ExtractionException> { f.run() }
        assertEquals(cause, ex.cause)
        assertIs<FsmState.Failed>(f.state)
    }

    // 11 ── PropertyExtractor exception handling ───────────────────────────────

    @Test fun `PropertyExtractor exception transitions to Failed and wraps in ExtractionException`() {
        val d     = descriptor(Property("v", Type.STRING, Multiplicity.ONE))
        val cause = RuntimeException("extract error")

        val f = fsm(descriptor = d, extractor = PropertyExtractor { _, _ -> throw cause })

        val ex = assertFailsWith<ExtractionException> { f.run() }
        assertEquals(cause, ex.cause)
        assertIs<FsmState.Failed>(f.state)
    }

    // A ── maxDepth limits location depth ─────────────────────────────────────

    @Test fun `maxDepth=1 locates top-level part but child parts have empty occurrences`() {
        val child   = Part("child", Multiplicity.ONE_MANY,
            properties = listOf(Property("qty", Type.NUMBER, Multiplicity.ONE)))
        val parent  = Part("parent", Multiplicity.ONE, parts = listOf(child))
        val d       = descriptor(parts = listOf(parent))

        val result = fsm(
            descriptor = d,
            locator    = PartLocator { _, _ -> listOf(Located(ScopedDoc("p"), null)) },
            maxDepth   = 1
        ).run()

        val parentInstances = result.parts.single()
        assertEquals(1, parentInstances.occurrences.size)       // parent was located

        val childInstances = parentInstances.occurrences.single().parts.single()
        assertEquals("child",          childInstances.part.name) // Part ref preserved
        assertTrue(childInstances.occurrences.isEmpty())          // child was NOT located
    }

    // B ── maxDepth=0 locates nothing ─────────────────────────────────────────

    @Test fun `maxDepth=0 makes no PartLocator calls and all parts have empty occurrences`() {
        val part = Part("section", Multiplicity.ONE)
        val d    = descriptor(parts = listOf(part))

        var locatorCalled = false
        val result = fsm(
            descriptor = d,
            locator    = PartLocator { _, _ -> locatorCalled = true; emptyList() },
            maxDepth   = 0
        ).run()

        assertFalse(locatorCalled, "PartLocator must not be called when maxDepth=0")
        assertTrue(result.parts.single().occurrences.isEmpty())
    }

    // C ── Locating state during Pass 1 ───────────────────────────────────────

    @Test fun `state is Locating with correct currentDepth during each BFS level`() {
        val child  = Part("child",  Multiplicity.ONE)
        val parent = Part("parent", Multiplicity.ONE, parts = listOf(child))
        val d      = descriptor(parts = listOf(parent))

        val depthsObserved = mutableListOf<Int>()
        lateinit var fRef: DocumentParserFsm
        fRef = DocumentParserFsm(d, { RootDoc }, { _, _ ->
            depthsObserved += (fRef.state as FsmState.Locating).currentDepth
            listOf(Located(ScopedDoc("s"), null))
        }, { _, _ -> emptyList() })
        fRef.run()

        assertEquals(listOf(1, 2), depthsObserved)
    }

    // D ── Extracting state during Pass 2 (regression guard) ──────────────────

    @Test fun `state is Extracting with correct path during PropertyExtractor calls in Pass 2`() {
        val prop  = Property("v", Type.STRING, Multiplicity.ONE)
        val part  = Part("section", Multiplicity.ONE, properties = listOf(prop))
        val d     = descriptor(parts = listOf(part))
        val scope = ScopedDoc("s")

        var capturedPath: List<PartFrame>? = null
        lateinit var fRef: DocumentParserFsm
        fRef = DocumentParserFsm(d, { RootDoc },
            { _, _ -> listOf(Located(scope, null)) },
            { _, _ ->
                capturedPath = (fRef.state as? FsmState.Extracting)?.path
                emptyList()
            }
        )
        fRef.run()

        val path = capturedPath!!
        assertEquals(1,     path.size)
        assertEquals(part,  path[0].part)
        assertEquals(scope, path[0].scope)
    }

    // E ── BFS ordering ────────────────────────────────────────────────────────

    @Test fun `PartLocator is called BFS-order, all depth-1 parts before any depth-2 parts`() {
        val child  = Part("child",  Multiplicity.ONE)
        val parent = Part("parent", Multiplicity.ONE, parts = listOf(child))
        val d      = descriptor(parts = listOf(parent))

        val callOrder = mutableListOf<String>()
        fsm(
            descriptor = d,
            locator    = PartLocator { part, _ ->
                callOrder += part.name
                listOf(Located(ScopedDoc(part.name), null))
            }
        ).run()

        assertEquals(listOf("parent", "child"), callOrder)
    }

    // F ── Multiple parent occurrences each get independent child locates ──────

    @Test fun `each parent occurrence independently locates its child parts`() {
        val child  = Part("child",  Multiplicity.ONE_MANY,
            properties = listOf(Property("id", Type.STRING, Multiplicity.ONE)))
        val parent = Part("parent", Multiplicity.ONE_MANY, parts = listOf(child))
        val d      = descriptor(parts = listOf(parent))

        val locatorArgs = mutableListOf<Pair<String, String>>()

        val result = DocumentParserFsm(d, { RootDoc },
            PartLocator { part, scope ->
                val label = (scope as? ScopedDoc)?.label ?: "root"
                locatorArgs += part.name to label
                when (part.name) {
                    "parent" -> listOf(Located(ScopedDoc("p1"), null), Located(ScopedDoc("p2"), null))
                    "child"  -> listOf(Located(ScopedDoc("c-under-$label"), null))
                    else     -> emptyList()
                }
            },
            PropertyExtractor { _, ctx -> listOf(Located((ctx as ScopedDoc).label, null)) }
        ).run()

        // parent located from root; child located once per parent occurrence (BFS)
        assertEquals(listOf("parent" to "root", "child" to "p1", "child" to "p2"), locatorArgs)

        val occurrences = result.parts.single().occurrences
        assertEquals(listOf("c-under-p1"), occurrences[0].parts.single().occurrences[0].properties[0].values)
        assertEquals(listOf("c-under-p2"), occurrences[1].parts.single().occurrences[0].properties[0].values)
    }
}
