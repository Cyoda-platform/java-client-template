package com.java_template.common.workflow;

/**
 * Base interface for all Cyoda entities.
 * Provides common functionality for entity identification and validation.
 */
public interface CyodaEntity {

    /**
     * Gets the entity type identifier.
     * @return the entity type as a string
     */
    String getEntityType();

    /**
     * Gets the entity class.
     * @return the Class object for this entity type
     */
    Class<? extends CyodaEntity> getClazz();

    /**
     * Gets the entity name (simple class name).
     * @return the simple class name
     */
    String getName();

    /**
     * Validates the entity data.
     * @return true if the entity is valid, false otherwise
     */
    default boolean isValid() {
        return true;
    }
}
