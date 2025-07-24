package com.java_template.application.criterion;

import com.java_template.application.entity.Order;
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
public class JobParametersValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public JobParametersValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("JobParametersValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobParametersValidCriterion".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Order entity) {
        // Validate that orderId, petId, quantity, status, and complete are set and quantity > 0
        if (entity.getOrderId() == null) {
            return EvaluationOutcome.fail("orderId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getPetId() == null) {
            return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
            return EvaluationOutcome.fail("quantity must be greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getComplete() == null) {
            return EvaluationOutcome.fail("complete flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
