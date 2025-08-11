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

import java.net.MalformedURLException;
import java.net.URL;

@Component
public class SubscriberContactInfoCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberContactInfoCriterion(SerializerFactory serializerFactory) {
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
        if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isEmpty()) {
            return EvaluationOutcome.fail("Contact address is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String type = subscriber.getContactType().toLowerCase();
        if (type.equals("email")) {
            if (!subscriber.getContactAddress().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                return EvaluationOutcome.fail("Invalid email address format", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else if (type.equals("webhook")) {
            try {
                new URL(subscriber.getContactAddress());
            } catch (MalformedURLException e) {
                return EvaluationOutcome.fail("Invalid webhook URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else {
            return EvaluationOutcome.fail("Unknown contact type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}