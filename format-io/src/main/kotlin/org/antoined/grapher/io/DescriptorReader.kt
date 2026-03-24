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

fun descriptorFromJson(json: String): Descriptor = jsonMapper.readValue(json)
fun descriptorFromJson(file: File): Descriptor = jsonMapper.readValue(file)
fun descriptorFromJson(stream: InputStream): Descriptor = jsonMapper.readValue(stream)

fun descriptorFromYaml(yaml: String): Descriptor = yamlMapper.readValue(yaml)
fun descriptorFromYaml(file: File): Descriptor = yamlMapper.readValue(file)
fun descriptorFromYaml(stream: InputStream): Descriptor = yamlMapper.readValue(stream)
