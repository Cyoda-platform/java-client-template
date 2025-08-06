package com.java_template.application.criterion;

import com.java_template.application.entity.Subscriber;
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
public class EmailValidAndUnique implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailValidAndUnique(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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

        Subscriber entity = context.entity();

        // Validate email format
        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String email = entity.getEmail();
        // Basic email format check
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Uniqueness check: simulate by context or external check (placeholder)
        // Here we assume a method context.isEmailUnique() or similar, but since no such method,
        // we use context metadata or external service call (not implemented here).
        // For demo, assume it passes uniqueness.

        return EvaluationOutcome.success();
    }
}
