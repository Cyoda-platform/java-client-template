package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Base interface for all workflow entities.
 * Provides common functionality for entity manipulation and conversion.
 */
public interface WorkflowEntity {
    
    /**
     * Converts this entity to an ObjectNode for JSON processing.
     * @return ObjectNode representation of this entity
     */
    ObjectNode toObjectNode();
    
    /**
     * Updates this entity from an ObjectNode.
     * @param objectNode the ObjectNode containing updated data
     */
    void fromObjectNode(ObjectNode objectNode);
    
    /**
     * Gets the entity type identifier.
     * @return the entity type as a string
     */
    String getEntityType();
    
    /**
     * Validates the entity data.
     * @return true if the entity is valid, false otherwise
     */
    default boolean isValid() {
        return true;
    }
}
