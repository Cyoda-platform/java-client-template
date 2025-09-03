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

@Component
public class ProductHasStockCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductHasStockCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking product has stock for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProductHasStock)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateProductHasStock(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entity();
        EntityCriteriaCalculationRequest request = context.request();

        logger.debug("Validating product has stock: {}", product != null ? product.getSku() : "null");

        // CRITICAL: Use product getters directly - never extract from payload
        
        // 1. Validate product entity exists
        if (product == null) {
            logger.warn("Product entity is null");
            return EvaluationOutcome.fail("Product is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 2. Check product.quantityAvailable is not null
        if (product.getQuantityAvailable() == null) {
            logger.warn("Product {} has null quantity available", product.getSku());
            return EvaluationOutcome.fail("Product quantity available is null", 
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // 3. Check product.quantityAvailable >= 0 (no negative stock)
        if (product.getQuantityAvailable() < 0) {
            logger.warn("Product {} has negative stock: {}", product.getSku(), product.getQuantityAvailable());
            return EvaluationOutcome.fail("Product has negative stock: " + product.getQuantityAvailable(), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // For this criterion, we need to get the required quantity from somewhere
        // In a real implementation, this would come from the request context or payload
        // For now, we'll extract it from the request payload if available
        Integer requiredQuantity = extractRequiredQuantity(request);
        
        if (requiredQuantity == null) {
            // If no required quantity specified, just check that stock is positive
            if (product.getQuantityAvailable() == 0) {
                logger.warn("Product {} is out of stock", product.getSku());
                return EvaluationOutcome.fail("Product is out of stock", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } else {
            // 4. Validate product.quantityAvailable >= required quantity
            if (product.getQuantityAvailable() < requiredQuantity) {
                logger.warn("Product {} has insufficient stock: available={}, required={}", 
                    product.getSku(), product.getQuantityAvailable(), requiredQuantity);
                return EvaluationOutcome.fail(
                    String.format("Insufficient stock: available=%d, required=%d", 
                        product.getQuantityAvailable(), requiredQuantity), 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Additional validation for product completeness
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            logger.warn("Product has missing SKU");
            return EvaluationOutcome.fail("Product SKU is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (product.getName() == null || product.getName().trim().isEmpty()) {
            logger.warn("Product {} has missing name", product.getSku());
            return EvaluationOutcome.fail("Product name is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (product.getPrice() == null || product.getPrice() < 0) {
            logger.warn("Product {} has invalid price: {}", product.getSku(), product.getPrice());
            return EvaluationOutcome.fail("Product has invalid price", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Product {} stock validation passed: available={}, required={}", 
            product.getSku(), product.getQuantityAvailable(), 
            requiredQuantity != null ? requiredQuantity : "not specified");
        
        return EvaluationOutcome.success();
    }
    
    private Integer extractRequiredQuantity(EntityCriteriaCalculationRequest request) {
        // In a real implementation, this would extract the required quantity from the request
        // This could come from the payload, request parameters, or workflow context
        // For now, we'll return null to indicate no specific quantity requirement
        
        try {
            if (request.getPayload() != null && request.getPayload().getData() != null) {
                // Try to extract from payload if it contains quantity information
                // This is a simplified approach - in practice, the structure would be well-defined
                Object data = request.getPayload().getData();
                if (data instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
                    Object qty = dataMap.get("requiredQuantity");
                    if (qty instanceof Number) {
                        return ((Number) qty).intValue();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract required quantity from request: {}", e.getMessage());
        }
        
        return null; // No specific quantity requirement
    }
}
