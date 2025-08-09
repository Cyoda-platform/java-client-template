package com.java_template.prototype.workflow;

import com.java_template.common.workflow.OperationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that creates entity-specific instances of GenericWorkflowOrchestrator.
 * This allows the generic orchestrator to support multiple entity types.
 */
@Component
@Profile("prototype")
public class GenericWorkflowOrchestratorFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(GenericWorkflowOrchestratorFactory.class);
    
    private final WorkflowDefinitionLoader workflowLoader;
    private final OperationFactory operationFactory;
    private final Map<String, GenericWorkflowOrchestrator> orchestratorCache = new ConcurrentHashMap<>();
    
    public GenericWorkflowOrchestratorFactory(WorkflowDefinitionLoader workflowLoader,
                                            OperationFactory operationFactory) {
        this.workflowLoader = workflowLoader;
        this.operationFactory = operationFactory;
    }
    
    /**
     * Gets or creates a workflow orchestrator for the specified entity model.
     * 
     * @param entityModel The entity model name (e.g., "Job", "Laureate", "Subscriber")
     * @return The workflow orchestrator for the entity model
     */
    public GenericWorkflowOrchestrator getOrchestrator(String entityModel) {
        return orchestratorCache.computeIfAbsent(entityModel, this::createOrchestrator);
    }
    
    /**
     * Creates a new orchestrator instance for the specified entity model.
     * 
     * @param entityModel The entity model name
     * @return The new orchestrator instance
     */
    private GenericWorkflowOrchestrator createOrchestrator(String entityModel) {
        // Verify that a workflow definition exists for this entity
        if (!workflowLoader.hasWorkflowDefinition(entityModel)) {
            logger.warn("No workflow definition found for entity model: {}", entityModel);
            // Still create the orchestrator - it will handle the missing workflow gracefully
        }
        
        GenericWorkflowOrchestrator orchestrator = new GenericWorkflowOrchestrator(
            workflowLoader, operationFactory, entityModel);
        
        logger.debug("Created GenericWorkflowOrchestrator for entity model: {}", entityModel);
        return orchestrator;
    }
    
    /**
     * Checks if an orchestrator can be created for the given entity model.
     * 
     * @param entityModel The entity model name
     * @return true if workflow definition exists, false otherwise
     */
    public boolean canCreateOrchestrator(String entityModel) {
        return workflowLoader.hasWorkflowDefinition(entityModel);
    }
    
    /**
     * Clears the orchestrator cache. Useful for testing or reloading configurations.
     */
    public void clearCache() {
        orchestratorCache.clear();
        logger.debug("GenericWorkflowOrchestrator cache cleared");
    }
    
    /**
     * Gets the number of cached orchestrators.
     * 
     * @return The cache size
     */
    public int getCacheSize() {
        return orchestratorCache.size();
    }
}
