package com.java_template.common.util.http;

/**
 * ABOUTME: Defines how JSON response bodies should be parsed, controlling tolerance
 * for edge cases like double-encoded JSON strings.
 */
public enum JsonParsingMode {
    /**
     * Strict mode: Only accept properly formatted JSON objects.
     * Fails fast on double-encoded JSON or non-object responses.
     */
    STRICT,

    /**
     * Lenient mode: Attempts to handle double-encoded JSON strings.
     * If the parsed JSON is a text string, attempts to parse it again.
     * Falls back gracefully on parsing failures.
     */
    LENIENT
}

