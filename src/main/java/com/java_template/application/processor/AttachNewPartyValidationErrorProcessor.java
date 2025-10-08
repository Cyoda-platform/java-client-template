package com.java_template.application.processor;

import com.java_template.application.criterion.NewPartyValidationCriterion;
import com.java_template.application.entity.party.version_1.Party;
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
 * ABOUTME: Processor that attaches validation error reasons to Party entities.
 */
@Component
public class AttachNewPartyValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final NewPartyValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachNewPartyValidationErrorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.validationCriterion = new NewPartyValidationCriterion(serializerFactory);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to Party entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Party.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EntityWithMetadata<Party> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Party> context) {
        
        EntityWithMetadata<Party> entityWithMetadata = context.entityResponse();
        Party party = entityWithMetadata.entity();

        CriterionSerializer.CriterionEntityEvaluationContext<Party> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            party.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to Party {}: {}", party.getPartyId(), errorReason);
        } else {
            logger.warn("Validation passed for Party {}, but error processor was called.", party.getPartyId());
        }

        return entityWithMetadata;
    }
}

