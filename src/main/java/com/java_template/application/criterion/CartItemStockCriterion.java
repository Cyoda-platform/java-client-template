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
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CartItemStockCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public CartItemStockCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking cart item stock criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Cart.class, this::validateCartItemStock)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateCartItemStock(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
        Cart cart = context.entity();
        
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            return EvaluationOutcome.success(); // Empty cart is valid for stock check
        }
        
        for (Cart.CartLine line : cart.getLines()) {
            Product product = getProductBySku(line.getSku());
            if (product == null) {
                return EvaluationOutcome.fail("Product not found: " + line.getSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            
            if (product.getQuantityAvailable() < line.getQty()) {
                return EvaluationOutcome.fail("Insufficient stock for " + line.getSku(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }
        
        return EvaluationOutcome.success();
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(java.util.List.of(skuCondition));
            
            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, 
                Product.ENTITY_NAME, 
                Product.ENTITY_VERSION, 
                condition, 
                true
            );
            
            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching product by SKU: {}", sku, e);
            return null;
        }
    }
}
