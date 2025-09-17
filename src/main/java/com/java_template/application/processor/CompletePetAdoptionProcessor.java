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
 * CompletePetAdoptionProcessor - Finalizes adoption and updates pet status
 * 
 * Purpose: Finalize adoption and update adoption completion date
 * Transition: reserved -> adopted (manual)
 */
@Component
public class CompletePetAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompletePetAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompletePetAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet adoption completion for request: {}", request.getId());

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
     * Finds adoption by pet.petId and sets completion date
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet entity = entityWithMetadata.entity();

        logger.debug("Completing adoption for pet: {}", entity.getPetId());

        // Find adoption by pet.petId
        EntityWithMetadata<Adoption> adoptionWithMetadata = findAdoptionByPetId(entity.getPetId());
        
        if (adoptionWithMetadata == null) {
            logger.error("No adoption found for pet {}", entity.getPetId());
            return entityWithMetadata;
        }

        Adoption adoption = adoptionWithMetadata.entity();
        
        // Set adoption.completionDate to current timestamp
        adoption.setCompletionDate(LocalDateTime.now());
        
        try {
            // Update the adoption entity with completion date
            entityService.update(adoptionWithMetadata.metadata().getId(), adoption, null);
            logger.info("Completed adoption {} for pet {} at {}", 
                       adoption.getAdoptionId(), entity.getPetId(), adoption.getCompletionDate());
        } catch (Exception e) {
            logger.error("Failed to update adoption {} for pet {}: {}", 
                        adoption.getAdoptionId(), entity.getPetId(), e.getMessage());
        }

        logger.info("Pet {} adoption completed successfully", entity.getPetId());

        return entityWithMetadata;
    }

    /**
     * Find adoption by pet ID
     */
    private EntityWithMetadata<Adoption> findAdoptionByPetId(String petId) {
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
            
            if (adoptions.isEmpty()) {
                return null;
            }
            
            // Return the first (most recent) adoption
            return adoptions.get(0);
        } catch (Exception e) {
            logger.error("Error finding adoption for pet {}: {}", petId, e.getMessage());
            return null;
        }
    }
}
