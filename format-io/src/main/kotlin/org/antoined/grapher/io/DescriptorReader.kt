package org.antoined.grapher.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.antoined.grapher.Descriptor
import java.io.File
import java.io.InputStream

private val jsonMapper = ObjectMapper()
    .registerModule(kotlinModule())

private val yamlMapper = ObjectMapper(YAMLFactory())
    .registerModule(kotlinModule())

/**
 * Deserialises a [Descriptor] from a JSON string, [File], or [InputStream].
 *
 * Enum fields (`type`, `multiplicity`) are matched by name (e.g. `"ONE_MANY"`).
 * Optional fields (`hints`, `parts`, `properties`, `pattern`) may be omitted and
 * will fall back to their defaults.
 */
fun descriptorFromJson(json: String): Descriptor = jsonMapper.readValue(json)
fun descriptorFromJson(file: File): Descriptor = jsonMapper.readValue(file)
fun descriptorFromJson(stream: InputStream): Descriptor = jsonMapper.readValue(stream)

/**
 * Deserialises a [Descriptor] from a YAML string, [File], or [InputStream].
 *
 * Same field rules as [descriptorFromJson].
 */
fun descriptorFromYaml(yaml: String): Descriptor = yamlMapper.readValue(yaml)
fun descriptorFromYaml(file: File): Descriptor = yamlMapper.readValue(file)
fun descriptorFromYaml(stream: InputStream): Descriptor = yamlMapper.readValue(stream)
