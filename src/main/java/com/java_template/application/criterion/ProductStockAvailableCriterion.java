package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProductStockAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    @Autowired
    private EntityService entityService;

    public ProductStockAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking product stock available criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateProductStockAvailable)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateProductStockAvailable(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating product stock availability for cart: {}", cart.getCartId());

        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            logger.info("Cart has no lines to validate: {}", cart.getCartId());
            return EvaluationOutcome.success();
        }

        try {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getQty() == null || line.getQty() <= 0) {
                    continue; // Skip invalid lines
                }

                // Get product by SKU
                Product product = getProductBySku(line.getSku());
                if (product == null) {
                    logger.warn("Product not found: {}", line.getSku());
                    return EvaluationOutcome.fail("Product not found: " + line.getSku(), 
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }

                // Check stock availability
                if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < line.getQty()) {
                    logger.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}", 
                               line.getSku(), product.getQuantityAvailable(), line.getQty());
                    return EvaluationOutcome.fail(
                        String.format("Insufficient stock for %s. Available: %d, Requested: %d", 
                                    line.getSku(), 
                                    product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0, 
                                    line.getQty()),
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }

                logger.debug("Stock check passed for SKU: {}, Available: {}, Requested: {}", 
                           line.getSku(), product.getQuantityAvailable(), line.getQty());
            }

            logger.info("All product stock validations passed for cart: {}", cart.getCartId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error validating product stock for cart: {}", cart.getCartId(), e);
            return EvaluationOutcome.fail("Error validating product stock: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
            
            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving product by SKU: {}", sku, e);
            return null;
        }
    }
}
