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
public class StockLevelCheckCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // threshold is configurable; for prototype we use 10 as default
    private static final int DEFAULT_THRESHOLD = 10;

    public StockLevelCheckCriterion(SerializerFactory serializerFactory) {
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
            return EvaluationOutcome.fail("Product missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        int threshold = DEFAULT_THRESHOLD; // Could be read from config
        Integer stock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        Integer reserved = 0;
        try {
            java.lang.reflect.Field f = Product.class.getDeclaredField("reservedQuantity");
            f.setAccessible(true);
            Object val = f.get(product);
            if (val instanceof Integer) reserved = (Integer) val;
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            // reservedQuantity not present in POJO; consider it as 0
        }

        int effective = stock - reserved;
        if (effective < threshold) {
            return EvaluationOutcome.fail("low stock: effective available " + effective, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
