package com.java_template.application.criterion;

import com.java_template.application.entity.EmailDispatchRecord;
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
public class IsEmailDispatchSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsEmailDispatchSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsEmailDispatchSuccessfulCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(EmailDispatchRecord.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsEmailDispatchSuccessfulCriterion".equals(modelSpec.operationName()) &&
               "emailDispatchRecord".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(EmailDispatchRecord entity) {
        if (entity.getJobTechnicalId() == null || entity.getJobTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("JobTechnicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getDispatchStatus() == null || entity.getDispatchStatus().isBlank()) {
            return EvaluationOutcome.fail("DispatchStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!entity.getDispatchStatus().equals("PENDING") && !entity.getDispatchStatus().equals("SENT") && !entity.getDispatchStatus().equals("FAILED")) {
            return EvaluationOutcome.fail("DispatchStatus must be PENDING, SENT, or FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // sentAt can be null if dispatchStatus is not SENT
        if (entity.getDispatchStatus().equals("SENT") && (entity.getSentAt() == null || entity.getSentAt().isBlank())) {
            return EvaluationOutcome.fail("SentAt timestamp is required when dispatchStatus is SENT", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
