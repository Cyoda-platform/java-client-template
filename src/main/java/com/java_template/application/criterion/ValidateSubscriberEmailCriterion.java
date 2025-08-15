package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ValidateSubscriberEmailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public ValidateSubscriberEmailCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        if (subscriber == null) {
            return EvaluationOutcome.fail("Subscriber is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String email = subscriber.getEmail();
        if (email == null || email.isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Basic RFC-like email validation (simple)
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!email.matches(emailRegex)) {
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            // Check uniqueness: no other subscriber with same email and not unsubscribed
            Object condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", email)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), condition, true);
            ArrayNode items = itemsFuture.get();
            // If creating new subscriber (no technicalId in request), then any existing is a conflict
            if (items != null && items.size() > 0) {
                // If the only found is the same entity being updated, allow it (we don't have technicalId context here), so be conservative and fail to avoid duplicates
                logger.info("Subscriber email duplicate detected: {}", email);
                return EvaluationOutcome.fail("Email already subscribed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Failed to validate uniqueness for email {}: {}", email, e.getMessage());
            // On errors when checking uniqueness, fail data quality
            return EvaluationOutcome.fail("Failed to validate email uniqueness", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
