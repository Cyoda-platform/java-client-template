package com.java_template.application.criterion;

import com.java_template.application.entity.PetEvent;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CanProcessEventCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CanProcessEventCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CanProcessEventCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetEvent.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CanProcessEventCriterion".equals(modelSpec.operationName()) &&
               "petEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetEvent entity) {
        // Business logic: Can process event if status is RECORDED and eventType is either CREATED or UPDATED
        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getEventType() == null || entity.getEventType().isBlank()) {
            return EvaluationOutcome.fail("Event type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"RECORDED".equals(entity.getStatus())) {
            return EvaluationOutcome.fail("Event status must be RECORDED to process", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (!("CREATED".equals(entity.getEventType()) || "UPDATED".equals(entity.getEventType()))) {
            return EvaluationOutcome.fail("Event type must be CREATED or UPDATED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
