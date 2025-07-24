package com.java_template.application.criterion;

import com.java_template.application.entity.PetRegistrationJob;
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
public class PetRegistrationJobValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetRegistrationJobValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetRegistrationJobValidationFailedCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetRegistrationJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRegistrationJobValidationFailedCriterion".equals(modelSpec.operationName()) &&
               "petRegistrationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetRegistrationJob entity) {
        // This criterion should fail if any required field is missing or invalid
        if (entity.getPetName() == null || entity.getPetName().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getPetType() == null || entity.getPetType().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getPetStatus() == null || entity.getPetStatus().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getOwnerName() == null || entity.getOwnerName().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getCreatedAt() == null) {
            return EvaluationOutcome.success();
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.success();
        }
        String status = entity.getStatus();
        if ("PENDING".equals(status) || "PROCESSING".equals(status) || "COMPLETED".equals(status) || "FAILED".equals(status)) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Validation failed for PetRegistrationJob", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
