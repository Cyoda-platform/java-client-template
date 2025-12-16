package com.java_template.common.util.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ABOUTME: Parses plain text response bodies, returning the content as a text field.
 */
public class PlainTextResponseParser implements ResponseBodyParser {
    private final ObjectMapper objectMapper;

    public PlainTextResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ObjectNode parse(String body, String contentType, int statusCode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", statusCode);
        result.put("text", body);
        return result;
    }
}

