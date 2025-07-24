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
public class ProcessingFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ProcessingFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ProcessingFailedCriterion initialized with SerializerFactory");
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
        return "ProcessingFailedCriterion".equals(modelSpec.operationName()) &&
               "petEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetEvent entity) {
        // Business logic for ProcessingFailedCriterion:
        // Fail if status is PROCESSING (meaning processing failed to move forward)
        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == PetEvent.StatusEnum.PROCESSED) {
            return EvaluationOutcome.fail("Event already processed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getStatus() == PetEvent.StatusEnum.RECORDED) {
            return EvaluationOutcome.fail("Event still recorded, processing not started", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getStatus() == PetEvent.StatusEnum.PROCESSED) {
            return EvaluationOutcome.fail("Event already processed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        // Here assuming if status is PROCESSING, then processing failed
        if (entity.getStatus() != PetEvent.StatusEnum.PROCESSED && entity.getStatus() != PetEvent.StatusEnum.RECORDED) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Event status invalid for failure criterion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
