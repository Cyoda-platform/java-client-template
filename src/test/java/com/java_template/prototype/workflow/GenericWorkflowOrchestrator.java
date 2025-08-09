package com.java_template.prototype.workflow;

import com.java_template.common.workflow.OperationFactory;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.prototype.workflow.model.*;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * Generic workflow orchestrator that executes workflows based on JSON definitions.
 * Replaces entity-specific orchestrators with a single, configurable implementation.
 *
 * Note: This class is not a Spring component. Instances are created by GenericWorkflowOrchestratorFactory.
 */
public class GenericWorkflowOrchestrator implements WorkflowOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(GenericWorkflowOrchestrator.class);
    
    private final WorkflowDefinitionLoader workflowLoader;
    private final OperationFactory operationFactory;
    private final String supportedEntityModel;
    
    public GenericWorkflowOrchestrator(WorkflowDefinitionLoader workflowLoader, 
                                     OperationFactory operationFactory) {
        this.workflowLoader = workflowLoader;
        this.operationFactory = operationFactory;
        this.supportedEntityModel = "GENERIC"; // Will be overridden by factory
    }
    
    // Constructor for entity-specific instances
    public GenericWorkflowOrchestrator(WorkflowDefinitionLoader workflowLoader, 
                                     OperationFactory operationFactory,
                                     String entityModel) {
        this.workflowLoader = workflowLoader;
        this.operationFactory = operationFactory;
        this.supportedEntityModel = entityModel;
    }
    
    @Override
    public String run(String technicalId,
                     CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
                     CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext,
                     String transition) {
        
        logger.info("Running GenericWorkflowOrchestrator for entity {} (model: {}) with transition: {}", 
                technicalId, supportedEntityModel, transition);
        
        WorkflowDefinition workflow = workflowLoader.loadWorkflowDefinition(supportedEntityModel);
        if (workflow == null) {
            logger.error("No workflow definition found for entity model: {}", supportedEntityModel);
            return "error_state";
        }
        
        try {
            return executeWorkflowTransition(workflow, transition, processorContext, criteriaContext);
        } catch (Exception e) {
            logger.error("Error executing workflow transition: {} for entity model: {}", transition, supportedEntityModel, e);
            return "error_state";
        }
    }
    
    /**
     * Executes a workflow transition based on the workflow definition.
     * For state_initial: executes the entire workflow from the beginning.
     * For entity_updated: starts from the specific transition and continues through the workflow.
     * For specific transitions: executes that transition and continues the workflow.
     */
    private String executeWorkflowTransition(WorkflowDefinition workflow,
                                           String transitionName,
                                           CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
                                           CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext) {

        String currentState;

        // Handle initial state - start from the beginning of the workflow
        if ("state_initial".equals(transitionName)) {
            logger.info("Starting workflow from initial state: {}", workflow.getInitialState());
            currentState = workflow.getInitialState();
        }
        // Handle entity_updated - restart from initial state
        else if ("entity_updated".equals(transitionName)) {
            logger.info("Entity updated, restarting workflow from initial state: {}", workflow.getInitialState());
            currentState = workflow.getInitialState();
        }
        // Handle specific transition
        else {
            logger.info("Executing specific transition: {}", transitionName);
            return findAndExecuteTransition(workflow, transitionName, processorContext, criteriaContext);
        }

        // Execute the workflow from the current state
        return executeWorkflowFromState(workflow, currentState, processorContext, criteriaContext);
    }

    /**
     * Executes the workflow starting from a specific state, following automatic transitions.
     */
    private String executeWorkflowFromState(WorkflowDefinition workflow,
                                           String currentState,
                                           CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
                                           CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext) {

        logger.debug("Executing workflow from state: {}", currentState);

        while (currentState != null && !isTerminalState(workflow, currentState)) {
            State state = workflow.getStates().get(currentState);
            if (state == null) {
                logger.warn("State {} not found in workflow definition", currentState);
                return "error_state";
            }

            // Find automatic transitions (non-manual)
            Transition automaticTransition = state.getTransitions().stream()
                    .filter(t -> !t.isManual())
                    .findFirst()
                    .orElse(null);

            if (automaticTransition == null) {
                logger.debug("No automatic transitions found in state {}, workflow complete", currentState);
                return currentState;
            }

            // Execute the automatic transition
            String nextState = executeTransition(automaticTransition, processorContext, criteriaContext);
            if (nextState == null) {
                // Criteria failed, workflow stops here
                logger.debug("Automatic transition {} failed criteria in state {}, workflow stops",
                        automaticTransition.getName(), currentState);
                return currentState;
            }

            logger.debug("Automatic transition {} executed: {} -> {}",
                    automaticTransition.getName(), currentState, nextState);
            currentState = nextState;
        }

        logger.info("Workflow execution completed at state: {}", currentState);
        return currentState;
    }

    /**
     * Checks if a state is terminal (has no outgoing transitions).
     */
    private boolean isTerminalState(WorkflowDefinition workflow, String stateName) {
        State state = workflow.getStates().get(stateName);
        return state == null || state.getTransitions().isEmpty();
    }
    
    /**
     * Finds and executes a transition across all states in the workflow.
     * If criteria fails for a transition, it continues searching for other matching transitions.
     */
    private String findAndExecuteTransition(WorkflowDefinition workflow,
                                          String transitionName,
                                          CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
                                          CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext) {

        // Search all states for the transition
        for (Map.Entry<String, State> stateEntry : workflow.getStates().entrySet()) {
            String stateName = stateEntry.getKey();
            State state = stateEntry.getValue();

            Transition transition = state.getTransition(transitionName);
            if (transition != null) {
                logger.debug("Found transition {} in state {}", transitionName, stateName);
                String result = executeTransition(transition, processorContext, criteriaContext);

                if (result != null) {
                    // Transition executed successfully
                    return result;
                } else {
                    // Criteria failed for this transition, continue searching for other matching transitions
                    logger.debug("Transition {} in state {} failed criteria, continuing search", transitionName, stateName);
                }
            }
        }

        logger.warn("No executable transition {} found in workflow {} (either not found or all failed criteria)",
                transitionName, workflow.getName());
        return "criteria_failed";
    }
    
    /**
     * Executes a specific transition only if criteria matches.
     * Returns null if criteria fails, indicating the transition should not be executed.
     */
    private String executeTransition(Transition transition,
                                   CyodaEventContext<EntityProcessorCalculationRequest> processorContext,
                                   CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext) {

        // Check criteria if present - only execute transition if criteria matches
        if (transition.hasCriterion()) {
            boolean criteriaResult = executeCriteria(transition.getCriterion(), criteriaContext);
            if (!criteriaResult) {
                logger.debug("Criteria evaluation failed for transition: {}, transition will not be executed", transition.getName());
                return null; // Don't execute this transition
            }
        }

        // Execute processors if present (only if criteria passed or no criteria)
        if (transition.hasProcessors()) {
            executeProcessors(transition.getProcessors(), processorContext);
        }

        logger.debug("Transition {} executed successfully, moving to state: {}", transition.getName(), transition.getNextState());
        return transition.getNextState();
    }
    
    /**
     * Executes criteria for a transition.
     */
    private boolean executeCriteria(CriterionConfig criterionConfig,
                                  CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext) {

        if (criterionConfig.getFunction() == null) {
            logger.warn("Criterion configuration missing function");
            return false;
        }

        String criterionName = criterionConfig.getFunctionName();
        logger.debug("Executing criterion: {}", criterionName);

        try {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName("default");
            modelSpec.setVersion(1);

            OperationSpecification.Criterion criterionSpec = new OperationSpecification.Criterion(
                modelSpec, criterionName, "", "", ""
            );

            EntityCriteriaCalculationResponse response = operationFactory.getCriteriaForModel(criterionSpec).check(criteriaContext);
            return response.getMatches();
        } catch (Exception e) {
            logger.error("Error executing criterion: {}", criterionName, e);
            return false;
        }
    }

    /**
     * Executes processors for a transition.
     */
    private void executeProcessors(java.util.List<ProcessorConfig> processors,
                                 CyodaEventContext<EntityProcessorCalculationRequest> processorContext) {

        for (ProcessorConfig processorConfig : processors) {
            String processorName = processorConfig.getName();
            logger.debug("Executing processor: {}", processorName);

            try {
                ModelSpec modelSpec = new ModelSpec();
                modelSpec.setName("default");
                modelSpec.setVersion(1);

                OperationSpecification.Processor processorSpec = new OperationSpecification.Processor(
                    modelSpec, processorName, "", "", ""
                );

                operationFactory.getProcessorForModel(processorSpec).process(processorContext);
                logger.debug("Successfully executed processor: {}", processorName);
            } catch (Exception e) {
                logger.error("Error executing processor: {}", processorName, e);
                // Continue with other processors even if one fails
            }
        }
    }

    @Override
    public String getSupportedEntityModel() {
        return supportedEntityModel;
    }
}
