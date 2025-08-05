package com.java_template.common.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.statemachine.TransitionInfo;
import org.cyoda.cloud.api.event.common.statemachine.WorkflowInfo;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * ABOUTME: Sealed class hierarchy representing different types of operations.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OperationSpecification.Entity.class, name = "entity"),
    @JsonSubTypes.Type(value = OperationSpecification.Criterion.class, name = "criterion"),
    @JsonSubTypes.Type(value = OperationSpecification.Processor.class, name = "processor")
})
public sealed class OperationSpecification
    permits OperationSpecification.Entity, OperationSpecification.Criterion, OperationSpecification.Processor {

    @JsonProperty("modelKey")
    private final @NotNull ModelSpec modelKey;

    @JsonProperty("operationName")
    private final @NotNull String operationName;

    protected OperationSpecification(@NotNull ModelSpec modelKey, @NotNull String operationName) {
        this.modelKey = modelKey;
        this.operationName = operationName;
    }

    // Default constructor for Jackson
    protected OperationSpecification() {
        this.modelKey = new ModelSpec();
        this.operationName = "";
    }

    public @NotNull ModelSpec modelKey() {
        return modelKey;
    }

    public @NotNull String operationName() {
        return operationName;
    }

    // Jackson needs these getters for serialization
    @JsonProperty("modelKey")
    public @NotNull ModelSpec getModelKey() {
        return modelKey;
    }

    @JsonProperty("operationName")
    public @NotNull String getOperationName() {
        return operationName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OperationSpecification that = (OperationSpecification) o;
        return Objects.equals(modelKey, that.modelKey) && Objects.equals(operationName, that.operationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelKey, operationName);
    }

    /**
     * Represents a basic entity operation without workflow context.
     */
    public static final class Entity extends OperationSpecification {

        public Entity(@NotNull ModelSpec modelKey, @NotNull String operationName) {
            super(modelKey, operationName);
        }

        // Default constructor for Jackson
        public Entity() {
            super(new ModelSpec(), "");
        }
    }

    /**
     * Represents a criterion-based transition operation.
     */
    public static final class Criterion extends OperationSpecification {
        @JsonProperty("stateName")
        private final @NotNull String stateName;

        @JsonProperty("transitionName")
        private final @NotNull String transitionName;

        @JsonProperty("workflowName")
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

        // Default constructor for Jackson
        public Criterion() {
            super(new ModelSpec(), "");
            this.stateName = "";
            this.transitionName = "";
            this.workflowName = "";
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

        // Jackson getters
        @JsonProperty("stateName")
        public @NotNull String getStateName() {
            return stateName;
        }

        @JsonProperty("transitionName")
        public @NotNull String getTransitionName() {
            return transitionName;
        }

        @JsonProperty("workflowName")
        public @NotNull String getWorkflowName() {
            return workflowName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Criterion criterion = (Criterion) o;
            return Objects.equals(stateName, criterion.stateName) && Objects.equals(transitionName, criterion.transitionName) && Objects.equals(workflowName, criterion.workflowName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), stateName, transitionName, workflowName);
        }
    }

    /**
     * Represents a processor-based transition operation.
     */
    public static final class Processor extends OperationSpecification {
        @JsonProperty("stateName")
        private final @NotNull String stateName;

        @JsonProperty("transitionName")
        private final @NotNull String transitionName;

        @JsonProperty("workflowName")
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

        // Default constructor for Jackson
        public Processor() {
            super(new ModelSpec(), "");
            this.stateName = "";
            this.transitionName = "";
            this.workflowName = "";
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

        // Jackson getters
        @JsonProperty("stateName")
        public @NotNull String getStateName() {
            return stateName;
        }

        @JsonProperty("transitionName")
        public @NotNull String getTransitionName() {
            return transitionName;
        }

        @JsonProperty("workflowName")
        public @NotNull String getWorkflowName() {
            return workflowName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Processor processor = (Processor) o;
            return Objects.equals(stateName, processor.stateName) && Objects.equals(transitionName, processor.transitionName) && Objects.equals(workflowName, processor.workflowName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), stateName, transitionName, workflowName);
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
