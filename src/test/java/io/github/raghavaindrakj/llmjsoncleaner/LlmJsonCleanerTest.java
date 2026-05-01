package io.github.raghavaindrakj.llmjsoncleaner;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmJsonCleanerTest {

    private final LlmJsonCleaner cleaner = new LlmJsonCleaner();

    @Test
    void parsesValidJsonDirectly() {
        JsonNode node = cleaner.cleanToJson("{\"name\":\"Raghava\",\"active\":true}");

        assertEquals("Raghava", node.get("name").asText());
        assertTrue(node.get("active").asBoolean());
    }

    @Test
    void extractsJsonFromMarkdownFence() {
        String response = "Here is the JSON:\n```json\n{\"ok\": true}\n```";

        JsonNode node = cleaner.cleanToJson(response);

        assertTrue(node.get("ok").asBoolean());
    }

    @Test
    void extractsBalancedJsonFromSurroundingText() {
        String response = "Result: {\"items\":[{\"id\":1},{\"id\":2}]} Thanks.";

        JsonNode node = cleaner.cleanToJson(response);

        assertEquals(2, node.get("items").size());
    }

    @Test
    void normalizesSingleQuotedJsonTokens() {
        JsonNode node = cleaner.cleanToJson("{'name': 'Raghava', 'note': 'it\\'s ready'}");

        assertEquals("Raghava", node.get("name").asText());
        assertEquals("it's ready", node.get("note").asText());
    }

    @Test
    void preservesApostrophesInsideSingleQuotedValues() {
        JsonNode node = cleaner.cleanToJson("{'message': 'patient's report is ready'}");

        assertEquals("patient's report is ready", node.get("message").asText());
    }

    @Test
    void escapesRawControlCharactersInsideStrings() {
        JsonNode node = cleaner.cleanToJson("{\"line\":\"first\nsecond\"}");

        assertEquals("first\nsecond", node.get("line").asText());
    }

    @Test
    void removesJsonCommentsWithoutChangingUrls() {
        String response = "{\n"
                + "  // model note\n"
                + "  \"url\": \"https://example.com/a//b\",\n"
                + "  \"enabled\": true /* trailing note */\n"
                + "}";

        JsonNode node = cleaner.cleanToJson(response);

        assertEquals("https://example.com/a//b", node.get("url").asText());
        assertTrue(node.get("enabled").asBoolean());
    }

    @Test
    void normalizesNonJsonLiteralsOutsideStrings() {
        JsonNode node = cleaner.cleanToJson("{\"a\": True, \"b\": False, \"c\": None, \"d\": NaN, \"text\": \"True\"}");

        assertTrue(node.get("a").asBoolean());
        assertFalse(node.get("b").asBoolean());
        assertTrue(node.get("c").isNull());
        assertTrue(node.get("d").isNull());
        assertEquals("True", node.get("text").asText());
    }

    @Test
    void removesTrailingCommas() {
        JsonNode node = cleaner.cleanToJson("{\"items\":[1,2,],}");

        assertEquals(2, node.get("items").size());
    }

    @Test
    void repairsMissingClosingBracketsOnlyWhenValuesAreComplete() {
        JsonNode node = cleaner.cleanToJson("{\"items\":[{\"id\":1}");

        assertEquals(1, node.get("items").get(0).get("id").asInt());
    }

    @Test
    void decodesHtmlEntitiesAfterOtherStrategiesFail() {
        JsonNode node = cleaner.cleanToJson("{&quot;name&quot;:&quot;Raghava&quot;}");

        assertEquals("Raghava", node.get("name").asText());
    }

    @Test
    void tryCleanReturnsEmptyForInvalidContent() {
        Optional<JsonNode> node = cleaner.tryCleanToJson("No JSON here");

        assertFalse(node.isPresent());
    }

    @Test
    void cleanRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> cleaner.cleanToJson("   "));
    }

    @Test
    void cleanThrowsCleanerExceptionForInvalidNonBlankInput() {
        assertThrows(LlmJsonCleanerException.class, () -> cleaner.cleanToJson("No JSON here"));
    }

    @Test
    void staticConvenienceApiReturnsCompactJsonString() {
        assertEquals("{\"ok\":true}", LlmJsonCleaner.cleanToString("```json\n{\"ok\": true}\n```"));
    }
}
