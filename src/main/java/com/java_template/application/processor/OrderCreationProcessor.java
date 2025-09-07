package com.java_template.application.processor;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OrderCreationProcessor - Handles order creation business logic
 * 
 * Creates a new pet care service order, including:
 * - Validating order data completeness
 * - Verifying pet and owner eligibility
 * - Generating unique orderId
 * - Setting order metadata
 */
@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    
    // Simple counter for generating unique order IDs
    private static final AtomicLong orderIdCounter = new AtomicLong(1);

    public OrderCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(PetCareOrder.class)
            .validate(this::isValidEntityWithMetadata, "Invalid order creation data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetCareOrder> entityWithMetadata) {
        PetCareOrder order = entityWithMetadata.entity();
        return order != null && 
               order.getPetId() != null && !order.getPetId().trim().isEmpty() &&
               order.getOwnerId() != null && !order.getOwnerId().trim().isEmpty() &&
               order.getServiceType() != null && !order.getServiceType().trim().isEmpty() &&
               order.getScheduledDate() != null &&
               order.getDuration() != null && order.getDuration() > 0 &&
               order.getCost() != null && order.getCost() >= 0 &&
               order.getPaymentMethod() != null && !order.getPaymentMethod().trim().isEmpty() &&
               order.getScheduledDate().isAfter(LocalDateTime.now());
    }

    private EntityWithMetadata<PetCareOrder> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetCareOrder> context) {
        
        EntityWithMetadata<PetCareOrder> entityWithMetadata = context.entityResponse();
        PetCareOrder order = entityWithMetadata.entity();

        logger.debug("Processing order creation for pet: {} and owner: {}", order.getPetId(), order.getOwnerId());

        // 1. Verify pet and owner eligibility
        verifyPetAndOwnerEligibility(order.getPetId(), order.getOwnerId());

        // 2. Generate unique orderId
        String orderId = generateUniqueOrderId();
        order.setOrderId(orderId);

        // 3. Set order metadata
        order.setOrderDate(LocalDateTime.now());

        logger.info("Order {} created successfully for pet {} and owner {}", orderId, order.getPetId(), order.getOwnerId());

        return entityWithMetadata;
    }

    private void verifyPetAndOwnerEligibility(String petId, String ownerId) {
        // Verify pet exists and is active
        EntityWithMetadata<Pet> petResponse = getPetByBusinessId(petId);
        if (petResponse == null) {
            throw new IllegalArgumentException("Pet not found: " + petId);
        }
        
        String petState = petResponse.getState();
        if (!"ACTIVE".equals(petState)) {
            throw new IllegalArgumentException("Pet is not active: " + petId + " (state: " + petState + ")");
        }

        // Verify owner exists and is active
        EntityWithMetadata<Owner> ownerResponse = getOwnerByBusinessId(ownerId);
        if (ownerResponse == null) {
            throw new IllegalArgumentException("Owner not found: " + ownerId);
        }
        
        String ownerState = ownerResponse.getState();
        if (!"ACTIVE".equals(ownerState)) {
            throw new IllegalArgumentException("Owner is not active: " + ownerId + " (state: " + ownerState + ")");
        }

        // Verify pet belongs to the specified owner
        Pet pet = petResponse.entity();
        if (!ownerId.equals(pet.getOwnerId())) {
            throw new IllegalArgumentException("Pet " + petId + " does not belong to owner " + ownerId);
        }

        logger.debug("Pet {} and owner {} verified as eligible for order", petId, ownerId);
    }

    private EntityWithMetadata<Pet> getPetByBusinessId(String petId) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            return entityService.findByBusinessId(petModelSpec, petId, "petId", Pet.class);
        } catch (Exception e) {
            logger.error("Failed to get pet: {}", petId, e);
            throw new RuntimeException("Pet lookup failed: " + e.getMessage(), e);
        }
    }

    private EntityWithMetadata<Owner> getOwnerByBusinessId(String ownerId) {
        try {
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            return entityService.findByBusinessId(ownerModelSpec, ownerId, "ownerId", Owner.class);
        } catch (Exception e) {
            logger.error("Failed to get owner: {}", ownerId, e);
            throw new RuntimeException("Owner lookup failed: " + e.getMessage(), e);
        }
    }

    private String generateUniqueOrderId() {
        return "ORDER-" + String.format("%03d", orderIdCounter.getAndIncrement());
    }
}
