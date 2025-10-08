package com.java_template.application.processor;

import com.java_template.application.criterion.SettlementQuoteValidationCriterion;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
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
 * ABOUTME: Processor that attaches validation error reasons to SettlementQuote entities.
 */
@Component
public class AttachSettlementQuoteValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final SettlementQuoteValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachSettlementQuoteValidationErrorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.validationCriterion = new SettlementQuoteValidationCriterion(serializerFactory);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to SettlementQuote entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(SettlementQuote.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EntityWithMetadata<SettlementQuote> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<SettlementQuote> context) {
        
        EntityWithMetadata<SettlementQuote> entityWithMetadata = context.entityResponse();
        SettlementQuote quote = entityWithMetadata.entity();

        CriterionSerializer.CriterionEntityEvaluationContext<SettlementQuote> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            quote.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to SettlementQuote {}: {}", quote.getQuoteId(), errorReason);
        } else {
            logger.warn("Validation passed for SettlementQuote {}, but error processor was called.", quote.getQuoteId());
        }

        return entityWithMetadata;
    }
}

