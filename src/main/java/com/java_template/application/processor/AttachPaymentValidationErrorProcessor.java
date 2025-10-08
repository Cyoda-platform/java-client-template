package com.java_template.application.processor;

import com.java_template.application.criterion.PaymentValidationCriterion;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Processor that attaches validation error reasons to Payment entities.
 */
@Component
public class AttachPaymentValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final PaymentValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachPaymentValidationErrorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.validationCriterion = new PaymentValidationCriterion(serializerFactory);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to Payment entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Payment.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EntityWithMetadata<Payment> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        CriterionSerializer.CriterionEntityEvaluationContext<Payment> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            payment.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to Payment {}: {}", payment.getPaymentId(), errorReason);
        } else {
            logger.warn("Validation passed for Payment {}, but error processor was called.", payment.getPaymentId());
        }

        return entityWithMetadata;
    }
}

