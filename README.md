# llm-json-cleaner

A lightweight Java library for cleaning, repairing, and extracting valid JSON from LLM responses.

Repository: [raghava-indra-kj/llm-json-cleaner-java](https://github.com/raghava-indra-kj/llm-json-cleaner-java)

## Requirements

- Java 8 or newer
- Maven 3.x

The library is compiled for Java 8 bytecode and can be used from Java 8 through current Java versions.

## Install Locally

Until the artifact is published to Maven Central or another Maven repository, install it into your local Maven repository:

```bash
mvn clean install
```

Then add it to any Maven project on the same machine:

```xml
<dependency>
    <groupId>io.github.raghavaindrakj</groupId>
    <artifactId>llm-json-cleaner</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Usage

```java
import com.fasterxml.jackson.databind.JsonNode;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleaner;

public class Example {
    public static void main(String[] args) {
        String llmResponse = "```json\n{\"name\":\"Raghava\", \"active\": true,}\n```";

        LlmJsonCleaner cleaner = new LlmJsonCleaner();
        JsonNode json = cleaner.cleanToJson(llmResponse);

        System.out.println(json.get("name").asText());
        System.out.println(json.get("active").asBoolean());
    }
}
```

Output:

```text
Raghava
true
```

## Reusable Instance

Create an instance once and reuse it wherever you clean LLM responses. You can also provide your own Jackson `ObjectMapper`.

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleaner;

public class Example {
    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        LlmJsonCleaner cleaner = new LlmJsonCleaner(objectMapper);

        JsonNode json = cleaner.cleanToJson("Result: {\"items\":[1,2,],}");

        System.out.println(json.get("items").size());
        System.out.println(json.toString());
    }
}
```

## Public API

- `new LlmJsonCleaner()` creates a cleaner with a default Jackson mapper
- `new LlmJsonCleaner(ObjectMapper objectMapper)` creates a cleaner with a caller-provided Jackson mapper
- `cleanToJson(String rawResponse)` cleans an LLM response and returns a Jackson `JsonNode`

`cleanToJson` is the single public cleaning method.

## Error Handling

Blank input is treated as caller misuse and throws `IllegalArgumentException`.

Non-empty input that cannot be cleaned into JSON throws `LlmJsonCleanerException`.

```java
import com.fasterxml.jackson.databind.JsonNode;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleaner;
import io.github.raghavaindrakj.llmjsoncleaner.LlmJsonCleanerException;

public class Example {
    public static void main(String[] args) {
        try {
            LlmJsonCleaner cleaner = new LlmJsonCleaner();
            JsonNode json = cleaner.cleanToJson("No JSON here");
            System.out.println(json);
        } catch (IllegalArgumentException exception) {
            System.out.println("Response was blank");
        } catch (LlmJsonCleanerException exception) {
            System.out.println("Response did not contain valid JSON");
        }
    }
}
```

## What It Handles

- Markdown JSON code fences
- JSON embedded inside surrounding text
- Python-style single-quoted keys and values
- Raw control characters inside JSON strings
- JSONC-style line and block comments
- Python and JavaScript literals such as `True`, `False`, `None`, `NaN`, and `Infinity`
- Trailing commas in objects and arrays
- Missing closing brackets when values are otherwise complete
- Common HTML entities such as `&quot;`
- BOM, non-breaking spaces, and zero-width characters

## Build And Test

```bash
mvn verify
```

This runs unit tests, builds the jar, builds source and javadoc jars, and checks Java 8 API compatibility.

## Maven Coordinates

```xml
<groupId>io.github.raghavaindrakj</groupId>
<artifactId>llm-json-cleaner</artifactId>
<version>1.0.0</version>
```

## License

MIT
