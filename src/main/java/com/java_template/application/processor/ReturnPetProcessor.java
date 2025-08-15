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

import java.time.Instant;

@Component
public class ReturnPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReturnPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReturnPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReturnPet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            String prevStatus = pet.getStatus();
            if (!"adopted".equalsIgnoreCase(prevStatus)) {
                logger.warn("Return requested for pet {} but pet is not adopted (status={})", pet.getId(), prevStatus);
            }

            pet.setStatus("returned");
            pet.getTags().add("returned_request");
            pet.setUpdatedAt(Instant.now().toString());
            // keep adoptedBy/adoptedAt for audit but can be cleared depending on policy
            logger.info("Pet {} marked as returned", pet.getId());
        } catch (Exception e) {
            logger.error("Error in ReturnPetProcessor for pet {}: {}", pet.getId(), e.getMessage(), e);
        }
        return pet;
    }
}
