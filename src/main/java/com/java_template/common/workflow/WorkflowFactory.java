package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Factory for managing workflow handlers and their registration.
 * Automatically discovers and registers all WorkflowHandler beans on construction.
 */
@Component
public class WorkflowFactory {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowFactory.class);

    private final Map<String, WorkflowHandler<? extends WorkflowEntity>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Function<ObjectNode, CompletableFuture<ObjectNode>>> methodRegistry = new ConcurrentHashMap<>();

    /**
     * Constructor that automatically discovers and registers all WorkflowHandler beans.
     */
    public WorkflowFactory(Map<String, WorkflowHandler> handlerBeans) {
        logger.info("Initializing WorkflowFactory with {} handler beans", handlerBeans.size());

        for (WorkflowHandler<? extends WorkflowEntity> handler : handlerBeans.values()) {
            registerHandler(handler);
        }

        logger.info("WorkflowFactory initialized with {} handlers and {} methods",
                   handlers.size(), methodRegistry.size());
    }

    /**
     * Registers a workflow handler for a specific entity type.
     *
     * @param handler the workflow handler to register
     */
    private void registerHandler(WorkflowHandler<? extends WorkflowEntity> handler) {
        String entityType = handler.getEntityType();
        handlers.put(entityType, handler);

        // Register all methods from this handler
        for (String methodName : handler.getAvailableMethods()) {
            String methodKey = methodName; // Could be prefixed with entityType if needed

            Function<ObjectNode, CompletableFuture<ObjectNode>> methodFunction = objectNode -> {
                try {
                    return processWorkflowInternal(handler, methodName, objectNode);
                } catch (Exception e) {
                    logger.error("Error processing workflow method '{}' for entity type '{}': {}",
                            methodName, entityType, e.getMessage(), e);
                    throw new RuntimeException("Workflow processing failed", e);
                }
            };

            methodRegistry.put(methodKey, methodFunction);
            logger.info("Registered workflow method: {} for entity type: {}", methodKey, entityType);
        }
    }

    /**
     * Gets a workflow handler for the specified entity type.
     *
     * @param entityType the entity type
     * @return the workflow handler, or null if not found
     */
    public WorkflowHandler<? extends WorkflowEntity> getHandler(String entityType) {
        return handlers.get(entityType);
    }

    /**
     * Gets a workflow method function by name.
     *
     * @param methodName the method name
     * @return the method function, or null if not found
     */
    public Function<ObjectNode, CompletableFuture<ObjectNode>> getMethod(String methodName) {
        return methodRegistry.get(methodName);
    }

    /**
     * Processes an entity through a specific workflow method.
     *
     * @param entityType the entity type
     * @param methodName the method name
     * @param objectNode the entity data
     * @return CompletableFuture containing the processed entity
     */
    public CompletableFuture<ObjectNode> processWorkflow(String entityType, String methodName, ObjectNode objectNode) {
        WorkflowHandler<? extends WorkflowEntity> handler = handlers.get(entityType);
        if (handler == null) {
            logger.warn("No workflow handler found for entity type: {}", entityType);
            return CompletableFuture.completedFuture(objectNode);
        }

        return processWorkflowInternal(handler, methodName, objectNode);
    }

    /**
     * Internal method to handle the generic type processing safely.
     */
    @SuppressWarnings("unchecked")
    private <T extends WorkflowEntity> CompletableFuture<ObjectNode> processWorkflowInternal(
            WorkflowHandler<T> handler, String methodName, ObjectNode objectNode) {
        try {
            T entity = handler.createEntity(objectNode);
            return handler.processEntity(entity, methodName)
                    .thenApply(WorkflowEntity::toObjectNode);
        } catch (Exception e) {
            logger.error("Error processing workflow for entity type '{}', method '{}': {}",
                    handler.getEntityType(), methodName, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }



    /**
     * Gets all registered method names.
     *
     * @return array of method names
     */
    public String[] getRegisteredMethods() {
        return methodRegistry.keySet().toArray(new String[0]);
    }
}
