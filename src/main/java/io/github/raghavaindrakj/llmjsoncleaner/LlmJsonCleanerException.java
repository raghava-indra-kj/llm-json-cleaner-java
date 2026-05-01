package io.github.raghavaindrakj.llmjsoncleaner;

/**
 * Runtime exception thrown when an LLM response cannot be converted into valid JSON.
 */
public class LlmJsonCleanerException extends RuntimeException {

    /**
     * Creates an exception with a failure message.
     *
     * @param message failure message
     */
    public LlmJsonCleanerException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a failure message and root cause.
     *
     * @param message failure message
     * @param cause   root cause
     */
    public LlmJsonCleanerException(String message, Throwable cause) {
        super(message, cause);
    }
}
