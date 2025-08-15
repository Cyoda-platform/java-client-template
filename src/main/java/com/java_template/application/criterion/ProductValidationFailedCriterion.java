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
public class ProductValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductValidationFailedCriterion(SerializerFactory serializerFactory) {
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
        Product p = context.entity();
        if (p == null) return EvaluationOutcome.fail("Product missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (p.getSku() == null || p.getSku().trim().isEmpty()) return EvaluationOutcome.fail("SKU missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (p.getPrice() == null || p.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) return EvaluationOutcome.fail("Invalid price", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (p.getStockQuantity() == null || p.getStockQuantity() < 0) return EvaluationOutcome.fail("Invalid stock quantity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        return EvaluationOutcome.fail("Product validation failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}