package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.application.entity.owner.version_1.Owner;
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
 * InitiateAdoptionProcessor - Validates adoption application and sets initial state
 * 
 * Purpose: Start the adoption application process
 * Transition: initial_state -> initiated (automatic)
 */
@Component
public class InitiateAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitiateAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InitiateAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption initiation for request: {}", request.getId());

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
     * Validates petId and ownerId exist, sets application date
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption entity = entityWithMetadata.entity();

        logger.debug("Initiating adoption: {}", entity.getAdoptionId());

        // Validate adoption.petId exists
        if (!validatePetExists(entity.getPetId())) {
            logger.error("Pet {} does not exist for adoption {}", 
                        entity.getPetId(), entity.getAdoptionId());
        }

        // Validate adoption.ownerId exists
        if (!validateOwnerExists(entity.getOwnerId())) {
            logger.error("Owner {} does not exist for adoption {}", 
                        entity.getOwnerId(), entity.getAdoptionId());
        }

        // Set adoption.applicationDate to current timestamp if not already set
        if (entity.getApplicationDate() == null) {
            entity.setApplicationDate(LocalDateTime.now());
            logger.info("Set application date for adoption {} to {}", 
                       entity.getAdoptionId(), entity.getApplicationDate());
        }

        logger.info("Adoption {} initiated successfully", entity.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Validate that pet exists
     */
    private boolean validatePetExists(String petId) {
        if (petId == null || petId.trim().isEmpty()) {
            return false;
        }

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
            return !pets.isEmpty();
        } catch (Exception e) {
            logger.error("Error validating pet existence for petId {}: {}", petId, e.getMessage());
            return false;
        }
    }

    /**
     * Validate that owner exists
     */
    private boolean validateOwnerExists(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return false;
        }

        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(Owner.ENTITY_NAME)
                    .withVersion(Owner.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.ownerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(ownerId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Owner>> owners = entityService.search(modelSpec, condition, Owner.class);
            return !owners.isEmpty();
        } catch (Exception e) {
            logger.error("Error validating owner existence for ownerId {}: {}", ownerId, e.getMessage());
            return false;
        }
    }
}
