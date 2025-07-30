package com.java_template.application.criterion;

import com.java_template.application.entity.Workflow;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Component
public class ValidateWorkflow implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateWorkflow(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Workflow.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Workflow> context) {
        Workflow workflow = context.entity();

        // Validate URL format
        String url = workflow.getUrl();
        if (url == null || url.isBlank()) {
            return EvaluationOutcome.fail("URL is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            return EvaluationOutcome.fail("URL format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate subscribers list - must not be null and all emails must contain '@'
        List<String> subscribers = workflow.getSubscribers();
        if (subscribers == null || subscribers.isEmpty()) {
            return EvaluationOutcome.fail("Subscribers list must not be empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        for (String email : subscribers) {
            if (email == null || !email.contains("@")) {
                return EvaluationOutcome.fail("Subscriber email is invalid: " + email, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Status validation - only allow PENDING or PROCESSING as valid for validation
        String status = workflow.getStatus();
        if (status != null && !(status.equals("PENDING") || status.equals("PROCESSING") || status.equals("COMPLETED"))) {
            return EvaluationOutcome.fail("Status is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
