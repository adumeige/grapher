package org.antoined.grapher.cli

import dev.kreuzberg.Kreuzberg
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import org.antoined.grapher.fsm.*
import org.antoined.grapher.io.toJsonSchemaString
import java.io.File

/**
 * A plain-text [Document] wrapping Kreuzberg's extracted content.
 *
 * [text] is the full text for the root document, or a scoped section for
 * a located part.
 */
class TextDocument(val text: String) : Document

/**
 * A [Location] expressed as character and line coordinates within the source text.
 *
 * All indices are zero-based. [endChar] is exclusive.
 */
data class TextLocation(
    val startChar: Int,
    val endChar: Int,
    val startLine: Int,
    val endLine: Int,
) : Location {
    companion object {
        /** Find [needle] in [haystack] and return its [TextLocation], or `null` if not found. */
        fun find(needle: String, haystack: String): TextLocation? {
            val idx = haystack.indexOf(needle)
            if (idx < 0) return null
            val startLine = haystack.substring(0, idx).count { it == '\n' }
            val endLine = startLine + needle.count { it == '\n' }
            return TextLocation(
                startChar = idx,
                endChar = idx + needle.length,
                startLine = startLine,
                endLine = endLine,
            )
        }
    }
}

/**
 * Bridges Kreuzberg (document-to-text) and a langchain4j chat model (text-to-structured-data)
 * into the FSM's [DocumentSource], [PartLocator], and [PropertyExtractor] interfaces.
 */
class Extractor(private val config: LlmConfig, private val dumpDir: File? = null) {

    init {
        dumpDir?.mkdirs()
    }

    private val chatModel = OpenAiChatModel.builder()
        .baseUrl(config.baseUrl)
        .apiKey(config.apiKey)
        .modelName(config.model)
        .temperature(config.temperature)
        .maxTokens(config.maxTokens)
        .build()

    private var llmCallCount = 0

    private fun chat(label: String, prompt: String): String {
        llmCallCount++
        val step = "%02d".format(llmCallCount)
        log("  LLM #$step [$label] sending ${prompt.length} chars…")

        dump(step, label, "prompt") {
            appendLine("== SYSTEM ==")
            appendLine(config.systemPrompt)
            appendLine()
            appendLine("== USER ==")
            appendLine(prompt)
        }

        val start = System.currentTimeMillis()
        val response = chatModel.chat(SystemMessage(config.systemPrompt), UserMessage(prompt))
        val elapsed = System.currentTimeMillis() - start
        val text = response.aiMessage().text()
        val usage = response.tokenUsage()
        val usageStr = if (usage != null) " (in=${usage.inputTokenCount()} out=${usage.outputTokenCount()})" else ""
        log("  LLM #$step [$label] ${elapsed}ms, ${text.length} chars response$usageStr")

        dump(step, label, "response") {
            appendLine(text)
        }

        return text
    }

    private inline fun dump(step: String, label: String, suffix: String, content: StringBuilder.() -> Unit) {
        if (dumpDir == null) return
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9_:-]"), "_")
        val file = File(dumpDir, "step-${step}-${safeLabel}-${suffix}.txt")
        file.writeText(StringBuilder().apply(content).toString())
        log("  dumped → ${file.path}")
    }

    /** Strip control characters (except newline, tab, carriage return) that break JSON serialization. */
    private fun sanitizeText(text: String): String =
        text.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")

    /** Use Kreuzberg to extract text from a file and wrap it as a [TextDocument]. */
    fun documentSource(path: String) = DocumentSource {
        log("Loading document: $path")
        val start = System.currentTimeMillis()
        val result = Kreuzberg.extractFile(path)
        val elapsed = System.currentTimeMillis() - start
        val sanitized = sanitizeText(result.content)
        log("Document loaded: ${sanitized.length} chars in ${elapsed}ms")
        TextDocument(sanitized)
    }

    /**
     * Locates all sibling parts within a scope in a single LLM call.
     * The LLM segments the document into the requested sections at once.
     */
    fun levelLocator(descriptor: Descriptor) = LevelLocator { parts, context ->
        val text = (context as TextDocument).text
        if (text.isBlank()) {
            parts.forEach { log("LOCATE ${it.name}: skipped (blank text)") }
            return@LevelLocator emptyMap()
        }

        val names = parts.joinToString(", ") { "\"${it.name}\"" }
        log("LOCATE level [${parts.joinToString { it.name }}] in ${text.length} chars…")
        val prompt = buildLevelLocatorPrompt(parts, descriptor, text)
        val response = chat("locate:$names", prompt)
        val result = parseLevelLocatorResponse(response, parts, text)
        result.forEach { (part, located) ->
            log("LOCATE ${part.name}: found ${located.size} occurrence(s)")
        }
        result
    }

    /**
     * Extracts property values by asking the LLM to find values
     * matching the property definition in the scoped document text.
     */
    fun propertyExtractor(descriptor: Descriptor) = PropertyExtractor { property, context ->
        val text = (context as TextDocument).text
        if (text.isBlank()) {
            log("EXTRACT ${property.name}: skipped (blank text)")
            return@PropertyExtractor emptyList()
        }

        log("EXTRACT ${property.name} (${property.type}, ${property.multiplicity}) from ${text.length} chars…")
        val prompt = buildPropertyExtractorPrompt(property, descriptor, text)
        val response = chat("extract:${property.name}", prompt)
        val results = parsePropertyExtractorResponse(response, text)
        log("EXTRACT ${property.name}: ${results.map { it.value }}")
        results
    }

    // ── One-shot extraction ────────────────────────────────────────────────────

    /**
     * Extracts structured data in a single LLM call by passing the full JSON schema
     * and document text, returning the raw JSON response.
     */
    fun extractOneShot(descriptor: Descriptor, documentPath: String): String {
        val doc = documentSource(documentPath).load() as TextDocument
        val schema = descriptor.toJsonSchemaString()

        val prompt = """
            |You are extracting structured data from a document.
            |Document type: ${descriptor.name} — ${descriptor.description}
            |
            |Extract all fields from the document text below and return a single JSON object
            |conforming to this JSON Schema:
            |$schema
            |
            |RULES:
            |- Respond with ONLY the JSON object, no commentary, no markdown fences.
            |- If a value is not found, use null.
            |- For array fields, return an empty array [] if no values are found.
            |
            |DOCUMENT TEXT:
            |${doc.text}
        """.trimMargin()

        return chat("one-shot", prompt)
    }

    // ── Prompt builders ─────────────────────────────────────────────────────────

    private fun buildLevelLocatorPrompt(parts: List<Part>, descriptor: Descriptor, text: String): String {
        val sectionsDescription = parts.joinToString("\n") { part ->
            val hints = if (part.hints.isNotEmpty()) " — ${part.hints.joinToString(". ")}" else ""
            val mult = if (part.multiplicity.max > 1) " (REPEATING: may appear multiple times)" else ""
            "  - \"${part.name}\"$mult$hints"
        }

        val schemaBlock = descriptor.toJsonSchemaString()

        return """
            |You are segmenting a document into its structural sections.
            |Document type: ${descriptor.name} — ${descriptor.description}
            |
            |The document conforms to this JSON Schema:
            |$schemaBlock
            |
            |Task: Split the text below into these sections:
            |$sectionsDescription
            |
            |RESPONSE FORMAT:
            |For each section found, output a header line followed by the section's text:
            |
            |===SECTION: <section_name>===
            |<text belonging to this section>
            |
            |For repeating sections, output the header multiple times (once per occurrence):
            |
            |===SECTION: <section_name>===
            |<first occurrence text>
            |===SECTION: <section_name>===
            |<second occurrence text>
            |
            |Output ONLY the section headers and the document text. No commentary.
            |If a section is not found, simply omit it.
            |
            |DOCUMENT TEXT:
            |$text
        """.trimMargin()
    }

    private fun buildPropertyExtractorPrompt(property: Property, descriptor: Descriptor, text: String): String {
        val schema = property.toJsonSchemaString()
        val multiplicityGuide = when {
            property.multiplicity.max == 1 -> "Extract exactly ONE value."
            else -> "There may be MULTIPLE values. Extract ALL of them."
        }

        return """
            |You are extracting structured data from a document.
            |Document type: ${descriptor.name} — ${descriptor.description}
            |
            |Task: Extract the value of "${property.name}" from the text below.
            |$multiplicityGuide
            |
            |The extracted value must conform to this JSON Schema:
            |$schema
            |
            |RESPOND with each value on its own line. No labels, no commentary.
            |If the value is not found, respond with exactly: NONE
            |
            |TEXT:
            |$text
        """.trimMargin()
    }

    // ── Response parsers ────────────────────────────────────────────────────────

    private val sectionHeaderPattern = Regex("""^===SECTION:\s*(.+?)\s*===$""")

    private fun parseLevelLocatorResponse(
        response: String,
        parts: List<Part>,
        sourceText: String
    ): Map<Part, List<Located<Document>>> {
        val partsByName = parts.associateBy { it.name.lowercase() }
        val result = mutableMapOf<Part, MutableList<Located<Document>>>()
        parts.forEach { result[it] = mutableListOf() }

        var currentPart: Part? = null
        val currentText = StringBuilder()

        fun flush() {
            val part = currentPart ?: return
            val text = currentText.toString().trim()
            if (text.isNotBlank()) {
                val location = TextLocation.find(text, sourceText)
                result.getOrPut(part) { mutableListOf() }
                    .add(Located(TextDocument(text) as Document, location))
            }
            currentText.clear()
        }

        for (line in response.lines()) {
            val match = sectionHeaderPattern.find(line.trim())
            if (match != null) {
                flush()
                val name = match.groupValues[1].trim().lowercase()
                currentPart = partsByName[name]
                if (currentPart == null) {
                    log("  WARN: LLM returned unknown section \"${match.groupValues[1]}\", ignoring")
                }
            } else {
                if (currentPart != null) {
                    currentText.appendLine(line)
                }
            }
        }
        flush()

        return result
    }

    private fun parsePropertyExtractorResponse(response: String, sourceText: String): List<Located<String>> {
        val trimmed = response.trim()
        if (trimmed.equals("NONE", ignoreCase = true) || trimmed.isBlank()) {
            return emptyList()
        }

        return trimmed
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { Located(it, TextLocation.find(it, sourceText)) }
    }
}

// ── Output rendering ────────────────────────────────────────────────────────

/**
 * Renders an [ExtractionRecord] as a JSON string matching the descriptor's
 * JSON Schema structure.
 */
fun ExtractionRecord.toJson(descriptor: Descriptor): String {
    val sb = StringBuilder()
    renderObject(sb, descriptor.properties, descriptor.parts, this.properties, this.parts, indent = 0)
    return sb.toString()
}

private fun renderObject(
    sb: StringBuilder,
    propDefs: List<Property>,
    partDefs: List<Part>,
    propRecords: List<PropertyRecord>,
    partInstances: List<PartInstances>,
    indent: Int,
    extraEntries: List<() -> Unit> = emptyList()
) {
    val pad = "  ".repeat(indent)
    val inner = "  ".repeat(indent + 1)
    sb.appendLine("$pad{")

    val entries = mutableListOf<() -> Unit>()

    propRecords.forEach { record ->
        entries.add {
            val isArray = record.property.multiplicity.max > 1
            if (isArray) {
                sb.appendLine("$inner\"${record.property.name}\": [")
                record.locatedValues.forEachIndexed { i, lv ->
                    val comma = if (i < record.locatedValues.size - 1) "," else ""
                    sb.appendLine("$inner  ${jsonValue(lv.value, record.property)}$comma")
                }
                sb.append("$inner]")
            } else {
                val value = record.values.firstOrNull()
                sb.append("$inner\"${record.property.name}\": ${jsonValue(value, record.property)}")
            }
        }
        val source = record.locatedValues.firstOrNull()?.location as? TextLocation
        if (source != null) {
            entries.add {
                sb.append("$inner\"${record.property.name}_source\": ${jsonLocation(source)}")
            }
        }
    }

    partInstances.forEach { instances ->
        entries.add {
            val isArray = instances.part.multiplicity.max > 1
            if (isArray) {
                sb.appendLine("$inner\"${instances.part.name}\": [")
                instances.occurrences.forEachIndexed { i, occ ->
                    renderPartRecord(sb, instances.part, occ, indent + 2)
                    if (i < instances.occurrences.size - 1) sb.appendLine(",") else sb.appendLine()
                }
                sb.append("$inner]")
            } else {
                sb.appendLine("$inner\"${instances.part.name}\":")
                val occ = instances.occurrences.firstOrNull()
                if (occ != null) {
                    renderPartRecord(sb, instances.part, occ, indent + 1)
                } else {
                    sb.append("${inner}null")
                }
            }
        }
    }

    val allEntries = extraEntries + entries
    allEntries.forEachIndexed { i, render ->
        render()
        if (i < allEntries.size - 1) sb.appendLine(",") else sb.appendLine()
    }

    sb.append("$pad}")
}

private fun renderPartRecord(sb: StringBuilder, part: Part, occ: PartRecord, indent: Int) {
    val inner = "  ".repeat(indent + 1)
    val sourceEntries = mutableListOf<() -> Unit>()
    val source = occ.location as? TextLocation
    if (source != null) {
        sourceEntries.add {
            sb.append("$inner\"_source\": ${jsonLocation(source)}")
        }
    }
    renderObject(sb, part.properties, part.parts, occ.properties, occ.parts, indent, sourceEntries)
}

private fun jsonLocation(loc: TextLocation): String =
    """{"startChar": ${loc.startChar}, "endChar": ${loc.endChar}, "startLine": ${loc.startLine}, "endLine": ${loc.endLine}}"""

private fun jsonValue(value: String?, property: Property): String {
    if (value == null) return "null"
    return when (property.type) {
        org.antoined.grapher.Type.INT -> value.toLongOrNull()?.toString() ?: "\"$value\""
        org.antoined.grapher.Type.NUMBER -> value.toDoubleOrNull()?.toString() ?: "\"$value\""
        else -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}
