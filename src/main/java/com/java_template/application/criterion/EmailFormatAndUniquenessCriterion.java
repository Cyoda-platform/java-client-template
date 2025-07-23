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

import java.util.regex.Pattern;

@Component
public class EmailFormatAndUniquenessCriterion implements CyodaCriterion {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    
    public EmailFormatAndUniquenessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("EmailFormatAndUniquenessCriterion initialized with SerializerFactory");
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", Pattern.CASE_INSENSITIVE);

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
        return "EmailFormatAndUniquenessCriterion".equals(modelSpec.operationName()) &&
               "subscriber".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Subscriber subscriber) {
        if (subscriber.getEmail() == null || subscriber.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!EMAIL_PATTERN.matcher(subscriber.getEmail()).matches()) {
            return EvaluationOutcome.fail("Email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Uniqueness check should be done externally, placeholder comment here
        // e.g., check in database if email already exists and is ACTIVE
        // For demonstration, simulate success (no duplicates)

        return EvaluationOutcome.success();
    }
}
