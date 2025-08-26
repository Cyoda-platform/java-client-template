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

import java.util.regex.Pattern;

@Component
public class UserInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String CRITERION_NAME = "UserInvalidCriterion";

    public UserInvalidCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return CRITERION_NAME.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
        User entity = context.entity();
        if (entity == null) {
            logger.debug("UserInvalidCriterion: entity is null");
            return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // If the entity was already marked invalid, propagate as data quality failure
        String status = entity.getValidationStatus();
        if (status != null && status.equalsIgnoreCase("INVALID")) {
            return EvaluationOutcome.fail("User already marked INVALID", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Required fields: username and email
        String username = entity.getUsername();
        if (username == null || username.isBlank()) {
            return EvaluationOutcome.fail("username is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String email = entity.getEmail();
        if (email == null || email.isBlank()) {
            return EvaluationOutcome.fail("email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Basic email format check
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return EvaluationOutcome.fail("email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Source fetched timestamp should be present for ingested users
        if (entity.getSourceFetchedAt() == null) {
            return EvaluationOutcome.fail("sourceFetchedAt is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // All checks passed -> success
        return EvaluationOutcome.success();
    }
}