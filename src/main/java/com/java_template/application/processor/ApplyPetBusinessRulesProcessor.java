package com.java_template.application.processor;

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

@Component
public class ApplyPetBusinessRulesProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ApplyPetBusinessRulesProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();

        // Check pet status validity
        if (!checkPetStatusValid(pet.getStatus())) {
            logger.error("Invalid Pet status: {}", pet.getStatus());
            return pet; // Early return, do not proceed if invalid
        }

        // Check pet category existence
        if (!checkPetCategoryExists(pet.getCategory())) {
            logger.error("Pet category does not exist: {}", pet.getCategory());
            return pet; // Early return, do not proceed if invalid
        }

        // Apply tagging logic
        applyTaggingProcessor(pet);

        // Validate photo URLs
        validatePhotoUrlsProcessor(pet);

        logger.info("Pet processing completed for entity");

        return pet;
    }

    private boolean checkPetStatusValid(String status) {
        if (status == null) {
            return false;
        }
        java.util.List<String> validStatuses = java.util.List.of("available", "pending", "sold");
        boolean isValid = validStatuses.contains(status.toLowerCase());
        if (!isValid) {
            logger.error("Pet status '{}' is not valid", status);
        }
        return isValid;
    }

    private boolean checkPetCategoryExists(String category) {
        if (category == null) {
            return false;
        }
        java.util.List<String> validCategories = java.util.List.of("cat", "dog", "bird", "reptile");
        boolean exists = validCategories.contains(category.toLowerCase());
        if (!exists) {
            logger.error("Pet category '{}' does not exist", category);
        }
        return exists;
    }

    private void applyTaggingProcessor(Pet pet) {
        if (pet.getTags() == null || pet.getTags().isBlank()) {
            pet.setTags("default");
            logger.info("Tags were empty; set default tag.");
        } else {
            logger.info("Pet tags verified: {}", pet.getTags());
        }
    }

    private void validatePhotoUrlsProcessor(Pet pet) {
        String urls = pet.getPhotoUrls();
        if (urls == null || urls.isBlank()) {
            logger.error("Pet photoUrls are empty");
            return;
        }
        String[] urlArray = urls.split(",");
        for (String url : urlArray) {
            if (!url.trim().startsWith("http")) {
                logger.error("Invalid photo URL: {}", url);
            }
        }
        logger.info("Photo URLs validated for Pet.");
    }
}
