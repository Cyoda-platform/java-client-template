package com.java_template.application.processor;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.application.entity.pet.version_1.Pet;
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

/**
 * ServiceCompletionProcessor - Handles service completion business logic
 * 
 * Marks successful completion of service, including:
 * - Finalizing service details
 * - Updating pet health records if veterinary service
 * - Processing payment confirmation
 */
@Component
public class ServiceCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ServiceCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ServiceCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Service completion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(PetCareOrder.class)
            .validate(this::isValidEntityWithMetadata, "Invalid service completion data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetCareOrder> entityWithMetadata) {
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return order != null && order.isValid() && "IN_PROGRESS".equals(currentState);
    }

    private EntityWithMetadata<PetCareOrder> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetCareOrder> context) {
        
        EntityWithMetadata<PetCareOrder> entityWithMetadata = context.entityResponse();
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing service completion for: {} in state: {}", order.getOrderId(), currentState);

        // 1. Finalize service details
        finalizeServiceDetails(order);

        // 2. Update pet health records if veterinary service
        if ("VETERINARY".equals(order.getServiceType())) {
            updatePetHealthRecords(order);
        }

        // 3. Payment processing is assumed to be handled externally
        logger.info("Service completed successfully for order {}", order.getOrderId());

        return entityWithMetadata;
    }

    private void finalizeServiceDetails(PetCareOrder order) {
        // Set completion date if not already set
        if (order.getCompletionDate() == null) {
            order.setCompletionDate(LocalDateTime.now());
        }

        // Ensure customer rating is within valid range
        if (order.getCustomerRating() != null) {
            if (order.getCustomerRating() < 1 || order.getCustomerRating() > 5) {
                logger.warn("Invalid customer rating {} for order {}, setting to null", 
                           order.getCustomerRating(), order.getOrderId());
                order.setCustomerRating(null);
            }
        }

        // Add completion note if not present
        if (order.getNotes() == null || order.getNotes().trim().isEmpty()) {
            order.setNotes("Service completed successfully");
        }

        logger.debug("Service details finalized for order {}", order.getOrderId());
    }

    private void updatePetHealthRecords(PetCareOrder order) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petResponse = entityService.findByBusinessId(
                petModelSpec, order.getPetId(), "petId", Pet.class);
            
            if (petResponse != null) {
                Pet pet = petResponse.entity();
                
                // Update last checkup date
                pet.setLastCheckupDate(order.getCompletionDate() != null ? 
                                     order.getCompletionDate() : LocalDateTime.now());
                
                // Add health notes if provided in order
                if (order.getNotes() != null && !order.getNotes().trim().isEmpty()) {
                    String existingNotes = pet.getHealthNotes() != null ? pet.getHealthNotes() : "";
                    String newNotes = existingNotes.isEmpty() ? order.getNotes() : 
                                    existingNotes + "; " + order.getNotes();
                    pet.setHealthNotes(newNotes);
                }
                
                // Update pet without transition (loop back to same state)
                entityService.update(petResponse.getId(), pet, null);
                
                logger.debug("Updated health records for pet {} after veterinary service", order.getPetId());
            }
        } catch (Exception e) {
            logger.error("Failed to update pet health records for order: {}", order.getOrderId(), e);
            // Don't fail the entire completion for this
        }
    }
}
