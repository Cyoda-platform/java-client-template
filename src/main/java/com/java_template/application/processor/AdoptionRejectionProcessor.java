package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
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
 * AdoptionRejectionProcessor - Handle adoption rejection
 */
@Component
public class AdoptionRejectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRejectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AdoptionRejectionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption rejection for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Adoption.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adoption entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Adoption> entityWithMetadata) {
        Adoption entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Handle adoption rejection
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing rejection for adoption: {} in state: {}", adoption.getAdoptionId(), currentState);

        // Verify adoption is in under_review state
        if (!"under_review".equals(currentState)) {
            logger.warn("Adoption {} is not in under_review state, current state: {}", adoption.getAdoptionId(), currentState);
        }

        // Record rejection reason in staff notes
        String rejectionNote = "Application rejected on " + LocalDateTime.now();
        if (adoption.getStaffNotes() != null && !adoption.getStaffNotes().trim().isEmpty()) {
            adoption.setStaffNotes(adoption.getStaffNotes() + " | " + rejectionNote);
        } else {
            adoption.setStaffNotes(rejectionNote);
        }

        // Release pet reservation by canceling the reservation
        releasePetReservation(adoption.getPetId());

        // Send rejection notice to owner (simulated)
        logger.info("Rejection notice sent for adoption: {}", adoption.getAdoptionId());

        logger.info("Adoption rejection {} processed successfully", adoption.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Release pet reservation by transitioning it back to available state
     */
    private void releasePetReservation(String petId) {
        try {
            // Find pet by business ID
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petWithMetadata = entityService.findByBusinessId(
                    petModelSpec, petId, "petId", Pet.class);

            if (petWithMetadata != null) {
                Pet pet = petWithMetadata.entity();
                // Update pet with cancel_reservation transition
                entityService.update(petWithMetadata.metadata().getId(), pet, "cancel_reservation");
                logger.info("Pet {} reservation cancelled", petId);
            } else {
                logger.warn("Pet {} not found for reservation cancellation", petId);
            }
        } catch (Exception e) {
            logger.error("Error cancelling pet reservation for {}: {}", petId, e.getMessage());
        }
    }
}
