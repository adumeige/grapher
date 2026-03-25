package org.antoined.grapher.cli

import org.antoined.grapher.io.descriptorFromJson
import org.antoined.grapher.io.descriptorFromYaml
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: grapher-cli <format-file> <document-file>")
        System.err.println()
        System.err.println("  <format-file>   Path to a descriptor file (.json or .yaml/.yml)")
        System.err.println("  <document-file> Path to the document to extract from")
        exitProcess(1)
    }

    val formatFile   = File(args[0]).also { require(it.exists()) { "Format file not found: ${it.path}" } }
    val documentFile = File(args[1]).also { require(it.exists()) { "Document file not found: ${it.path}" } }

    val descriptor = when (formatFile.extension.lowercase()) {
        "json"       -> descriptorFromJson(formatFile)
        "yaml", "yml" -> descriptorFromYaml(formatFile)
        else -> {
            System.err.println("Unsupported format file extension: .${formatFile.extension} (expected .json, .yaml, or .yml)")
            exitProcess(1)
        }
    }

    println("Descriptor : ${descriptor.namespace}/${descriptor.name} v${descriptor.version}")
    println("Document   : ${documentFile.path}")

    // FIXME: wire up PartLocator and PropertyExtractor implementations for the
    //        target document type, then run the extraction loop:
    //
    //   val record = DocumentParserFsm(
    //       descriptor        = descriptor,
    //       source            = DocumentSource { /* load documentFile */ },
    //       partLocator       = PartLocator    { part, ctx -> TODO() },
    //       propertyExtractor = PropertyExtractor { prop, ctx -> TODO() }
    //   ).run()
    //
    //   // render / serialise `record` to stdout or an output file
}
