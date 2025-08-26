package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserVerifiedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public UserVerifiedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User user = context.entity();
        if (user == null) {
            logger.warn("UserVerifiedCriterion: entity is null");
            return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        String status = user.getStatus();
        if (status == null || status.isBlank()) {
            logger.debug("UserVerifiedCriterion: missing status for user id={}", user.getId());
            return EvaluationOutcome.fail("User status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Business rule: user must be verified to pass this criterion
        if (!"verified".equalsIgnoreCase(status.trim())) {
            logger.debug("UserVerifiedCriterion: user id={} not verified (status={})", user.getId(), status);
            return EvaluationOutcome.fail("User is not verified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Data quality check: verified users must have an email
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            logger.debug("UserVerifiedCriterion: verified user id={} missing email", user.getId());
            return EvaluationOutcome.fail("Verified user missing email", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}