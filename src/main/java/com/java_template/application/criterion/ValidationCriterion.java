package com.java_template.application.criterion;

import com.java_template.application.entity.EmailNotification;
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
public class ValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(EmailNotification.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ValidationCriterion".equals(modelSpec.operationName()) &&
               "emailNotification".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(EmailNotification entity) {
        // Validate subscriberEmail format and notificationDate presence
        if (entity.getSubscriberEmail() == null || entity.getSubscriberEmail().isBlank()) {
            return EvaluationOutcome.fail("subscriberEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Basic email format check
        if (!entity.getSubscriberEmail().matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$")) {
            return EvaluationOutcome.fail("subscriberEmail format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getNotificationDate() == null || entity.getNotificationDate().isBlank()) {
            return EvaluationOutcome.fail("notificationDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Optional: could add date format validation here
        return EvaluationOutcome.success();
    }
}
