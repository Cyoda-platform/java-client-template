package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Pet;
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

import java.util.ArrayList;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public PetProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

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

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        String technicalId = context.request().getEntityId();

        // Validate pet name and category
        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet {} validation failed: name or category blank", technicalId);
            return pet;
        }
        // Assign default tags if empty
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (!pet.getTags().contains("fun")) {
            pet.getTags().add("fun");
        }
        // Assign default photoUrls if empty
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }
        if (pet.getPhotoUrls().isEmpty()) {
            pet.getPhotoUrls().add("https://example.com/default-pet-photo.jpg");
        }
        logger.info("Processed Pet id {} with name {}", technicalId, pet.getName());

        // Notify inventory system or other services here (not implemented)

        return pet;
    }
}
