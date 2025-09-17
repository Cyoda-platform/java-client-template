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
 * AdoptionCompletionProcessor - Complete adoption process
 */
@Component
public class AdoptionCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AdoptionCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption completion for request: {}", request.getId());

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
     * Complete adoption process
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Completing adoption: {} in state: {}", adoption.getAdoptionId(), currentState);

        // Verify adoption is in approved state
        if (!"approved".equals(currentState)) {
            logger.warn("Adoption {} is not in approved state, current state: {}", adoption.getAdoptionId(), currentState);
        }

        // Set adoption date to current time
        adoption.setAdoptionDate(LocalDateTime.now());

        // Finalize adoption paperwork (simulated)
        finalizeAdoptionPaperwork(adoption);

        // Process adoption fee payment (simulated)
        processAdoptionFeePayment(adoption);

        // Complete pet adoption by transitioning pet to adopted state
        completePetAdoption(adoption.getPetId());

        // Schedule follow-up visit
        scheduleFollowUpVisit(adoption);

        // Add completion notes
        String completionNote = "Adoption completed on " + adoption.getAdoptionDate();
        if (adoption.getStaffNotes() != null && !adoption.getStaffNotes().trim().isEmpty()) {
            adoption.setStaffNotes(adoption.getStaffNotes() + " | " + completionNote);
        } else {
            adoption.setStaffNotes(completionNote);
        }

        logger.info("Adoption completion {} processed successfully", adoption.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Finalize adoption paperwork
     */
    private void finalizeAdoptionPaperwork(Adoption adoption) {
        // Ensure contract is signed
        adoption.setContractSigned(true);
        logger.info("Adoption paperwork finalized for adoption: {}", adoption.getAdoptionId());
    }

    /**
     * Process adoption fee payment
     */
    private void processAdoptionFeePayment(Adoption adoption) {
        // Simulated payment processing
        if (adoption.getAdoptionFee() != null && adoption.getAdoptionFee() > 0) {
            logger.info("Adoption fee of ${} processed for adoption: {}", adoption.getAdoptionFee(), adoption.getAdoptionId());
        } else {
            logger.info("No adoption fee to process for adoption: {}", adoption.getAdoptionId());
        }
    }

    /**
     * Complete pet adoption by transitioning pet to adopted state
     */
    private void completePetAdoption(String petId) {
        try {
            // Find pet by business ID
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petWithMetadata = entityService.findByBusinessId(
                    petModelSpec, petId, "petId", Pet.class);

            if (petWithMetadata != null) {
                Pet pet = petWithMetadata.entity();
                // Update pet with complete_adoption transition
                entityService.update(petWithMetadata.metadata().getId(), pet, "complete_adoption");
                logger.info("Pet {} adoption completed", petId);
            } else {
                logger.warn("Pet {} not found for adoption completion", petId);
            }
        } catch (Exception e) {
            logger.error("Error completing pet adoption for {}: {}", petId, e.getMessage());
        }
    }

    /**
     * Schedule follow-up visit
     */
    private void scheduleFollowUpVisit(Adoption adoption) {
        // Schedule follow-up visit for 30 days from now
        LocalDateTime followUpDate = LocalDateTime.now().plusDays(30);
        adoption.setFollowUpDate(followUpDate);
        
        logger.info("Follow-up visit scheduled for adoption {} on {}", adoption.getAdoptionId(), followUpDate);
    }
}
