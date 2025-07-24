package com.java_template.application.criterion;

import com.java_template.application.entity.PetIngestionJob;
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
public class IsProcessingFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsProcessingFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsProcessingFailedCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsProcessingFailedCriterion".equals(modelSpec.operationName()) &&
               "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetIngestionJob entity) {
        // Business logic for failed processing:
        // Job status must be PROCESSING or FAILED
        // If status is FAILED, it's a failure

        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!"PROCESSING".equalsIgnoreCase(entity.getStatus()) && !"FAILED".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.fail("Job status must be PROCESSING or FAILED for failure evaluation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.fail("Processing failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Additional checks can be implemented here if needed

        return EvaluationOutcome.success();
    }
}
