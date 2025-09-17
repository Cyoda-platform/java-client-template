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

import java.util.List;

/**
 * StartReviewProcessor - Begins review process and updates pet status
 * 
 * Purpose: Begin staff review of adoption application
 * Transition: initiated -> under_review (manual)
 */
@Component
public class StartReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartReviewProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption review start for request: {}", request.getId());

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
     * Main business logic processing method
     * Finds pet by adoption.petId and transitions pet to "reserved" state
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption entity = entityWithMetadata.entity();

        logger.debug("Starting review for adoption: {}", entity.getAdoptionId());

        // Find pet by adoption.petId
        EntityWithMetadata<Pet> petWithMetadata = findPetByPetId(entity.getPetId());
        
        if (petWithMetadata == null) {
            logger.error("Pet {} not found for adoption {}", 
                        entity.getPetId(), entity.getAdoptionId());
            return entityWithMetadata;
        }

        Pet pet = petWithMetadata.entity();
        String currentPetState = petWithMetadata.metadata().getState();
        
        logger.info("Found pet {} in state {} for adoption {}", 
                   pet.getPetId(), currentPetState, entity.getAdoptionId());

        // Transition pet to "reserved" state if not already reserved
        if (!"reserved".equals(currentPetState)) {
            try {
                entityService.update(petWithMetadata.metadata().getId(), pet, "reserve_pet");
                logger.info("Transitioned pet {} to reserved state for adoption {}", 
                           pet.getPetId(), entity.getAdoptionId());
            } catch (Exception e) {
                logger.error("Failed to transition pet {} to reserved state for adoption {}: {}", 
                            pet.getPetId(), entity.getAdoptionId(), e.getMessage());
            }
        } else {
            logger.info("Pet {} is already in reserved state for adoption {}", 
                       pet.getPetId(), entity.getAdoptionId());
        }

        logger.info("Review started for adoption {}", entity.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Find pet by pet ID
     */
    private EntityWithMetadata<Pet> findPetByPetId(String petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Pet.ENTITY_NAME)
                    .withVersion(Pet.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Pet>> pets = entityService.search(modelSpec, condition, Pet.class);
            
            if (pets.isEmpty()) {
                return null;
            }
            
            return pets.get(0);
        } catch (Exception e) {
            logger.error("Error finding pet for petId {}: {}", petId, e.getMessage());
            return null;
        }
    }
}
