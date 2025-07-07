package com.java_template.common.workflow;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for Cyoda workflow processors.
 * Each workflow method should be implemented as a separate processor class.
 * Processors work directly with CyodaEntity types for type safety and cleaner code.
 *
 * @param <T> the specific CyodaEntity type this processor handles
 */
public interface CyodaProcessor<T extends CyodaEntity> {

    /**
     * Processes the given entity.
     * @param entity the entity to process
     * @return CompletableFuture containing the processed entity
     */
    CompletableFuture<T> process(T entity);

    /**
     * Gets the entity type class that this processor handles.
     * @return the Class object for the entity type
     */
    Class<T> getEntityType();
}
