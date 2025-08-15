package com.java_template.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonUtils {
    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String mapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Error converting Map to JSON", e);
        }
    }

    public Map<String, Object> jsonToMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JSON to Map", e);
        }
    }

    public String toJson(Object data) {
        try {
            if (data instanceof String) {
                return (String) data;
            }
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }

    public String getJsonString(Object responseJson) {
        if (responseJson instanceof String) {
            return (String) responseJson;
        } else {
            return toJson(responseJson);
        }
    }

    public JsonNode getJsonNode(Object object) {
        return objectMapper.valueToTree(object);
    }

    // New helper methods used across processors
    public JsonNode parse(String json) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON string", e);
        }
    }

    public ObjectNode parseToObjectNode(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return objectMapper.createObjectNode();
            }
            if (node.isObject()) {
                return (ObjectNode) node;
            }
            // If it's not an object, wrap it inside an object with a single field "value"
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("value", node);
            return wrapper;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON to ObjectNode", e);
        }
    }

    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    public <T> T fromJsonNode(JsonNode node, Class<T> clazz) {
        try {
            if (node == null || node.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(node, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error converting JsonNode to " + clazz.getSimpleName(), e);
        }
    }
}
