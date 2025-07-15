package com.java_template.common.workflow;

/**
 * Base interface for all Cyoda entities.
 * Provides common functionality for entity identification and validation.
 */
public interface CyodaEntity {

    /**
     * Gets the model key for this entity, containing both model operationName and version.
     * This is used for processor selection and entity identification.
     * @return the OperationSpecification containing model operationName and version
     */
    OperationSpecification getModelKey();

    /**
     * Validates the entity data.
     * @return true if the entity is valid, false otherwise
     */
    default boolean isValid() {
        return true;
    }
}
