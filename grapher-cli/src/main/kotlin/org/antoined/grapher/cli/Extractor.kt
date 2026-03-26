package org.antoined.grapher.cli

import dev.kreuzberg.Kreuzberg
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import org.antoined.grapher.Descriptor
import org.antoined.grapher.Part
import org.antoined.grapher.Property
import org.antoined.grapher.fsm.*
import java.io.File

/**
 * A plain-text [Document] wrapping Kreuzberg's extracted content.
 *
 * [text] is the full text for the root document, or a scoped section for
 * a located part.
 */
class TextDocument(val text: String) : Document

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
            appendLine("=== SYSTEM ===")
            appendLine(config.systemPrompt)
            appendLine()
            appendLine("=== USER ===")
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

    /** Use Kreuzberg to extract text from a file and wrap it as a [TextDocument]. */
    fun documentSource(path: String) = DocumentSource {
        log("Loading document: $path")
        val start = System.currentTimeMillis()
        val result = Kreuzberg.extractFile(path)
        val elapsed = System.currentTimeMillis() - start
        log("Document loaded: ${result.content.length} chars in ${elapsed}ms")
        TextDocument(result.content)
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
        val result = parseLevelLocatorResponse(response, parts)
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
        val results = parsePropertyExtractorResponse(response)
        log("EXTRACT ${property.name}: ${results.map { it.value }}")
        results
    }

    // ── Prompt builders ─────────────────────────────────────────────────────────

    private fun buildLevelLocatorPrompt(parts: List<Part>, descriptor: Descriptor, text: String): String {
        val sectionsDescription = parts.joinToString("\n") { part ->
            val hints = if (part.hints.isNotEmpty()) " — ${part.hints.joinToString(". ")}" else ""
            val mult = if (part.multiplicity.max > 1) " (REPEATING: may appear multiple times)" else ""
            "  - \"${part.name}\"$mult$hints"
        }

        return """
            |You are segmenting a document into its structural sections.
            |Document type: ${descriptor.name} — ${descriptor.description}
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
        val hintsSection = if (property.hints.isNotEmpty())
            "\nHints: ${property.hints.joinToString(". ")}" else ""
        val patternSection = if (property.pattern != null)
            "\nExpected pattern: ${property.pattern}" else ""
        val typeGuide = "Expected type: ${property.type.name}"
        val multiplicityGuide = when {
            property.multiplicity.max == 1 -> "Extract exactly ONE value."
            else -> "There may be MULTIPLE values. Extract ALL of them."
        }

        return """
            |You are extracting structured data from a document.
            |Document type: ${descriptor.name} — ${descriptor.description}
            |
            |Task: Extract the value of "${property.name}" from the text below.
            |$typeGuide
            |$multiplicityGuide$hintsSection$patternSection
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
        parts: List<Part>
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
                result.getOrPut(part) { mutableListOf() }
                    .add(Located(TextDocument(text) as Document, null))
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

    private fun parsePropertyExtractorResponse(response: String): List<Located<String>> {
        val trimmed = response.trim()
        if (trimmed.equals("NONE", ignoreCase = true) || trimmed.isBlank()) {
            return emptyList()
        }

        return trimmed
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { Located(it, null) }
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
    indent: Int
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
                record.values.forEachIndexed { i, v ->
                    val comma = if (i < record.values.size - 1) "," else ""
                    sb.appendLine("$inner  ${jsonValue(v, record.property)}$comma")
                }
                sb.append("$inner]")
            } else {
                val value = record.values.firstOrNull()
                sb.append("$inner\"${record.property.name}\": ${jsonValue(value, record.property)}")
            }
        }
    }

    partInstances.forEach { instances ->
        entries.add {
            val isArray = instances.part.multiplicity.max > 1
            if (isArray) {
                sb.appendLine("$inner\"${instances.part.name}\": [")
                instances.occurrences.forEachIndexed { i, occ ->
                    renderObject(sb, instances.part.properties, instances.part.parts, occ.properties, occ.parts, indent + 2)
                    if (i < instances.occurrences.size - 1) sb.appendLine(",") else sb.appendLine()
                }
                sb.append("$inner]")
            } else {
                sb.appendLine("$inner\"${instances.part.name}\":")
                val occ = instances.occurrences.firstOrNull()
                if (occ != null) {
                    renderObject(sb, instances.part.properties, instances.part.parts, occ.properties, occ.parts, indent + 1)
                } else {
                    sb.append("${inner}null")
                }
            }
        }
    }

    entries.forEachIndexed { i, render ->
        render()
        if (i < entries.size - 1) sb.appendLine(",") else sb.appendLine()
    }

    sb.append("$pad}")
}

private fun jsonValue(value: String?, property: Property): String {
    if (value == null) return "null"
    return when (property.type) {
        org.antoined.grapher.Type.INT -> value.toLongOrNull()?.toString() ?: "\"$value\""
        org.antoined.grapher.Type.NUMBER -> value.toDoubleOrNull()?.toString() ?: "\"$value\""
        else -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}
