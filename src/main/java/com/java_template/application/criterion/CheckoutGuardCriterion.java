package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.reservation.version_1.Reservation;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class CheckoutGuardCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckoutGuardCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        String opName = modelSpec == null ? null : modelSpec.operationName();
        return className.equals(opName);
    }

    @SuppressWarnings("unchecked")
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
         Cart cart = context.entity();

         // Guard 1: Cart must have items
         if (cart.getItems() == null || cart.getItems().isEmpty()) {
             return EvaluationOutcome.fail("Cart is empty and cannot be checked out", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Guard 2: All reservations for this cart must be ACTIVE
         List<Reservation> reservations;
         try {
             // Attempt to call context.relatedEntities(Reservation.class) via reflection if available
             Method relatedMethod = context.getClass().getMethod("relatedEntities", Class.class);
             Object result = relatedMethod.invoke(context, Reservation.class);
             if (result instanceof List) {
                 reservations = (List<Reservation>) result;
             } else {
                 reservations = null;
             }
         } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
             // Fallback to a second possible method name: fetchEntities(Class)
             try {
                 Method fetchMethod = context.getClass().getMethod("fetchEntities", Class.class);
                 Object result = fetchMethod.invoke(context, Reservation.class);
                 if (result instanceof List) {
                     reservations = (List<Reservation>) result;
                 } else {
                     reservations = null;
                 }
             } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                 logger.debug("Unable to load related reservations for cart {}: {}", cart == null ? "<null>" : cart.getId(), ex.getMessage());
                 return EvaluationOutcome.fail("Could not validate reservations for cart", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if (reservations == null || reservations.isEmpty()) {
             return EvaluationOutcome.fail("No reservations found for cart; cannot proceed to checkout", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<Reservation> nonMatching = reservations.stream()
                 .filter(Objects::nonNull)
                 .filter(r -> cart.getId() != null && cart.getId().equals(r.getCartId()))
                 .filter(r -> r.getStatus() == null || !"ACTIVE".equalsIgnoreCase(r.getStatus()))
                 .collect(Collectors.toList());

         if (!nonMatching.isEmpty()) {
             String ids = nonMatching.stream()
                     .map(Reservation::getId)
                     .filter(Objects::nonNull)
                     .collect(Collectors.joining(","));
             String msg = "Found reservations not ACTIVE for cart: " + (ids == null || ids.isBlank() ? "unknown-ids" : ids);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}