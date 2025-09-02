package com.java_template.application.criterion;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StockSufficientCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public StockSufficientCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating StockSufficientCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateStockSufficiency)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateStockSufficiency(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Validating stock sufficiency for order: {}", order != null ? order.getOrderId() : "null");

        if (order == null) {
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (order.getLines() == null || order.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Order has no lines to validate", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Calculate total required stock per SKU across all lines
        Map<String, Integer> totalRequiredBySku = new HashMap<>();
        for (Order.OrderLine line : order.getLines()) {
            String sku = line.getSku();
            Integer qty = line.getQty();
            
            if (sku == null || sku.trim().isEmpty()) {
                return EvaluationOutcome.fail("Order line has invalid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            
            if (qty == null || qty <= 0) {
                return EvaluationOutcome.fail("Order line has invalid quantity for SKU: " + sku, StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            
            totalRequiredBySku.merge(sku, qty, Integer::sum);
        }

        try {
            // For each unique SKU, validate stock availability
            for (Map.Entry<String, Integer> entry : totalRequiredBySku.entrySet()) {
                String sku = entry.getKey();
                Integer totalRequired = entry.getValue();
                
                // Get product by SKU
                EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, sku);
                Product product = productResponse.getData();
                
                if (product == null) {
                    return EvaluationOutcome.fail("Product " + sku + " not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }

                // Check product.quantityAvailable >= totalRequired
                if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < totalRequired) {
                    return EvaluationOutcome.fail("Insufficient stock for product " + sku + 
                        ": required " + totalRequired + ", available " + product.getQuantityAvailable(), 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
                
                logger.info("Stock validation passed for SKU {}: required={}, available={}", 
                    sku, totalRequired, product.getQuantityAvailable());
            }

            logger.info("Stock sufficiency validation passed for order: {} with {} unique SKUs", 
                order.getOrderId(), totalRequiredBySku.size());

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Failed to validate stock sufficiency: {}", e.getMessage());
            return EvaluationOutcome.fail("Stock validation failed for multiple products: " + e.getMessage(), 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
