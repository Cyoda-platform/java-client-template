package com.java_template.application.processor;

import com.java_template.application.criterion.NewLoanValidationCriterion;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Processor that attaches validation error reasons to Loan entities
 * when NewLoanValidationCriterion fails. This processor re-runs the validation
 * logic to capture the specific failure reason and stores it in the entity's
 * validationErrorReason field.
 */
@Component
public class AttachNewLoanValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final NewLoanValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachNewLoanValidationErrorProcessor(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        // Create an instance of the validation criterion to reuse its logic
        this.validationCriterion = new NewLoanValidationCriterion(serializerFactory, entityService);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to Loan entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Loan.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Runs validation and attaches the failure reason to the entity
     */
    private EntityWithMetadata<Loan> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Loan> context) {
        
        EntityWithMetadata<Loan> entityWithMetadata = context.entityResponse();
        Loan loan = entityWithMetadata.entity();

        // Create a criterion context to run validation
        CriterionSerializer.CriterionEntityEvaluationContext<Loan> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        // Run the validation logic
        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            // Extract the failure reason and attach it to the entity
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            loan.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to Loan {}: {}", loan.getLoanId(), errorReason);
        } else {
            logger.warn("Validation passed for Loan {}, but error processor was called. This should not happen.", 
                loan.getLoanId());
        }

        return entityWithMetadata;
    }
}

