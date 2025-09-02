package com.java_template.application.criterion;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
public class PaymentReadyForApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public PaymentReadyForApprovalCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Payment.class, this::validatePaymentReadyForApproval)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validatePaymentReadyForApproval(CriterionSerializer.CriterionEntityEvaluationContext<Payment> context) {
        Payment payment = context.entity();

        if (payment == null) {
            return EvaluationOutcome.fail("Payment not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if payment is in INITIATED state
        String paymentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"INITIATED".equals(paymentState)) {
            return EvaluationOutcome.fail("Payment is not in initiated state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if at least 3 seconds have passed since creation
        if (payment.getCreatedAt() != null) {
            Instant currentTime = Instant.now();
            long secondsPassed = ChronoUnit.SECONDS.between(payment.getCreatedAt(), currentTime);
            if (secondsPassed < 3) {
                return EvaluationOutcome.fail("Payment approval time not reached", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Check if associated cart exists and is valid
        Optional<EntityResponse<Cart>> cartResponse = getCartById(payment.getCartId());
        if (cartResponse.isEmpty()) {
            return EvaluationOutcome.fail("Associated cart not found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        String cartState = cartResponse.get().getMetadata().getState();
        if (!"CHECKING_OUT".equals(cartState)) {
            return EvaluationOutcome.fail("Associated cart is not in checkout state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private Optional<EntityResponse<Cart>> getCartById(String cartId) {
        try {
            Condition condition = Condition.of("$.cartId", "EQUALS", cartId);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
            return entityService.getFirstItemByCondition(Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, searchCondition, true);
        } catch (Exception e) {
            logger.error("Error retrieving cart by ID: {}", cartId, e);
            return Optional.empty();
        }
    }
}
