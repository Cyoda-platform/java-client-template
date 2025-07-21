package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
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
public class PetJobStartCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetJobStartCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetJobStartCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobStartCriterion".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetJob entity) {
        // Business logic: pet job can start if jobType and payload are valid
        if (entity.getJobType() == null || entity.getJobType().isBlank()) {
            return EvaluationOutcome.fail("jobType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getPayload() == null || entity.getPayload().isEmpty()) {
            return EvaluationOutcome.fail("payload cannot be empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional rule: Only allow start if status is PENDING
        if (!"PENDING".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.fail("status must be PENDING to start", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
