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
import java.util.List;

@Component
public class ValidatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidatePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            boolean hasName = pet.getName() != null && !pet.getName().trim().isEmpty();
            boolean hasSpecies = pet.getSpecies() != null && !pet.getSpecies().trim().isEmpty();
            List<String> images = pet.getImages();
            boolean hasImages = images != null && !images.isEmpty();

            if (hasName && hasSpecies && hasImages) {
                pet.setLifecycleState("AVAILABLE");
                pet.setStatus("available");
            } else {
                // mark as pending if required fields missing or images missing
                pet.setLifecycleState("PENDING");
                pet.setStatus("pending");
            }
            pet.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error validating pet {}: {}", pet == null ? "<null>" : pet.getTechnicalId(), e.getMessage(), e);
            // keep entity unchanged other than updatedAt to signal attempt
            if (pet != null) {
                pet.setUpdatedAt(Instant.now().toString());
            }
        }
        return pet;
    }
}
