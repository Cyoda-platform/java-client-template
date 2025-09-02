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
public class ProductValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProduct)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateProduct(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entity();

        if (product == null) {
            return EvaluationOutcome.fail("Product entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            return EvaluationOutcome.fail("Product SKU is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getPrice() == null || product.getPrice() <= 0) {
            return EvaluationOutcome.fail("Product price must be greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            return EvaluationOutcome.fail("Product category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            return EvaluationOutcome.fail("Product quantity cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
