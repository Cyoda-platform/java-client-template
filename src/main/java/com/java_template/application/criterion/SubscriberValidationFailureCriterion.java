package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class SubscriberValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
            return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            return EvaluationOutcome.fail("Contact type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isEmpty()) {
            return EvaluationOutcome.fail("Contact details are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate contactType and contactDetails format
        if (!isValidContactType(subscriber.getContactType())) {
            return EvaluationOutcome.fail("Invalid contact type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (subscriber.getContactType().equalsIgnoreCase("email")) {
            if (!isValidEmail(subscriber.getContactDetails())) {
                return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else if (subscriber.getContactType().equalsIgnoreCase("webhook")) {
            if (!isValidUrl(subscriber.getContactDetails())) {
                return EvaluationOutcome.fail("Invalid webhook URL format", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }

    private boolean isValidContactType(String contactType) {
        return contactType.equalsIgnoreCase("email") || contactType.equalsIgnoreCase("webhook");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}