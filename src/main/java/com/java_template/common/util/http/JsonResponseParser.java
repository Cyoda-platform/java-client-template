package com.java_template.common.util.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ABOUTME: Parses JSON response bodies with configurable strictness for handling
 * edge cases like double-encoded JSON strings.
 */
public class JsonResponseParser implements ResponseBodyParser {
    private static final Logger logger = LoggerFactory.getLogger(JsonResponseParser.class);
    private final ObjectMapper objectMapper;
    private final JsonParsingMode mode;

    public JsonResponseParser(ObjectMapper objectMapper, JsonParsingMode mode) {
        this.objectMapper = objectMapper;
        this.mode = mode;
    }

    @Override
    public ObjectNode parse(String body, String contentType, int statusCode) throws ResponseParsingException {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", statusCode);

        try {
            JsonNode responseJson = objectMapper.readTree(body);

            if (mode == JsonParsingMode.STRICT) {
                parseStrict(responseJson, result, statusCode, contentType);
            } else {
                parseLenient(responseJson, result);
            }

            return result;
        } catch (Exception e) {
            if (mode == JsonParsingMode.STRICT) {
                throw new ResponseParsingException(
                    "Failed to parse response as JSON: " + e.getMessage(),
                    e,
                    statusCode,
                    contentType
                );
            } else {
                logger.warn("Failed to parse response JSON: {}", e.getMessage());
                result.put("text", body);
                return result;
            }
        }
    }

    private void parseStrict(JsonNode responseJson, ObjectNode result, int statusCode, String contentType)
            throws ResponseParsingException {
        if (responseJson.isObject()) {
            result.set("json", (ObjectNode) responseJson);
        } else {
            throw new ResponseParsingException(
                "Expected JSON object but got " + responseJson.getNodeType(),
                statusCode,
                contentType
            );
        }
    }

    private void parseLenient(JsonNode responseJson, ObjectNode result) {
        if (responseJson.isObject()) {
            result.set("json", (ObjectNode) responseJson);
        } else if (responseJson.isTextual()) {
            // Handle potential double-encoded JSON
            try {
                JsonNode parsedJson = objectMapper.readTree(responseJson.asText());
                if (parsedJson.isObject()) {
                    result.set("json", (ObjectNode) parsedJson);
                } else {
                    result.set("json", parsedJson);
                }
            } catch (Exception e) {
                result.put("text", responseJson.asText());
            }
        } else {
            result.set("json", responseJson);
        }
    }
}

