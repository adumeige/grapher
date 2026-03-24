package org.antoined.grapher

enum class Multiplicity(val min: Int, val max: Int) {
    ZERO_ONE(0, 1),
    ONE(1, 1),
    ZERO_MANY(0, 42),
    ONE_MANY(1, 42)
}

enum class Type(val jsonType: String) {
    STRING("string"),
    INT("integer"),
    NUMBER("number"),
    DATE("date"),
}

data class Property(
    val name: String,
    val type: Type = Type.STRING,
    val multiplicity: Multiplicity = Multiplicity.ONE,
    val pattern:String? = null,
    val hints: List<String> = emptyList())

data class Part(
    val name: String,
    val multiplicity: Multiplicity = Multiplicity.ONE,
    val hints: List<String> = emptyList(),
    val parts: List<Part> = emptyList(),
    val properties: List<Property> = emptyList())


data class Descriptor(
    val namespace: String,
    val name: String,
    val description: String,
    val version: String,
    val parts: List<Part> = emptyList(),
    val properties: List<Property> = emptyList(), )