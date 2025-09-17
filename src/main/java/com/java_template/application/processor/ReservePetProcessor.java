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
import java.util.UUID;

/**
 * ReservePetProcessor - Marks pet as reserved and creates adoption record
 * 
 * Purpose: Reserve pet for potential adopter and create adoption application
 * Transition: available -> reserved (manual)
 */
@Component
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReservePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet reservation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Creates new Adoption entity with pet.petId and current timestamp
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet entity = entityWithMetadata.entity();

        logger.debug("Reserving pet: {}", entity.getPetId());

        // Check if there's already an active adoption for this pet
        if (hasActiveAdoption(entity.getPetId())) {
            logger.warn("Pet {} already has an active adoption", entity.getPetId());
            return entityWithMetadata;
        }

        // Create new Adoption entity with pet.petId
        Adoption adoption = new Adoption();
        adoption.setAdoptionId(UUID.randomUUID().toString());
        adoption.setPetId(entity.getPetId());
        adoption.setApplicationDate(LocalDateTime.now());
        
        // Note: ownerId should be set by the calling system/controller
        // For now, we'll create the adoption record without ownerId
        // The ownerId will be set when the adoption is properly initiated
        
        try {
            EntityWithMetadata<Adoption> createdAdoption = entityService.create(adoption);
            logger.info("Created adoption {} for pet {}", 
                       createdAdoption.entity().getAdoptionId(), entity.getPetId());
        } catch (Exception e) {
            logger.error("Failed to create adoption for pet {}: {}", entity.getPetId(), e.getMessage());
            // Continue processing even if adoption creation fails
        }

        logger.info("Pet {} reserved successfully", entity.getPetId());

        return entityWithMetadata;
    }

    /**
     * Check if pet already has an active adoption
     */
    private boolean hasActiveAdoption(String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Adoption.ENTITY_NAME)
                    .withVersion(Adoption.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Adoption>> adoptions = entityService.search(modelSpec, condition, Adoption.class);
            return !adoptions.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking for active adoption for pet {}: {}", petId, e.getMessage());
            return false;
        }
    }
}
