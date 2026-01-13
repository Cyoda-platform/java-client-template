package com.java_template.common.util.http;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ABOUTME: Strategy interface for parsing HTTP response bodies into structured ObjectNode results.
 * Implementations handle different content types and parsing strategies.
 */
public interface ResponseBodyParser {
    /**
     * Parse the HTTP response body into an ObjectNode containing the parsed content and metadata.
     *
     * @param body the raw response body as a string
     * @param contentType the Content-Type header value from the response (may be null)
     * @param statusCode the HTTP status code
     * @return ObjectNode with "status" field and content field(s)
     * @throws ResponseParsingException if parsing fails and the parser is configured to fail fast
     */
    ObjectNode parse(String body, String contentType, int statusCode) throws ResponseParsingException;
}

