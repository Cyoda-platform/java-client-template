package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public ValidatePetProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid Pet state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        boolean valid = entity != null
            && entity.getPetId() != null && !entity.getPetId().isEmpty()
            && entity.getName() != null && !entity.getName().isEmpty()
            && entity.getCategory() != null && !entity.getCategory().isEmpty()
            && entity.getStatus() != null && !entity.getStatus().isEmpty();

        if (!valid) {
            logger.warn("Pet validation failed for petId: {}", entity != null ? entity.getPetId() : "null");
        }
        return valid;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        // Pets are immutable after creation, no updates performed.
        logger.info("Validated Pet entity with petId: {}", pet.getPetId());
        return pet;
    }
}
