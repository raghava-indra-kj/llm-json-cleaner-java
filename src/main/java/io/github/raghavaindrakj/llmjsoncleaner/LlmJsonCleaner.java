package io.github.raghavaindrakj.llmjsoncleaner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans AI model text responses and extracts a valid JSON payload.
 */
public final class LlmJsonCleaner {

    private static final Pattern MARKDOWN_JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[][] NON_JSON_LITERAL_REPLACEMENTS = {
            {"-Infinity", "null"},
            {"Infinity", "null"},
            {"False", "false"},
            {"True", "true"},
            {"None", "null"},
            {"NaN", "null"}
    };

    private static final LlmJsonCleaner DEFAULT = new LlmJsonCleaner();

    private final ObjectMapper objectMapper;

    /**
     * Creates a cleaner with a default Jackson {@link ObjectMapper}.
     */
    public LlmJsonCleaner() {
        this(new ObjectMapper());
    }

    /**
     * Creates a cleaner with a caller-provided Jackson {@link ObjectMapper}.
     *
     * @param objectMapper mapper used to parse and serialize JSON
     */
    public LlmJsonCleaner(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Cleans and parses a raw LLM response using a default cleaner instance.
     *
     * @param rawResponse raw text returned by an LLM
     * @return parsed JSON payload extracted from the response
     */
    public static JsonNode clean(String rawResponse) {
        return DEFAULT.cleanToJson(rawResponse);
    }

    /**
     * Attempts to clean and parse a raw LLM response using a default cleaner instance.
     *
     * @param rawResponse raw text returned by an LLM
     * @return parsed JSON payload when cleaning succeeds
     */
    public static Optional<JsonNode> tryClean(String rawResponse) {
        return DEFAULT.tryCleanToJson(rawResponse);
    }

    /**
     * Cleans, parses, and serializes a raw LLM response as normalized compact JSON.
     *
     * @param rawResponse raw text returned by an LLM
     * @return compact JSON string
     */
    public static String cleanToString(String rawResponse) {
        return DEFAULT.cleanToJsonString(rawResponse);
    }

    /**
     * Cleans and parses a raw LLM response into JSON.
     *
     * @param rawResponse raw text returned by an LLM
     * @return parsed JSON payload extracted from the response
     * @throws IllegalArgumentException when the input is null, empty, or whitespace-only
     * @throws LlmJsonCleanerException when the non-empty response cannot be cleaned into JSON
     */
    public JsonNode cleanToJson(String rawResponse) {
        String cleanedResponse = cleanTextArtifacts(rawResponse);
        if (isBlank(cleanedResponse)) {
            throw new IllegalArgumentException("LLM response cannot be empty");
        }
        Optional<JsonNode> cleanedJson = trySanitizeToJson(cleanedResponse);
        if (cleanedJson.isPresent()) {
            return cleanedJson.get();
        }
        throw new LlmJsonCleanerException("LLM response could not be cleaned into JSON");
    }

    /**
     * Attempts to clean and parse a raw LLM response into JSON.
     *
     * @param rawResponse raw text returned by an LLM
     * @return parsed JSON payload when cleaning succeeds
     */
    public Optional<JsonNode> tryCleanToJson(String rawResponse) {
        String cleanedResponse = cleanTextArtifacts(rawResponse);
        if (isBlank(cleanedResponse)) {
            return Optional.empty();
        }
        return trySanitizeToJson(cleanedResponse);
    }

    /**
     * Cleans, parses, and serializes a raw LLM response as normalized compact JSON.
     *
     * @param rawResponse raw text returned by an LLM
     * @return compact JSON string
     */
    public String cleanToJsonString(String rawResponse) {
        try {
            return objectMapper.writeValueAsString(cleanToJson(rawResponse));
        } catch (JsonProcessingException exception) {
            throw new LlmJsonCleanerException("Cleaned JSON could not be serialized", exception);
        }
    }

    private Optional<JsonNode> trySanitizeToJson(String cleanedResponse) {
        Optional<JsonNode> directJson = tryParseWithCommonRepairs(cleanedResponse);
        if (directJson.isPresent()) {
            return directJson;
        }

        Optional<JsonNode> markdownJson = extractFromMarkdownJsonBlock(cleanedResponse);
        if (markdownJson.isPresent()) {
            return markdownJson;
        }

        Optional<JsonNode> balancedJson = extractBalancedJsonPayload(cleanedResponse);
        if (balancedJson.isPresent()) {
            return balancedJson;
        }

        String htmlUnescapedResponse = unescapeHtmlEntities(cleanedResponse);
        if (!htmlUnescapedResponse.equals(cleanedResponse)) {
            Optional<JsonNode> htmlJson = tryParseWithCommonRepairs(htmlUnescapedResponse);
            if (htmlJson.isPresent()) {
                return htmlJson;
            }

            Optional<JsonNode> htmlMarkdownJson = extractFromMarkdownJsonBlock(htmlUnescapedResponse);
            if (htmlMarkdownJson.isPresent()) {
                return htmlMarkdownJson;
            }

            return extractBalancedJsonPayload(htmlUnescapedResponse);
        }

        return Optional.empty();
    }

    private String cleanTextArtifacts(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }

        return rawResponse
                .replace("\uFEFF", "")
                .replace("\uFFFE", "")
                .replace("\u00A0", " ")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .trim();
    }

    private String unescapeHtmlEntities(String content) {
        if (isBlank(content) || content.indexOf('&') < 0) {
            return content;
        }

        StringBuilder unescaped = new StringBuilder(content.length());
        int index = 0;
        while (index < content.length()) {
            char current = content.charAt(index);
            if (current != '&') {
                unescaped.append(current);
                index++;
                continue;
            }

            int semicolon = content.indexOf(';', index + 1);
            if (semicolon < 0 || semicolon - index > 32) {
                unescaped.append(current);
                index++;
                continue;
            }

            String entity = content.substring(index + 1, semicolon);
            String decoded = decodeHtmlEntity(entity);
            if (decoded == null) {
                unescaped.append(current);
                index++;
            } else {
                unescaped.append(decoded);
                index = semicolon + 1;
            }
        }

        return unescaped.toString();
    }

    private String decodeHtmlEntity(String entity) {
        if ("quot".equals(entity)) {
            return "\"";
        }
        if ("apos".equals(entity) || "#39".equals(entity) || "#x27".equalsIgnoreCase(entity)) {
            return "'";
        }
        if ("amp".equals(entity)) {
            return "&";
        }
        if ("lt".equals(entity)) {
            return "<";
        }
        if ("gt".equals(entity)) {
            return ">";
        }
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return decodeNumericHtmlEntity(entity.substring(2), 16);
        }
        if (entity.startsWith("#")) {
            return decodeNumericHtmlEntity(entity.substring(1), 10);
        }
        return null;
    }

    private String decodeNumericHtmlEntity(String value, int radix) {
        try {
            int codePoint = Integer.parseInt(value, radix);
            if (!Character.isValidCodePoint(codePoint)) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Optional<JsonNode> tryParseWithCommonRepairs(String content) {
        Optional<JsonNode> parsed = tryParse(content);
        if (parsed.isPresent()) {
            return parsed;
        }

        String singleQuotedStringsNormalized = normalizeSingleQuotedStrings(content);
        parsed = tryParse(singleQuotedStringsNormalized);
        if (parsed.isPresent()) {
            return parsed;
        }

        String controlCharactersEscaped = escapeControlCharacters(singleQuotedStringsNormalized);
        parsed = tryParse(controlCharactersEscaped);
        if (parsed.isPresent()) {
            return parsed;
        }

        String commentsRemoved = stripJsonComments(controlCharactersEscaped);
        parsed = tryParse(commentsRemoved);
        if (parsed.isPresent()) {
            return parsed;
        }

        String nonJsonLiteralsNormalized = normalizeNonJsonLiterals(commentsRemoved);
        parsed = tryParse(nonJsonLiteralsNormalized);
        if (parsed.isPresent()) {
            return parsed;
        }

        String trailingCommasRemoved = removeTrailingCommas(nonJsonLiteralsNormalized);
        parsed = tryParse(trailingCommasRemoved);
        if (parsed.isPresent()) {
            return parsed;
        }

        return repairMissingClosingBrackets(trailingCommasRemoved).flatMap(this::tryParse);
    }

    private Optional<JsonNode> tryParse(String content) {
        if (isBlank(content)) {
            return Optional.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(content);
            return node == null || node.isMissingNode() ? Optional.<JsonNode>empty() : Optional.of(node);
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeSingleQuotedStrings(String content) {
        if (isBlank(content)) {
            return content;
        }

        StringBuilder normalized = new StringBuilder(content.length());
        boolean inDoubleQuotedString = false;
        boolean inSingleQuotedString = false;
        boolean escaped = false;
        boolean changed = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inDoubleQuotedString) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inDoubleQuotedString = false;
                }
                continue;
            }

            if (inSingleQuotedString) {
                if (escaped) {
                    appendEscapedSingleQuotedCharacter(normalized, current);
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'' && isSingleQuoteTerminator(content, index)) {
                    normalized.append('"');
                    inSingleQuotedString = false;
                } else {
                    appendSingleQuotedCharacter(normalized, current);
                }
                continue;
            }

            if (current == '"') {
                normalized.append(current);
                inDoubleQuotedString = true;
            } else if (current == '\'' && isSingleQuoteStart(content, index)) {
                normalized.append('"');
                inSingleQuotedString = true;
                changed = true;
            } else {
                normalized.append(current);
            }
        }

        return changed && !inSingleQuotedString ? normalized.toString() : content;
    }

    private void appendSingleQuotedCharacter(StringBuilder normalized, char current) {
        if (current == '"') {
            normalized.append("\\\"");
        } else {
            normalized.append(current);
        }
    }

    private void appendEscapedSingleQuotedCharacter(StringBuilder normalized, char current) {
        if (current == '\'') {
            normalized.append('\'');
        } else if (current == '"') {
            normalized.append("\\\"");
        } else if (isJsonEscapeCharacter(current)) {
            normalized.append('\\').append(current);
        } else {
            normalized.append("\\\\").append(current);
        }
    }

    private boolean isJsonEscapeCharacter(char current) {
        return current == '"' || current == '\\' || current == '/' || current == 'b'
                || current == 'f' || current == 'n' || current == 'r' || current == 't'
                || current == 'u';
    }

    private boolean isSingleQuoteStart(String content, int index) {
        char previous = previousSignificantCharacter(content, index);
        return previous == '\0' || previous == '{' || previous == '[' || previous == ',' || previous == ':';
    }

    private boolean isSingleQuoteTerminator(String content, int index) {
        char next = nextSignificantCharacter(content, index);
        return next == '\0' || next == '}' || next == ']' || next == ',' || next == ':';
    }

    private String escapeControlCharacters(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder escapedContent = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                if (escaped) {
                    escapedContent.append(current);
                    escaped = false;
                } else if (current == '\\') {
                    escapedContent.append(current);
                    escaped = true;
                } else if (current == '"') {
                    escapedContent.append(current);
                    inString = false;
                } else if (current < 0x20) {
                    appendEscapedControlCharacter(escapedContent, current);
                } else {
                    escapedContent.append(current);
                }
                continue;
            }

            if (current == '"') {
                escapedContent.append(current);
                inString = true;
            } else if (current < 0x20 && current != '\n' && current != '\r' && current != '\t') {
                continue;
            } else {
                escapedContent.append(current);
            }
        }

        return escapedContent.toString();
    }

    private void appendEscapedControlCharacter(StringBuilder escapedContent, char current) {
        if (current == '\t') {
            escapedContent.append("\\t");
        } else if (current == '\n') {
            escapedContent.append("\\n");
        } else if (current == '\r') {
            escapedContent.append("\\r");
        } else if (current == '\b') {
            escapedContent.append("\\b");
        } else if (current == '\f') {
            escapedContent.append("\\f");
        } else {
            String hex = Integer.toHexString(current);
            escapedContent.append("\\u");
            for (int index = hex.length(); index < 4; index++) {
                escapedContent.append('0');
            }
            escapedContent.append(hex);
        }
    }

    private String stripJsonComments(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder stripped = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                stripped.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                stripped.append(current);
                inString = true;
            } else if (current == '/' && hasNext(content, index, '/')) {
                index = skipLineComment(content, index);
            } else if (current == '/' && hasNext(content, index, '*')) {
                index = skipBlockComment(content, index);
            } else {
                stripped.append(current);
            }
        }

        return stripped.toString();
    }

    private int skipLineComment(String content, int index) {
        int cursor = index + 2;
        while (cursor < content.length() && content.charAt(cursor) != '\n' && content.charAt(cursor) != '\r') {
            cursor++;
        }
        return cursor - 1;
    }

    private int skipBlockComment(String content, int index) {
        int cursor = index + 2;
        while (cursor + 1 < content.length()) {
            if (content.charAt(cursor) == '*' && content.charAt(cursor + 1) == '/') {
                return cursor + 1;
            }
            cursor++;
        }
        return content.length() - 1;
    }

    private String normalizeNonJsonLiterals(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder normalized = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                normalized.append(current);
                inString = true;
                continue;
            }

            String[] replacement = findNonJsonLiteralReplacement(content, index);
            if (replacement != null) {
                normalized.append(replacement[1]);
                index += replacement[0].length() - 1;
            } else {
                normalized.append(current);
            }
        }

        return normalized.toString();
    }

    private String[] findNonJsonLiteralReplacement(String content, int index) {
        for (String[] replacement : NON_JSON_LITERAL_REPLACEMENTS) {
            if (matchesToken(content, index, replacement[0])) {
                return replacement;
            }
        }
        return null;
    }

    private boolean matchesToken(String content, int index, String token) {
        if (index + token.length() > content.length() || !content.startsWith(token, index)) {
            return false;
        }

        return hasTokenBoundaryBefore(content, index) && hasTokenBoundaryAfter(content, index + token.length());
    }

    private boolean hasTokenBoundaryBefore(String content, int index) {
        return index == 0 || !isIdentifierCharacter(content.charAt(index - 1));
    }

    private boolean hasTokenBoundaryAfter(String content, int index) {
        return index >= content.length() || !isIdentifierCharacter(content.charAt(index));
    }

    private boolean isIdentifierCharacter(char current) {
        return Character.isLetterOrDigit(current) || current == '_' || current == '$';
    }

    private Optional<JsonNode> extractFromMarkdownJsonBlock(String content) {
        if (isBlank(content)) {
            return Optional.empty();
        }

        Matcher matcher = MARKDOWN_JSON_BLOCK.matcher(content);
        while (matcher.find()) {
            String fencedContent = cleanTextArtifacts(matcher.group(1));
            Optional<JsonNode> parsed = tryParseWithCommonRepairs(fencedContent);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> extractBalancedJsonPayload(String content) {
        if (isBlank(content)) {
            return Optional.empty();
        }

        int searchFrom = 0;
        while (searchFrom < content.length()) {
            int start = findNextJsonStart(content, searchFrom);
            if (start < 0) {
                return Optional.empty();
            }

            int end = findBalancedJsonEnd(content, start);
            String candidate = end > start ? content.substring(start, end) : content.substring(start);
            Optional<JsonNode> parsed = tryParseWithCommonRepairs(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }

            searchFrom = start + 1;
        }

        return Optional.empty();
    }

    private int findNextJsonStart(String content, int fromIndex) {
        int objectStart = content.indexOf('{', fromIndex);
        int arrayStart = content.indexOf('[', fromIndex);

        if (objectStart < 0) {
            return arrayStart;
        }
        if (arrayStart < 0) {
            return objectStart;
        }
        return Math.min(objectStart, arrayStart);
    }

    private int findBalancedJsonEnd(String content, int start) {
        Deque<Character> bracketStack = new ArrayDeque<Character>();
        char stringDelimiter = '\0';
        boolean escaped = false;

        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);

            if (stringDelimiter != '\0') {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == stringDelimiter
                        && (stringDelimiter == '"' || isSingleQuoteTerminator(content, index))) {
                    stringDelimiter = '\0';
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                stringDelimiter = current;
                continue;
            }

            if (current == '{' || current == '[') {
                bracketStack.push(current);
            } else if (current == '}' || current == ']') {
                if (bracketStack.isEmpty() || !matchesOpeningBracket(bracketStack.pop(), current)) {
                    return -1;
                }

                if (bracketStack.isEmpty()) {
                    return index + 1;
                }
            }
        }

        return -1;
    }

    private boolean matchesOpeningBracket(char opening, char closing) {
        return (opening == '{' && closing == '}') || (opening == '[' && closing == ']');
    }

    private String removeTrailingCommas(String content) {
        if (content == null) {
            return null;
        }

        StringBuilder normalized = new StringBuilder(content.length());
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                normalized.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                normalized.append(current);
                inString = true;
            } else if (current == ',' && isTrailingComma(content, index)) {
                continue;
            } else {
                normalized.append(current);
            }
        }

        return normalized.toString();
    }

    private boolean isTrailingComma(String content, int index) {
        char next = nextSignificantCharacter(content, index);
        return next == '}' || next == ']';
    }

    private Optional<String> repairMissingClosingBrackets(String content) {
        if (isBlank(content) || endsWithIncompleteJsonToken(content)) {
            return Optional.empty();
        }

        Deque<Character> bracketStack = new ArrayDeque<Character>();
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                bracketStack.push(current);
            } else if (current == '}' || current == ']') {
                if (bracketStack.isEmpty() || !matchesOpeningBracket(bracketStack.pop(), current)) {
                    return Optional.empty();
                }
            }
        }

        if (inString || escaped || bracketStack.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder repaired = new StringBuilder(content);
        while (!bracketStack.isEmpty()) {
            repaired.append(closingBracketFor(bracketStack.pop()));
        }
        return Optional.of(repaired.toString());
    }

    private boolean endsWithIncompleteJsonToken(String content) {
        char last = lastSignificantCharacter(content);
        return last == '\0' || last == ',' || last == ':' || last == '{' || last == '[';
    }

    private char closingBracketFor(char opening) {
        return opening == '{' ? '}' : ']';
    }

    private boolean hasNext(String content, int currentIndex, char expectedNext) {
        return currentIndex + 1 < content.length() && content.charAt(currentIndex + 1) == expectedNext;
    }

    private char previousSignificantCharacter(String content, int index) {
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            char current = content.charAt(cursor);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private char nextSignificantCharacter(String content, int index) {
        for (int cursor = index + 1; cursor < content.length(); cursor++) {
            char current = content.charAt(cursor);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private char lastSignificantCharacter(String content) {
        for (int cursor = content.length() - 1; cursor >= 0; cursor--) {
            char current = content.charAt(cursor);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
