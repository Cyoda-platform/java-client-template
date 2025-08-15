package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AdoptPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptPetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptPet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.getStatus() != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            // If pet is RESERVED, mark ADOPTED. We cannot rely on a reservation object because entity does not model it.
            if (pet != null && "RESERVED".equalsIgnoreCase(pet.getStatus())) {
                pet.setStatus("ADOPTED");
                logger.info("Pet {} marked ADOPTED", pet.getId());

                // Attempt to persist change for other systems by updating the pet via EntityService (best-effort).
                try {
                    if (pet.getId() != null) {
                        UUID petUuid = UUID.fromString(pet.getId());
                        entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid, pet).join();
                    }
                } catch (Exception ex) {
                    logger.warn("Unable to persist adopted pet {} via EntityService: {}", pet == null ? "<null>" : pet.getId(), ex.getMessage());
                }

            } else {
                logger.warn("AdoptPetProcessor invoked but pet {} is not RESERVED - current status={}", pet == null ? "<null>" : pet.getId(), pet == null ? "<null>" : pet.getStatus());
            }
        } catch (Exception e) {
            logger.error("Error during AdoptPetProcessor for pet {}: {}", pet == null ? "<null>" : pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
