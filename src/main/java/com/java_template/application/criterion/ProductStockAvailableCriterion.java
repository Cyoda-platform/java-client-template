```java
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
public class ProductStockAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductStockAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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
        Product product = context.entity();

        // Get requested quantity from input parameters if available
        Integer requestedQty = (Integer) context.getInputData().get("requestedQty");
        if (requestedQty == null) {
            requestedQty = 1; // Default to 1 if not specified
        }

        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            return EvaluationOutcome.fail("Product has negative stock", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getQuantityAvailable() < requestedQty) {
            return EvaluationOutcome.fail(
                String.format("Insufficient stock. Available: %d, Requested: %d",
                    product.getQuantityAvailable(), requestedQty),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        return EvaluationOutcome.success();
    }
}
```