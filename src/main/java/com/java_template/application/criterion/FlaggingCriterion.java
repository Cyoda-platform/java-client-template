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
public class FlaggingCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FlaggingCriterion(SerializerFactory serializerFactory) {
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
        if (p.getStockLevel() != null && p.getReorderPoint() != null && p.getStockLevel() <= p.getReorderPoint()) {
            return EvaluationOutcome.fail("Product requires restocking", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        // Low performance if revenue metric exists and below threshold
        if (p.getMetrics() != null && p.getMetrics().containsKey("revenue")) {
            Object rev = p.getMetrics().get("revenue");
            double revenue = rev instanceof Number ? ((Number) rev).doubleValue() : 0.0;
            if (revenue < 1.0) {
                return EvaluationOutcome.fail("Low revenue", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }
}
