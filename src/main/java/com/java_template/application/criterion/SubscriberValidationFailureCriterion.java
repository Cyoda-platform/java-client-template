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

import java.net.MalformedURLException;
import java.net.URL;

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

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$");
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        if (subscriber == null) {
            return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String contactType = subscriber.getContactType();
        String contactAddress = subscriber.getContactAddress();
        if (contactType == null || contactType.isEmpty()) {
            return EvaluationOutcome.fail("Contact type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (contactAddress == null || contactAddress.isEmpty()) {
            return EvaluationOutcome.fail("Contact address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if ("email".equalsIgnoreCase(contactType)) {
            if (!isValidEmail(contactAddress)) {
                return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else if ("webhook".equalsIgnoreCase(contactType)) {
            if (!isValidUrl(contactAddress)) {
                return EvaluationOutcome.fail("Invalid webhook URL format", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else {
            return EvaluationOutcome.fail("Unsupported contact type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String subscribedCategories = subscriber.getSubscribedCategories();
        if (subscribedCategories == null || subscribedCategories.isEmpty()) {
            return EvaluationOutcome.fail("Subscribed categories must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
