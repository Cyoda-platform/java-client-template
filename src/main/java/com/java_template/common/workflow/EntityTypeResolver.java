package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for converting between ObjectNode and CyodaEntity types.
 * Handles the conversion logic at the workflow boundary.
 */
@Component
public class EntityTypeResolver {

    private static final Logger logger = LoggerFactory.getLogger(EntityTypeResolver.class);

    private final ObjectMapper objectMapper;

    public EntityTypeResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts an ObjectNode to a specific CyodaEntity type.
     * @param payload the ObjectNode to convert
     * @param entityType the target entity class
     * @param <T> the entity type
     * @return the converted entity
     * @throws RuntimeException if conversion fails
     */
    public <T extends CyodaEntity> T convertToEntity(ObjectNode payload, Class<T> entityType) {
        try {
            T entity = objectMapper.treeToValue(payload, entityType);
            logger.debug("Successfully converted ObjectNode to {}", entityType.getSimpleName());
            return entity;
        } catch (Exception e) {
            logger.error("Failed to convert ObjectNode to {}: {}", entityType.getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Entity conversion failed for type: " + entityType.getSimpleName(), e);
        }
    }

    /**
     * Converts a CyodaEntity to an ObjectNode.
     * @param entity the entity to convert
     * @param <T> the entity type
     * @return the converted ObjectNode
     * @throws RuntimeException if conversion fails
     */
    public <T extends CyodaEntity> ObjectNode convertToObjectNode(T entity) {
        try {
            ObjectNode result = objectMapper.valueToTree(entity);
            logger.debug("Successfully converted {} to ObjectNode", entity.getClass().getSimpleName());
            return result;
        } catch (Exception e) {
            logger.error("Failed to convert {} to ObjectNode: {}", entity.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("ObjectNode conversion failed for entity: " + entity.getClass().getSimpleName(), e);
        }
    }

    /**
     * Safely converts ObjectNode to entity with validation.
     * @param payload the ObjectNode to convert
     * @param entityType the target entity class
     * @param <T> the entity type
     * @return the converted and validated entity, or null if invalid
     */
    public <T extends CyodaEntity> T convertAndValidate(ObjectNode payload, Class<T> entityType) {
        try {
            T entity = convertToEntity(payload, entityType);
            if (entity.isValid()) {
                return entity;
            } else {
                logger.warn("Converted entity {} is not valid", entityType.getSimpleName());
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to convert and validate entity {}: {}", entityType.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
