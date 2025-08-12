package com.java_template.prototype.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;

import java.util.function.Consumer;

/**
 * Interface for workflow orchestrators that handle entity lifecycle transitions.
 * Each entity type should have its own orchestrator implementation.
 */
public interface WorkflowOrchestrator {

    /**
     * Runs the workflow orchestrator for the given transition with mock contexts.
     *
     * @param technicalId The technical ID of the entity
     * @param processorContext Mock context for processor execution
     * @param criteriaContext Mock context for criteria evaluation
     * @param transition The current transition/state
     * @return The next transition/state
     */
    String run(String technicalId,
               CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
               CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext,
               String transition);

    /**
     * Runs the workflow orchestrator with a callback that is invoked after each transition's processors
     * have executed, providing the updated entity data to allow persistence.
     * Default implementation delegates to the legacy run() without updates.
     */
    default String run(String technicalId,
               CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
               CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext,
               String transition,
               Consumer<ObjectNode> onDataUpdated) {
        return run(technicalId, processorContext, criteriaContext, transition);
    }

    /**
     * Returns the entity model name that this orchestrator supports.
     *
     * @return The entity model name (e.g., "Job", "Laureate", "Subscriber")
     */
    String getSupportedEntityModel();
}
