package com.java_template.common.workflow;

import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.statemachine.TransitionInfo;
import org.cyoda.cloud.api.event.common.statemachine.WorkflowInfo;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * ABOUTME: Sealed class hierarchy representing different types of operations.
 */
public sealed class OperationSpecification
    permits OperationSpecification.Entity, OperationSpecification.Criterion, OperationSpecification.Processor {

    private final @NotNull ModelSpec modelKey;
    private final @NotNull String operationName;

    protected OperationSpecification(@NotNull ModelSpec modelKey, @NotNull String operationName) {
        this.modelKey = modelKey;
        this.operationName = operationName;
    }

    public @NotNull ModelSpec modelKey() {
        return modelKey;
    }

    public @NotNull String operationName() {
        return operationName;
    }

    /**
     * Represents a basic entity operation without workflow context.
     */
    public static final class Entity extends OperationSpecification {

        public Entity(@NotNull ModelSpec modelKey, @NotNull String operationName) {
            super(modelKey, operationName);
        }
    }

    /**
     * Represents a criterion-based transition operation.
     */
    public static final class Criterion extends OperationSpecification {
        private final @NotNull String stateName;
        private final @NotNull String transitionName;
        private final @NotNull String workflowName;

        public Criterion(@NotNull ModelSpec modeKey,
                         @NotNull String criterionName,
                         @NotNull String stateName,
                         @NotNull String transitionName,
                         @NotNull String workflowName) {
            super(modeKey, criterionName);
            this.stateName = stateName;
            this.transitionName = transitionName;
            this.workflowName = workflowName;
        }

        public @NotNull String criterionName() {
            return operationName();
        }

        public @NotNull String stateName() {
            return stateName;
        }

        public @NotNull String transitionName() {
            return transitionName;
        }

        public @NotNull String workflowName() {
            return workflowName;
        }

        /**
         * Gets the criterion name for this operation.
         * @return the criterion name
         */
        public String getCriterionName() {
            return criterionName();
        }
    }

    /**
     * Represents a processor-based transition operation.
     */
    public static final class Processor extends OperationSpecification {
        private final @NotNull String stateName;
        private final @NotNull String transitionName;
        private final @NotNull String workflowName;

        public Processor(@NotNull ModelSpec modelKey,
                         @NotNull String processorName,
                         @NotNull String stateName,
                         @NotNull String transitionName,
                         @NotNull String workflowName) {
            super(modelKey,processorName);
            this.stateName = stateName;
            this.transitionName = transitionName;
            this.workflowName = workflowName;
        }

        public @NotNull String processorName() {
            return operationName();
        }

        public @NotNull String stateName() {
            return stateName;
        }

        public @NotNull String transitionName() {
            return transitionName;
        }

        public @NotNull String workflowName() {
            return workflowName;
        }

        /**
         * Gets the processor name for this operation.
         * @return the processor name
         */
        public String getProcessorName() {
            return processorName();
        }
    }

    public static Processor create(EntityProcessorCalculationRequest request, EntityMetadata metadata) {

        // TODO This is just a suggestion of things to add. Need to agree what we really should pack up.
        String processorName = request.getProcessorName();

        String stateName = metadata.getState();
        String transitionName = Optional.ofNullable(request.getTransition()).map(TransitionInfo::getName).orElseThrow(
                () -> new IllegalStateException("Transition name is required for processor operation")
        );
        String workflowName = Optional.ofNullable(request.getWorkflow()).map(WorkflowInfo::getName).orElseThrow(
                () -> new IllegalStateException("Workflow name is required for processor operation")
        );

        return new Processor(metadata.getModelKey(), processorName, stateName, transitionName, workflowName);
    }

    public static Criterion create(EntityCriteriaCalculationRequest request, EntityMetadata metadata) {

        // TODO This is just a suggestion of things to add. Need to agree what we really should pack up.
        String criteriaName = request.getCriteriaName();
        String stateName = metadata.getState();
        String transitionName = Optional.ofNullable(request.getTransition()).map(TransitionInfo::getName).orElseThrow(
                () -> new IllegalStateException("Transition name is required for this operation")
        );
        String workflowName = Optional.ofNullable(request.getWorkflow()).map(WorkflowInfo::getName).orElseThrow(
                () -> new IllegalStateException("Workflow name is required for this operation")
        );

        return new Criterion(metadata.getModelKey(), criteriaName, stateName, transitionName, workflowName);
    }

}
