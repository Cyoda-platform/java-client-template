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

import java.util.Map;

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
        logger.info("Evaluating ProductAvailabilityCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProductAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateProductAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entity();

        logger.info("Validating product availability for product: {}", product != null ? product.getSku() : "null");

        if (product == null) {
            return EvaluationOutcome.fail("Product not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Extract requested quantity from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        Integer requestedQty = 1; // TODO: Extract from request payload

        // Check product.quantityAvailable >= 0 (no negative stock)
        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            return EvaluationOutcome.fail("Product has negative stock: " + product.getQuantityAvailable(), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check product.quantityAvailable >= requestedQty
        if (product.getQuantityAvailable() < requestedQty) {
            return EvaluationOutcome.fail("Insufficient stock available: requested " + requestedQty + 
                ", available " + product.getQuantityAvailable(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.info("Product availability validation passed: sku={}, requested={}, available={}", 
            product.getSku(), requestedQty, product.getQuantityAvailable());

        return EvaluationOutcome.success();
    }
}
