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

import java.util.regex.Pattern;

@Component
public class SubscriberContactValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberContactValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(http|https)://.*$");

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
        if (subscriber.getContactType() == null || subscriber.getContactType().isEmpty()) {
            return EvaluationOutcome.fail("Contact type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String contactType = subscriber.getContactType().toLowerCase();
        if (!contactType.equals("email") && !contactType.equals("webhook")) {
            return EvaluationOutcome.fail("Contact type must be 'email' or 'webhook'", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isEmpty()) {
            return EvaluationOutcome.fail("Contact details are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (contactType.equals("email") && !EMAIL_PATTERN.matcher(subscriber.getContactDetails()).matches()) {
            return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (contactType.equals("webhook") && !URL_PATTERN.matcher(subscriber.getContactDetails()).matches()) {
            return EvaluationOutcome.fail("Invalid webhook URL format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
