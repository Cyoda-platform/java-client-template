package com.java_template.application.processor;

import com.java_template.application.criterion.accrual.AccrualValidationCriterion;
import com.java_template.application.entity.accrual.version_1.Accrual;
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
 * ABOUTME: Processor that attaches validation error reasons to Accrual entities.
 */
@Component
public class AttachAccrualValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final AccrualValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachAccrualValidationErrorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.validationCriterion = new AccrualValidationCriterion(serializerFactory, entityService);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to Accrual entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EntityWithMetadata<Accrual> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {
        
        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        CriterionSerializer.CriterionEntityEvaluationContext<Accrual> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            accrual.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to Accrual {}: {}", accrual.getAccrualId(), errorReason);
        } else {
            logger.warn("Validation passed for Accrual {}, but error processor was called.", accrual.getAccrualId());
        }

        return entityWithMetadata;
    }
}

