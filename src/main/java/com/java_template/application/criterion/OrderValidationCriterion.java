package com.java_template.application.criterion;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * OrderValidationCriterion - Determines if a pending order can be automatically confirmed
 * 
 * Validates:
 * - Referenced pet exists and is in ACTIVE state
 * - Referenced owner exists and is in ACTIVE state
 * - Pet belongs to the specified owner
 * - Scheduled date is at least 24 hours in the future
 * - Service type is valid and available
 * - Cost is within reasonable range for service type
 * - Payment method is valid
 */
@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    // Valid service types
    private static final List<String> VALID_SERVICE_TYPES = Arrays.asList(
        "GROOMING", "BOARDING", "VETERINARY", "TRAINING", "DAYCARE"
    );

    // Valid payment methods
    private static final List<String> VALID_PAYMENT_METHODS = Arrays.asList(
        "CREDIT_CARD", "DEBIT_CARD", "CASH", "CHECK", "BANK_TRANSFER"
    );

    public OrderValidationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(PetCareOrder.class, this::validateOrder)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrder(CriterionSerializer.CriterionEntityEvaluationContext<PetCareOrder> context) {
        PetCareOrder order = context.entityWithMetadata().entity();
        
        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 1. Validate entity references
        EvaluationOutcome entityValidation = validateEntityReferences(order);
        if (!entityValidation.isSuccess()) {
            return entityValidation;
        }

        // 2. Check ownership relationship
        EvaluationOutcome ownershipValidation = validateOwnershipRelationship(order);
        if (!ownershipValidation.isSuccess()) {
            return ownershipValidation;
        }

        // 3. Validate scheduling
        EvaluationOutcome schedulingValidation = validateScheduling(order);
        if (!schedulingValidation.isSuccess()) {
            return schedulingValidation;
        }

        // 4. Validate business rules
        EvaluationOutcome businessValidation = validateBusinessRules(order);
        if (!businessValidation.isSuccess()) {
            return businessValidation;
        }

        logger.debug("Order {} passed all validation criteria", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateEntityReferences(PetCareOrder order) {
        try {
            // Get pet by petId
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petResponse = entityService.findByBusinessId(
                petModelSpec, order.getPetId(), "petId", Pet.class);
            
            if (petResponse == null) {
                logger.warn("Pet not found for order {}: {}", order.getOrderId(), order.getPetId());
                return EvaluationOutcome.fail("Referenced pet does not exist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
            String petState = petResponse.getState();
            if (!"ACTIVE".equals(petState)) {
                logger.warn("Pet {} is not active for order {}: state={}", order.getPetId(), order.getOrderId(), petState);
                return EvaluationOutcome.fail("Referenced pet is not in ACTIVE state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Get owner by ownerId
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> ownerResponse = entityService.findByBusinessId(
                ownerModelSpec, order.getOwnerId(), "ownerId", Owner.class);
            
            if (ownerResponse == null) {
                logger.warn("Owner not found for order {}: {}", order.getOrderId(), order.getOwnerId());
                return EvaluationOutcome.fail("Referenced owner does not exist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
            String ownerState = ownerResponse.getState();
            if (!"ACTIVE".equals(ownerState)) {
                logger.warn("Owner {} is not active for order {}: state={}", order.getOwnerId(), order.getOrderId(), ownerState);
                return EvaluationOutcome.fail("Referenced owner is not in ACTIVE state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Failed to validate entity references for order {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Failed to validate entity references", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private EvaluationOutcome validateOwnershipRelationship(PetCareOrder order) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petResponse = entityService.findByBusinessId(
                petModelSpec, order.getPetId(), "petId", Pet.class);
            
            if (petResponse != null) {
                Pet pet = petResponse.entity();
                if (!order.getOwnerId().equals(pet.getOwnerId())) {
                    logger.warn("Pet {} does not belong to owner {} for order {}", 
                               order.getPetId(), order.getOwnerId(), order.getOrderId());
                    return EvaluationOutcome.fail("Pet does not belong to the specified owner", 
                                                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }

            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Failed to validate ownership relationship for order {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Failed to validate ownership relationship", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private EvaluationOutcome validateScheduling(PetCareOrder order) {
        // Check scheduled date is at least 24 hours in the future
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledDate = order.getScheduledDate();
        
        if (scheduledDate == null) {
            logger.warn("Order {} has no scheduled date", order.getOrderId());
            return EvaluationOutcome.fail("Scheduled date is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalDateTime minimumScheduleTime = now.plusHours(24);
        if (scheduledDate.isBefore(minimumScheduleTime)) {
            logger.warn("Order {} scheduled too soon: {} (minimum: {})", 
                       order.getOrderId(), scheduledDate, minimumScheduleTime);
            return EvaluationOutcome.fail("Service must be scheduled at least 24 hours in advance", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify service type is supported
        if (!VALID_SERVICE_TYPES.contains(order.getServiceType())) {
            logger.warn("Order {} has invalid service type: {}", order.getOrderId(), order.getServiceType());
            return EvaluationOutcome.fail("Service type is not supported", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private EvaluationOutcome validateBusinessRules(PetCareOrder order) {
        // Validate cost is within reasonable range
        if (order.getCost() == null || order.getCost() < 0) {
            logger.warn("Order {} has invalid cost: {}", order.getOrderId(), order.getCost());
            return EvaluationOutcome.fail("Cost must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check cost is within reasonable range for service type
        if (!isReasonableCostForService(order.getServiceType(), order.getCost())) {
            logger.warn("Order {} has unreasonable cost {} for service {}", 
                       order.getOrderId(), order.getCost(), order.getServiceType());
            return EvaluationOutcome.fail("Cost is outside reasonable range for service type", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate payment method
        if (!VALID_PAYMENT_METHODS.contains(order.getPaymentMethod())) {
            logger.warn("Order {} has invalid payment method: {}", order.getOrderId(), order.getPaymentMethod());
            return EvaluationOutcome.fail("Payment method is not accepted", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private boolean isReasonableCostForService(String serviceType, Double cost) {
        // Define reasonable cost ranges for each service type
        switch (serviceType) {
            case "GROOMING":
                return cost >= 25.0 && cost <= 200.0;
            case "BOARDING":
                return cost >= 30.0 && cost <= 500.0;
            case "VETERINARY":
                return cost >= 50.0 && cost <= 1000.0;
            case "TRAINING":
                return cost >= 40.0 && cost <= 300.0;
            case "DAYCARE":
                return cost >= 20.0 && cost <= 100.0;
            default:
                return cost >= 10.0 && cost <= 1000.0;
        }
    }
}
