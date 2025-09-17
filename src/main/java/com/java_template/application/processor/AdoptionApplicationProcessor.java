package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AdoptionApplicationProcessor - Process new adoption application
 */
@Component
public class AdoptionApplicationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionApplicationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionApplicationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption application for request: {}", request.getId());

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
     * Process new adoption application
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();

        logger.debug("Processing adoption application: {}", adoption.getAdoptionId());

        // Set application date to current time if not already set
        if (adoption.getApplicationDate() == null) {
            adoption.setApplicationDate(LocalDateTime.now());
        }

        // Set default values for optional fields
        if (adoption.getHomeVisitRequired() == null) {
            adoption.setHomeVisitRequired(true); // Default to requiring home visit
        }

        if (adoption.getContractSigned() == null) {
            adoption.setContractSigned(false);
        }

        if (adoption.getHomeVisitPassed() == null) {
            adoption.setHomeVisitPassed(false);
        }

        // Reserve pet by updating its state to reserved
        reservePet(adoption.getPetId());

        // Send confirmation to owner (simulated)
        logger.info("Application confirmation sent for adoption: {}", adoption.getAdoptionId());

        logger.info("Adoption application {} processed successfully", adoption.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Reserve pet by transitioning it to reserved state
     */
    private void reservePet(String petId) {
        try {
            // Find pet by business ID
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petWithMetadata = entityService.findByBusinessId(
                    petModelSpec, petId, "petId", Pet.class);

            if (petWithMetadata != null) {
                Pet pet = petWithMetadata.entity();
                // Update pet with reserve_pet transition
                entityService.update(petWithMetadata.metadata().getId(), pet, "reserve_pet");
                logger.info("Pet {} reserved for adoption", petId);
            } else {
                logger.warn("Pet {} not found for reservation", petId);
            }
        } catch (Exception e) {
            logger.error("Error reserving pet {}: {}", petId, e.getMessage());
        }
    }
}
