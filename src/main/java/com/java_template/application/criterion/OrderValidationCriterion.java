package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
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
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    @Autowired
    private EntityService entityService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[1-9]\\d{1,14}$|^[+]?[1-9]\\d{0,3}[-\\s]?\\d{3}[-\\s]?\\d{3}[-\\s]?\\d{4}$"
    );

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order entity = context.entity();

        // Customer name validation
        if (entity.getCustomerName() == null || entity.getCustomerName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Customer name is required and must be 1-100 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCustomerName().length() < 1 || entity.getCustomerName().length() > 100) {
            return EvaluationOutcome.fail("Customer name is required and must be 1-100 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Customer email validation
        if (entity.getCustomerEmail() == null || entity.getCustomerEmail().trim().isEmpty()) {
            return EvaluationOutcome.fail("Customer email is required and must be valid format", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!EMAIL_PATTERN.matcher(entity.getCustomerEmail()).matches()) {
            return EvaluationOutcome.fail("Customer email is required and must be valid format", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Customer phone validation
        if (entity.getCustomerPhone() == null || entity.getCustomerPhone().trim().isEmpty()) {
            return EvaluationOutcome.fail("Customer phone is required and must be valid format", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (!PHONE_PATTERN.matcher(entity.getCustomerPhone().replaceAll("[-\\s]", "")).matches()) {
            return EvaluationOutcome.fail("Customer phone is required and must be valid format", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Customer address validation
        if (entity.getCustomerAddress() == null || entity.getCustomerAddress().trim().isEmpty()) {
            return EvaluationOutcome.fail("Customer address is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Pet validation
        if (entity.getPetId() == null) {
            return EvaluationOutcome.fail("Order must reference an existing pet", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            EntityResponse<Pet> petResponse = entityService.getItem(
                UUID.fromString(entity.getPetId().toString()), 
                Pet.class
            );
            String petState = petResponse.getMetadata().getState();
            
            if (!"reserved".equals(petState)) {
                return EvaluationOutcome.fail("Referenced pet must be reserved", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Order must reference an existing pet", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Quantity validation
        if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
            return EvaluationOutcome.fail("Order quantity must be positive", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Total amount validation
        if (entity.getTotalAmount() == null || entity.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.fail("Order total amount must be positive", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Payment method validation
        if (entity.getPaymentMethod() == null || entity.getPaymentMethod().trim().isEmpty()) {
            return EvaluationOutcome.fail("Payment method is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Order date validation
        if (entity.getOrderDate() != null && entity.getOrderDate().isAfter(LocalDateTime.now())) {
            return EvaluationOutcome.fail("Order date cannot be in the future", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
