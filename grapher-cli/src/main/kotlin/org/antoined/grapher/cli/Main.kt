package org.antoined.grapher.cli

import org.antoined.grapher.fsm.DocumentParserFsm
import org.antoined.grapher.fsm.PartLocator
import org.antoined.grapher.io.descriptorFromJson
import org.antoined.grapher.io.descriptorFromYaml
import org.antoined.grapher.io.toJsonSchemaString
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

internal fun log(msg: String) {
    val ts = LocalTime.now().format(timeFormatter)
    System.err.println("[$ts] $msg")
}

private val LLM_FLAGS = setOf(
    "--llm-base-url",
    "--llm-api-key",
    "--llm-model",
    "--llm-temperature",
    "--llm-max-tokens",
    "--llm-system-prompt",
    "--dump-dir",
)

fun main() {
    boot(
        arrayOf(
            "--one-shot",
            "local/format/tapi.yaml",
            "local/input/JTAPI_001259_2024_A_4153_2024.pdf",
            "--llm-base-url",
            "http://heavy:8080/v1",
            "--llm-model",
            "qwen3.5-9B-nothink",
            "--dump-dir",
            "trace",
            "--llm-max-tokens",
            "${1024 * 64}"
        )
    )
}

fun boot(args: Array<String>) {
    val parsed = parseArgs(args)

    when (parsed.command) {
        Command.SCHEMA -> {
            val formatFile = requireFile(parsed.positional[0], "Format file")
            println(loadDescriptor(formatFile).toJsonSchemaString())
        }

        Command.EXTRACT -> {
            val formatFile = requireFile(parsed.positional[0], "Format file")
            val documentFile = requireFile(parsed.positional[1], "Document file")
            val descriptor = loadDescriptor(formatFile)
            val llmConfig = LlmConfig.resolve(parsed.flags)
            val dumpDir = parsed.flags["dump-dir"]?.let { File(it) }
            val extractor = Extractor(llmConfig, dumpDir)

            log("Descriptor : ${descriptor.namespace}/${descriptor.name} v${descriptor.version}")
            log("Document   : ${documentFile.path}")
            log("LLM        : ${llmConfig.baseUrl}  model=${llmConfig.model}  temp=${llmConfig.temperature}  maxTokens=${llmConfig.maxTokens}")
            log("Parts      : ${descriptor.parts.size} top-level, ${descriptor.properties.size} top-level properties")

            val start = System.currentTimeMillis()
            val record = DocumentParserFsm(
                descriptor = descriptor,
                source = extractor.documentSource(documentFile.path),
                partLocator = PartLocator { _, _ -> emptyList() },  // unused, levelLocator handles it
                propertyExtractor = extractor.propertyExtractor(descriptor),
                levelLocator = extractor.levelLocator(descriptor),
            ).run()
            val elapsed = System.currentTimeMillis() - start

            log("Done in ${elapsed}ms")
            println(record.toJson(descriptor))
        }

        Command.ONE_SHOT -> {
            val formatFile = requireFile(parsed.positional[0], "Format file")
            val documentFile = requireFile(parsed.positional[1], "Document file")
            val descriptor = loadDescriptor(formatFile)
            val llmConfig = LlmConfig.resolve(parsed.flags)
            val dumpDir = parsed.flags["dump-dir"]?.let { File(it) }
            val extractor = Extractor(llmConfig, dumpDir)

            log("Mode       : one-shot")
            log("Descriptor : ${descriptor.namespace}/${descriptor.name} v${descriptor.version}")
            log("Document   : ${documentFile.path}")
            log("LLM        : ${llmConfig.baseUrl}  model=${llmConfig.model}  temp=${llmConfig.temperature}  maxTokens=${llmConfig.maxTokens}")

            val start = System.currentTimeMillis()
            val json = extractor.extractOneShot(descriptor, documentFile.path)
            val elapsed = System.currentTimeMillis() - start

            log("Done in ${elapsed}ms")
            println(json)
        }

        Command.HELP -> printUsage()
    }
}

// ── Arg parsing ─────────────────────────────────────────────────────────────

private enum class Command { SCHEMA, EXTRACT, ONE_SHOT, HELP }

private data class ParsedArgs(
    val command: Command,
    val positional: List<String>,
    val flags: Map<String, String>,
)

private fun parseArgs(args: Array<String>): ParsedArgs {
    val flags = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()
    var schemaMode = false
    var oneShotMode = false

    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--help" || arg == "-h" -> return ParsedArgs(Command.HELP, emptyList(), emptyMap())
            arg == "--schema" -> {
                schemaMode = true; i++
            }
            arg == "--one-shot" -> {
                oneShotMode = true; i++
            }

            arg in LLM_FLAGS -> {
                if (i + 1 >= args.size) {
                    System.err.println("Missing value for $arg")
                    exitProcess(1)
                }
                flags[arg.removePrefix("--")] = args[i + 1]
                i += 2
            }

            arg.startsWith("--") -> {
                System.err.println("Unknown option: $arg")
                printUsage()
                exitProcess(1)
            }

            else -> {
                positional += arg; i++
            }
        }
    }

    return when {
        schemaMode -> {
            if (positional.size != 1) {
                System.err.println("--schema requires exactly one format file")
                exitProcess(1)
            }
            ParsedArgs(Command.SCHEMA, positional, flags)
        }

        oneShotMode -> {
            if (positional.size != 2) {
                System.err.println("--one-shot requires a format file and a document file")
                exitProcess(1)
            }
            ParsedArgs(Command.ONE_SHOT, positional, flags)
        }

        positional.size == 2 -> ParsedArgs(Command.EXTRACT, positional, flags)
        else -> ParsedArgs(Command.HELP, emptyList(), emptyMap())
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun requireFile(path: String, label: String): File =
    File(path).also { require(it.exists()) { "$label not found: ${it.path}" } }

private fun loadDescriptor(formatFile: File) = when (formatFile.extension.lowercase()) {
    "json" -> descriptorFromJson(formatFile)
    "yaml", "yml" -> descriptorFromYaml(formatFile)
    else -> {
        System.err.println("Unsupported format file extension: .${formatFile.extension} (expected .json, .yaml, or .yml)")
        exitProcess(1)
    }
}

private fun printUsage() {
    System.err.println(
        """
        |Usage: grapher-cli [options] <format-file> <document-file>
        |       grapher-cli --one-shot [options] <format-file> <document-file>
        |       grapher-cli --schema <format-file>
        |
        |Commands:
        |  <format-file> <document-file>   Extract structured data from a document (multi-pass FSM)
        |  --one-shot <format> <document>   Extract in a single LLM call using JSON Schema
        |  --schema <format-file>           Print the JSON Schema for the given format
        |
        |LLM options:
        |  --llm-base-url <url>             LLM endpoint          (default: http://localhost:8080/v1)
        |  --llm-api-key <key>              API key                (default: no-key)
        |  --llm-model <name>               Model name             (default: default)
        |  --llm-temperature <0.0-1.0>      Sampling temperature   (default: 0.2)
        |  --llm-max-tokens <n>             Max response tokens    (default: 4096)
        |  --llm-system-prompt <prompt>     Override system prompt
        |
        |Debug options:
        |  --dump-dir <dir>                 Dump prompts/responses to step-XX-*.txt files
        |
        |Environment variables (override defaults, overridden by CLI flags):
        |  GRAPHER_LLM_BASE_URL             LLM endpoint
        |  GRAPHER_LLM_API_KEY              API key
        |  GRAPHER_LLM_MODEL                Model name
        |  GRAPHER_LLM_TEMPERATURE          Sampling temperature
        |  GRAPHER_LLM_MAX_TOKENS           Max response tokens
        |  GRAPHER_LLM_SYSTEM_PROMPT        System prompt
        |
        |A .env file in the working directory is also loaded (lowest priority).
    """.trimMargin()
    )
}
