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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class ProductArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductArchiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking product archive criteria for request: {}", request.getId());
        
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
        Product entity = context.entity();
        logger.info("Checking archive criteria for product: {}", entity.getName());

        LocalDateTime now = LocalDateTime.now();

        // Check if product has been in 'analyzed' state for > 30 days
        if (entity.getUpdatedAt() != null) {
            long daysSinceUpdate = ChronoUnit.DAYS.between(entity.getUpdatedAt(), now);
            if (daysSinceUpdate > 30) {
                logger.info("Product {} has been in analyzed state for {} days, eligible for archival", 
                           entity.getName(), daysSinceUpdate);
                return EvaluationOutcome.success();
            }
        }

        // Check if product has zero stock for > 90 days
        if (entity.getStockQuantity() != null && entity.getStockQuantity() == 0) {
            if (entity.getUpdatedAt() != null) {
                long daysSinceZeroStock = ChronoUnit.DAYS.between(entity.getUpdatedAt(), now);
                if (daysSinceZeroStock > 90) {
                    logger.info("Product {} has zero stock for {} days, eligible for archival", 
                               entity.getName(), daysSinceZeroStock);
                    return EvaluationOutcome.success();
                }
            }
        }

        // Check if product has no sales in last 180 days
        if (entity.getLastSaleDate() != null) {
            long daysSinceLastSale = ChronoUnit.DAYS.between(entity.getLastSaleDate(), now);
            if (daysSinceLastSale > 180) {
                logger.info("Product {} has no sales for {} days, eligible for archival", 
                           entity.getName(), daysSinceLastSale);
                return EvaluationOutcome.success();
            }
        } else {
            // No sales recorded at all
            if (entity.getCreatedAt() != null) {
                long daysSinceCreation = ChronoUnit.DAYS.between(entity.getCreatedAt(), now);
                if (daysSinceCreation > 180) {
                    logger.info("Product {} has never had sales and was created {} days ago, eligible for archival", 
                               entity.getName(), daysSinceCreation);
                    return EvaluationOutcome.success();
                }
            }
        }

        // Check business constraints - product should remain active
        logger.info("Product {} does not meet archival criteria, should remain active", entity.getName());
        return EvaluationOutcome.fail("Product should remain active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
