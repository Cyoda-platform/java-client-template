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
public class ProductReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Product.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
         Product entity = context.entity(); // Product is the subject of this criterion.

         if (entity == null) {
             logger.warn("ProductReadyCriterion: received null entity in evaluation context");
             return EvaluationOutcome.fail("Product entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identity and descriptive fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             logger.warn("ProductReadyCriterion: product missing id");
             return EvaluationOutcome.fail("Product id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             logger.warn("ProductReadyCriterion: product {} missing name", entity.getId());
             return EvaluationOutcome.fail("Product name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSku() == null || entity.getSku().isBlank()) {
             logger.warn("ProductReadyCriterion: product {} missing sku", entity.getId());
             return EvaluationOutcome.fail("Product SKU is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCurrency() == null || entity.getCurrency().isBlank()) {
             logger.warn("ProductReadyCriterion: product {} missing currency", entity.getId());
             return EvaluationOutcome.fail("Currency is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric validations
         if (entity.getPrice() == null) {
             logger.warn("ProductReadyCriterion: product {} missing price", entity.getId());
             return EvaluationOutcome.fail("Price must be specified", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrice() < 0.0) {
             logger.warn("ProductReadyCriterion: product {} has negative price {}", entity.getId(), entity.getPrice());
             return EvaluationOutcome.fail("Price must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getStock() == null) {
             logger.warn("ProductReadyCriterion: product {} missing stock", entity.getId());
             return EvaluationOutcome.fail("Stock must be specified", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStock() < 0) {
             logger.warn("ProductReadyCriterion: product {} has negative stock {}", entity.getId(), entity.getStock());
             return EvaluationOutcome.fail("Stock must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Availability/business rules
         if (entity.getAvailable() == null) {
             logger.warn("ProductReadyCriterion: product {} missing available flag", entity.getId());
             return EvaluationOutcome.fail("Available flag must be specified", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!entity.getAvailable()) {
             logger.info("ProductReadyCriterion: product {} marked not available", entity.getId());
             return EvaluationOutcome.fail("Product is not available for sale/indexing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If stock is zero -> not ready (business rule)
         if (entity.getStock() != null && entity.getStock() <= 0) {
             logger.info("ProductReadyCriterion: product {} has non-positive stock ({})", entity.getId(), entity.getStock());
             return EvaluationOutcome.fail("Product out of stock", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         logger.debug("ProductReadyCriterion: product {} passed readiness checks", entity.getId());
         return EvaluationOutcome.success();
    }
}