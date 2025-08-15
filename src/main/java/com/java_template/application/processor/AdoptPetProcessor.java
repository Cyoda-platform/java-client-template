package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdoptPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdoptPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
            // If pet is RESERVED, mark ADOPTED. The workflow will persist the pet entity after processing.
            if (pet != null && "RESERVED".equalsIgnoreCase(pet.getStatus())) {
                pet.setStatus("ADOPTED");
                logger.info("Pet {} marked ADOPTED", pet.getId());
            } else {
                logger.warn("AdoptPetProcessor invoked but pet {} is not RESERVED - current status={}", pet == null ? "<null>" : pet.getId(), pet == null ? "<null>" : pet.getStatus());
            }
        } catch (Exception e) {
            logger.error("Error during AdoptPetProcessor for pet {}: {}", pet == null ? "<null>" : pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
