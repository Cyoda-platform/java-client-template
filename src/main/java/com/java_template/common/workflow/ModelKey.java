package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

import static com.java_template.common.config.Config.ENTITY_VERSION;

/**
 * Represents a model key with name and version for processor selection.
 * Used to match processors with specific entity models and versions.
 */

public record ModelKey(String modelName, String modelVersion) {

    /**
     * Extracts ModelKey from payload for component selection.
     * This method provides a centralized way to extract entity metadata from ObjectNode payloads.
     * <p>
     * This is a simple extraction method that only looks at metadata and entityType fields.
     * <p>
     * Priority order:
     * 1. Extract from metadata field (entityName + entityVersion)
     * 2. Fallback to entityType field (backward compatibility)
     * 3. Default to generic entity
     *
     * @param payload the ObjectNode payload to extract ModelKey from
     * @return ModelKey containing entity name and version
     */
    public static ModelKey extractFromPayload(ObjectNode payload) {
        // Priority 1: Try to extract from metadata field
        if (payload.has("metadata")) {
            ObjectNode metadata = (ObjectNode) payload.get("metadata");
            String entityName = metadata.path("entityName").asText(null);
            String entityVersion = metadata.path("entityVersion").asText("1000"); // default from Config.ENTITY_VERSION

            if (entityName != null) {
                return new ModelKey(entityName, entityVersion);
            }
        }

        // Priority 2: Fallback to entityType field (for backward compatibility)
        String entityType = payload.path("entityType").asText(null);
        return new ModelKey(Objects.requireNonNullElse(entityType, "generic"), ENTITY_VERSION);

        // Priority 3: Default fallback
    }
}
