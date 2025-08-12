package com.java_template.prototype.workflow;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for getting workflow orchestrators by entity model name.
 * Now uses the GenericWorkflowOrchestrator instead of entity-specific implementations.
 */
@Component
@Profile("prototype")
public class WorkflowOrchestratorFactory {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowOrchestratorFactory.class);

    private final Map<String, WorkflowOrchestrator> orchestratorCache = new ConcurrentHashMap<>();
    private final GenericWorkflowOrchestratorFactory genericOrchestratorFactory;

    public WorkflowOrchestratorFactory(GenericWorkflowOrchestratorFactory genericOrchestratorFactory) {
        this.genericOrchestratorFactory = genericOrchestratorFactory;
        logger.debug("Initialized WorkflowOrchestratorFactory with GenericWorkflowOrchestratorFactory");
    }
    
    /**
     * Gets the workflow orchestrator for the specified entity model.
     * Creates a generic orchestrator instance if one doesn't exist.
     *
     * @param entityModel The entity model name (e.g., "Job", "Laureate", "Subscriber")
     * @return The workflow orchestrator for the entity model
     * @throws IllegalArgumentException if no workflow definition is found for the entity model
     */
    public WorkflowOrchestrator getOrchestrator(String entityModel) {
        return orchestratorCache.computeIfAbsent(entityModel, this::createOrchestrator);
    }

    /**
     * Creates a new orchestrator for the specified entity model.
     *
     * @param entityModel The entity model name
     * @return The workflow orchestrator
     * @throws IllegalArgumentException if no workflow definition exists
     */
    private WorkflowOrchestrator createOrchestrator(String entityModel) {
        if (!genericOrchestratorFactory.canCreateOrchestrator(entityModel)) {
            throw new IllegalArgumentException("No workflow definition found for entity model: " + entityModel);
        }

        WorkflowOrchestrator orchestrator = genericOrchestratorFactory.getOrchestrator(entityModel);
        logger.debug("Created orchestrator for entity model: {}", entityModel);
        return orchestrator;
    }

    /**
     * Checks if an orchestrator exists for the given entity model.
     *
     * @param entityModel The entity model name
     * @return true if a workflow definition exists, false otherwise
     */
    public boolean hasOrchestrator(String entityModel) {
        return orchestratorCache.containsKey(entityModel) ||
               genericOrchestratorFactory.canCreateOrchestrator(entityModel);
    }
}
