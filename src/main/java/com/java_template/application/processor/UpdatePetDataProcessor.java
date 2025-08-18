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

import java.util.List;

@Component
public class UpdatePetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdatePetDataProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UpdatePetData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.getTechnicalId() != null;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        // Apply updates only when appropriate - idempotent behaviour
        if (pet.getName() != null && !pet.getName().trim().isEmpty()) {
            pet.setName(pet.getName().trim());
        }
        if (pet.getSpecies() != null) {
            pet.setSpecies(pet.getSpecies().trim().toLowerCase());
        }

        // Ensure photos list is deduplicated
        List<?> photos = pet.getPhotos();
        if (photos != null && photos.size() > 1) {
            // naive dedup by equals
            java.util.LinkedHashSet<Object> set = new java.util.LinkedHashSet<>(photos);
            pet.setPhotos(new java.util.ArrayList<>(set));
        }

        // update derived flag isArchived
        if ("ARCHIVED".equalsIgnoreCase(pet.getStatus())) {
            pet.setIsArchived(true);
        } else {
            pet.setIsArchived(false);
        }

        if (pet.getVersion() != null) {
            pet.setVersion(pet.getVersion() + 1);
        }

        logger.info("Updated pet data for {}", pet.getTechnicalId());
        return pet;
    }
}
