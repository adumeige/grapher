# grapher

A Kotlin library for defining document extraction formats and generating JSON Schema from them, intended to provide LLMs with both a target structure and extraction hints.

## Modules

| Module | Description |
|---|---|
| `format` | Core model: `Descriptor`, `Part`, `Property`, `Type`, `Multiplicity` |
| `format-io` | Load descriptors from JSON/YAML; export to JSON Schema |

## Model

A `Descriptor` is the root of a format definition. It contains `Part`s (composite sections, nestable) and `Property`s (scalar fields). Both accept a `Multiplicity` and a list of `hints`.

```kotlin
val invoice = Descriptor(
    namespace = "finance", name = "Invoice",
    description = "An invoice document", version = "1.0",
    properties = listOf(
        Property("invoiceNumber", Type.STRING, Multiplicity.ONE, pattern = "[A-Z]{2}-\\d+"),
        Property("date", Type.DATE, Multiplicity.ONE)
    ),
    parts = listOf(
        Part("lineItems", Multiplicity.ONE_MANY,
            hints = listOf("Extract one entry per product or service"),
            properties = listOf(
                Property("description", hints = listOf("Product or service name")),
                Property("amount", Type.NUMBER)
            )
        )
    )
)
```

## JSON Schema export

`hints` become the JSON Schema `description` field; `Multiplicity` controls `required` and array wrapping.

```kotlin
val schema: String = invoice.toJsonSchemaString()
```

## Loading a descriptor

Descriptors can be stored as JSON or YAML and loaded at runtime:

```kotlin
val d = descriptorFromJson(File("invoice.json"))
val d = descriptorFromYaml(File("invoice.yaml"))
```

## Build

```
mvn install
```
