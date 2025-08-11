package com.java_template.application.criterion;

import com.java_template.application.entity.Subscriber.version_1.Subscriber;
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
public class CheckSubscriberCriteria implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(http|https)://.*$");

    public CheckSubscriberCriteria(SerializerFactory serializerFactory) {
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
        if (subscriber.getContactType() == null || subscriber.getContactType().trim().isEmpty()) {
            return EvaluationOutcome.fail("ContactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (subscriber.getContactDetails() == null || subscriber.getContactDetails().trim().isEmpty()) {
            return EvaluationOutcome.fail("ContactDetails is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String type = subscriber.getContactType().toLowerCase();
        String details = subscriber.getContactDetails();
        if ("email" .equals(type)) {
            if (!EMAIL_PATTERN.matcher(details).matches()) {
                return EvaluationOutcome.fail("ContactDetails is not a valid email", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else if ("webhook" .equals(type)) {
            if (!URL_PATTERN.matcher(details).matches()) {
                return EvaluationOutcome.fail("ContactDetails is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else {
            return EvaluationOutcome.fail("Unsupported contactType", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
