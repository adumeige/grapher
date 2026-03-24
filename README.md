# grapher

A Kotlin library for defining document extraction formats and generating JSON Schema from them, intended to provide LLMs with both a target structure and extraction hints.

## Modules

| Module | Description |
|---|---|
| `format` | Core model: `Descriptor`, `Part`, `Property`, `Type`, `Multiplicity` |
| `format-dsl` | Kotlin DSL for defining descriptors |
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

## DSL

The `format-dsl` module provides a concise alternative to constructing the model directly.

```kotlin
val invoice = descriptor("finance", "Invoice") {
    description = "An invoice document"
    version     = "1.0"

    property("invoiceNumber") {
        pattern = "[A-Z]{2}-\\d+"
        hint("Invoice reference number")
    }
    property("date",  Type.DATE)
    property("notes", Type.STRING, Multiplicity.ZERO_ONE)

    part("lineItems") {
        multiplicity = Multiplicity.ONE_MANY
        hint("One entry per product or service")

        property("description") { hint("Product or service name") }
        property("amount", Type.NUMBER)
    }
}
```

`hint(text)` can be called multiple times to accumulate hints. The shorthand `property(name, type)` / `property(name, type, multiplicity)` skips the block for simple fields. Parts can be nested to any depth.

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
