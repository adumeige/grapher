package org.antoined.grapher.cli

import java.io.File

/**
 * LLM connection and generation parameters.
 *
 * Resolved in order: CLI flags → environment variables → .env file → defaults.
 */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val systemPrompt: String,
) {
    companion object {
        private const val DEFAULT_BASE_URL      = "http://localhost:8080/v1"
        private const val DEFAULT_API_KEY       = "no-key"
        private const val DEFAULT_MODEL         = "default"
        private const val DEFAULT_TEMPERATURE   = 0.2
        private const val DEFAULT_MAX_TOKENS    = 4096
        private const val DEFAULT_SYSTEM_PROMPT = "You are a structured data extraction assistant. " +
                "You receive document text and a description of what to extract. " +
                "Always respond with ONLY the requested data, no commentary."

        /**
         * Builds an [LlmConfig] by layering CLI args over env vars over .env file over defaults.
         *
         * [cliArgs] keys match the long flag names without the `--` prefix
         * (e.g. `"llm-base-url"` → value).
         */
        fun resolve(cliArgs: Map<String, String>): LlmConfig {
            val dotenv = loadDotenv()

            fun get(cliKey: String, envKey: String, default: String): String =
                cliArgs[cliKey]
                    ?: System.getenv(envKey)
                    ?: dotenv[envKey]
                    ?: default

            return LlmConfig(
                baseUrl      = get("llm-base-url",      "GRAPHER_LLM_BASE_URL",      DEFAULT_BASE_URL),
                apiKey       = get("llm-api-key",        "GRAPHER_LLM_API_KEY",        DEFAULT_API_KEY),
                model        = get("llm-model",          "GRAPHER_LLM_MODEL",          DEFAULT_MODEL),
                temperature  = get("llm-temperature",    "GRAPHER_LLM_TEMPERATURE",    DEFAULT_TEMPERATURE.toString()).toDouble(),
                maxTokens    = get("llm-max-tokens",     "GRAPHER_LLM_MAX_TOKENS",     DEFAULT_MAX_TOKENS.toString()).toInt(),
                systemPrompt = get("llm-system-prompt",  "GRAPHER_LLM_SYSTEM_PROMPT",  DEFAULT_SYSTEM_PROMPT),
            )
        }

        private fun loadDotenv(): Map<String, String> {
            val file = File(".env")
            if (!file.exists()) return emptyMap()

            return file.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"")
                    key to value
                }
                .toMap()
        }
    }
}
