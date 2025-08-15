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
public class ProductValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("{} invoked for request {}", className, request.getId());

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
        if (product == null) {
            return EvaluationOutcome.fail("Product payload missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getId() == null || product.getId().isBlank()) {
            return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getName() == null || product.getName().isBlank()) {
            return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getPrice() == null) {
            return EvaluationOutcome.fail("price is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            return EvaluationOutcome.fail("price must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getCurrency() == null || product.getCurrency().isBlank()) {
            return EvaluationOutcome.fail("currency is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getStockQuantity() == null) {
            return EvaluationOutcome.fail("stockQuantity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (product.getStockQuantity() < 0) {
            return EvaluationOutcome.fail("stockQuantity must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
