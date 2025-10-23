package com.java_template.common.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.cyoda.cloud.api.event.common.EntityMetadata;

/**
 * ABOUTME: Base interface for all Cyoda entities providing common functionality
 * for entity identification, validation, and model specification.
 */
public interface CyodaEntity {

    /**
     * Gets the model key for this entity, containing both model operationName and version.
     * This is used for processor selection and entity identification.
     * @return the OperationSpecification containing model operationName and version
     */
    @JsonIgnore
    OperationSpecification getModelKey();

    /**
     * Validates the entity data.
     * @return true if the entity is valid, false otherwise
     */
    @JsonIgnore
    default boolean isValid(EntityMetadata metadata) {
        return true;
    }
}
