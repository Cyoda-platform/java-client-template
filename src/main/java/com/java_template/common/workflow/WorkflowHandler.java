package com.java_template.common.workflow;

import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all workflow handlers.
 * Defines the contract for workflow processing methods.
 * 
 * @param <T> the type of entity this workflow handles
 */
public interface WorkflowHandler<T extends WorkflowEntity> {
    
    /**
     * Gets the entity type this workflow handles.
     * @return the entity type identifier
     */
    String getEntityType();
    
    /**
     * Creates an entity instance from the given data.
     * @param data the raw entity data
     * @return a new entity instance
     */
    T createEntity(Object data);
    
    /**
     * Processes the entity through the workflow.
     * This is the main entry point for workflow execution.
     * 
     * @param entity the entity to process
     * @param methodName the specific workflow method to execute
     * @return a CompletableFuture containing the processed entity
     */
    CompletableFuture<T> processEntity(T entity, String methodName);
    
    /**
     * Gets all available workflow method names for this handler.
     * @return array of method names
     */
    String[] getAvailableMethods();
}
