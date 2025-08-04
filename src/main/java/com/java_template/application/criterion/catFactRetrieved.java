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
import java.time.LocalDateTime;

@Component
public class catFactRetrieved implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public catFactRetrieved(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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

        // Business logic: For catFactRetrieved criterion, check if subscriber is active and has non-null email
        if (subscriber.getStatus() == null || !"ACTIVE".equalsIgnoreCase(subscriber.getStatus())) {
            return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Subscriber email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (subscriber.getSubscribedDate() == null || subscriber.getSubscribedDate().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Subscribed date is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Additional checks can be added as needed

        return EvaluationOutcome.success();
    }
}
