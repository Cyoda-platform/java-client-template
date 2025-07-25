package com.java_template.application.criterion;

import com.java_template.application.entity.ProductPerformanceJob;
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
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ValidationFailureCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(ProductPerformanceJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ValidationFailureCriterion".equals(modelSpec.operationName()) &&
               "productPerformanceJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ProductPerformanceJob entity) {
        // Failure logic: The failure criterion should fail if the scheduledDay is not MONDAY or emailRecipient is invalid
        if (entity.getScheduledDay() == null || !"MONDAY".equalsIgnoreCase(entity.getScheduledDay())) {
            return EvaluationOutcome.success(); // The success criterion covers the positive case
        }
        if (entity.getEmailRecipient() == null || entity.getEmailRecipient().isBlank() || !entity.getEmailRecipient().contains("@")) {
            return EvaluationOutcome.success(); // The success criterion covers the positive case
        }
        // If all validations pass, this criterion fails (meaning validation failure is false)
        return EvaluationOutcome.fail("Validation failure criterion triggered unexpectedly", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
