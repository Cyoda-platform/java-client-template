package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class CartAbandonCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    /**
     * Threshold in minutes after which an Active cart is considered abandoned.
     * Chosen as 30 minutes per common e-commerce inactivity heuristics.
     */
    private static final long ABANDON_THRESHOLD_MINUTES = 30L;

    public CartAbandonCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
         Cart cart = context.entity();

         if (cart == null) {
             logger.warn("Cart entity is null in CartAbandonCriterion");
             return EvaluationOutcome.fail("Cart entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure status is present
         if (cart.getStatus() == null || cart.getStatus().isBlank()) {
             logger.warn("Cart {} has missing status", cart.getId());
             return EvaluationOutcome.fail("Cart status is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Only Active carts are eligible for abandonment evaluation
         if (!"Active".equalsIgnoreCase(cart.getStatus())) {
             // Not an active cart -> nothing to do (criterion passes)
             return EvaluationOutcome.success();
         }

         // createdAt is required to compute inactivity
         if (cart.getCreatedAt() == null || cart.getCreatedAt().isBlank()) {
             logger.warn("Cart {} has missing createdAt", cart.getId());
             return EvaluationOutcome.fail("Cart createdAt timestamp is required to evaluate abandonment", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         OffsetDateTime createdAt;
         try {
             createdAt = OffsetDateTime.parse(cart.getCreatedAt());
         } catch (DateTimeParseException ex) {
             logger.warn("Cart {} has invalid createdAt format: {}", cart.getId(), cart.getCreatedAt());
             return EvaluationOutcome.fail("Invalid createdAt format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Duration age = Duration.between(createdAt.toInstant(), OffsetDateTime.now().toInstant());
         long minutes = age.toMinutes();

         if (minutes >= ABANDON_THRESHOLD_MINUTES) {
             String msg = String.format("Cart inactive for %d minutes (threshold %d)", minutes, ABANDON_THRESHOLD_MINUTES);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}