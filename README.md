# llm-json-cleaner

A lightweight Java library for cleaning, repairing, and extracting valid JSON from LLM responses.

## Installation

```xml
<dependency>
    <groupId>io.github.raghavaindrakj</groupId>
    <artifactId>llm-json-cleaner</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Requirements

- Java 8 or newer
- Jackson Databind 2.17.x

The library is compiled for Java 8 bytecode and is intended to run on Java 8 through the latest Java releases.

## Usage

```java
import com.fasterxml.jackson.databind.JsonNode;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleaner;

public class Example {
    public static void main(String[] args) {
        String llmResponse = "```json\n{\"name\":\"Raghava\", \"active\": true,}\n```";

        JsonNode json = LlmJsonCleaner.clean(llmResponse);

        System.out.println(json.get("name").asText());
    }
}
```

For applications that need a reusable instance or a custom Jackson mapper:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleaner;

LlmJsonCleaner cleaner = new LlmJsonCleaner(new ObjectMapper());
String normalizedJson = cleaner.cleanToJsonString("Here is JSON: {'ok': True}");
```

## What It Handles

- Markdown JSON code fences
- JSON embedded inside surrounding text
- Python-style single-quoted keys and values
- Raw control characters inside strings
- JSONC-style line and block comments
- Python and JavaScript literals such as `True`, `False`, `None`, `NaN`, and `Infinity`
- Trailing commas in objects and arrays
- Missing closing brackets when values are otherwise complete
- Common HTML entities such as `&quot;`
- BOM, non-breaking spaces, and zero-width characters

## Public API

- `LlmJsonCleaner.clean(String)` - static convenience method returning `JsonNode`
- `LlmJsonCleaner.tryClean(String)` - static convenience method returning `Optional<JsonNode>`
- `LlmJsonCleaner.cleanToString(String)` - static convenience method returning compact JSON
- `new LlmJsonCleaner().cleanToJson(String)` - instance API returning `JsonNode`
- `new LlmJsonCleaner().tryCleanToJson(String)` - instance API returning `Optional<JsonNode>`
- `new LlmJsonCleaner().cleanToJsonString(String)` - instance API returning compact JSON

Blank input throws `IllegalArgumentException`. Non-empty input that cannot be cleaned into JSON throws `LlmJsonCleanerException`.

## Build

```bash
mvn verify
```

## Maven Coordinates

```xml
<groupId>io.github.raghavaindrakj</groupId>
<artifactId>llm-json-cleaner</artifactId>
<version>1.0.0</version>
```

## License

MIT
