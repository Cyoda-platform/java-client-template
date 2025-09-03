package com.java_template.application.criterion;

import com.java_template.application.entity.product.version_1.Product;
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

import java.math.BigDecimal;

@Component
public class ProductAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking product availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product entity = context.entity();
        logger.info("Validating product availability for: {}", entity.getName());

        // Check product data completeness
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getId() == null || entity.getId() <= 0) {
            return EvaluationOutcome.fail("Product ID must be valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getCategory() == null || entity.getCategory().trim().isEmpty()) {
            return EvaluationOutcome.fail("Product category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
            return EvaluationOutcome.fail("Product must have at least one photo URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check business rules
        if (entity.getStockQuantity() == null) {
            logger.warn("Stock quantity is null for product {}, will be set to 0", entity.getName());
        }

        if (entity.getPrice() == null || entity.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Price is invalid for product {}, will be calculated from category average", entity.getName());
        }

        // All checks passed
        logger.info("Product availability validation passed for: {}", entity.getName());
        return EvaluationOutcome.success();
    }
}
