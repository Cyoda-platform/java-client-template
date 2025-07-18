package com.java_template.application.criterion;

import com.java_template.application.entity.NbaScoreJob;
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
public class CanStartProcessingCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CanStartProcessingCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CanStartProcessingCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(NbaScoreJob.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CanStartProcessingCriterion".equals(modelSpec.operationName()) &&
                "nbaScoreJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(NbaScoreJob entity) {
        // Business logic: Can start processing if status is PENDING and id, date, status are valid
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status must not be null or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!"PENDING".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.fail("Job status must be PENDING to start processing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.fail("Job ID must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getDate() == null) {
            return EvaluationOutcome.fail("Job date must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
