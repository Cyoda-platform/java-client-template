package com.java_template.application.criterion;

import com.java_template.application.entity.ProductData;
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
public class IsProductDataValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsProductDataValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsProductDataValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(ProductData.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsProductDataValidCriterion".equals(modelSpec.operationName()) &&
               "productData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ProductData entity) {
        // Validate productId is not null or blank
        if (entity.getProductId() == null || entity.getProductId().isBlank()) {
            return EvaluationOutcome.fail("Product ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate salesVolume is not null and >= 0
        if (entity.getSalesVolume() == null || entity.getSalesVolume() < 0) {
            return EvaluationOutcome.fail("Sales volume must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate revenue is not null and >= 0
        if (entity.getRevenue() == null || entity.getRevenue() < 0) {
            return EvaluationOutcome.fail("Revenue must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate inventoryCount is not null and >= 0
        if (entity.getInventoryCount() == null || entity.getInventoryCount() < 0) {
            return EvaluationOutcome.fail("Inventory count must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
