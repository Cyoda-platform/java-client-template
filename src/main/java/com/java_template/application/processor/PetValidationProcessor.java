package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        Pet entity = context.entity();

        // Ensure entity is not null (should not happen due to prior validate, but safe-guard)
        if (entity == null) {
            logger.warn("Pet entity is null in processing context");
            return entity;
        }

        // 1. Ensure required business fields: name already enforced by isValid().
        //    Breed is considered required by business rules (even though entity model treats it as optional).
        if (entity.getBreed() == null || entity.getBreed().isBlank()) {
            logger.info("Breed missing for pet id {}. Setting to 'Unknown'.", entity.getId());
            entity.setBreed("Unknown");
        }

        // 2. Normalize breed: capitalize words and trim whitespace
        String breed = entity.getBreed();
        if (breed != null) {
            String normalizedBreed = Arrays.stream(breed.trim().split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .map(s -> {
                        String lower = s.toLowerCase();
                        return Character.toUpperCase(lower.charAt(0)) + (lower.length() > 1 ? lower.substring(1) : "");
                    })
                    .collect(Collectors.joining(" "));
            entity.setBreed(normalizedBreed);
        }

        // 3. Normalize description: trim; if absent set to empty string
        String desc = entity.getDescription();
        if (desc == null || desc.isBlank()) {
            entity.setDescription("");
        } else {
            entity.setDescription(desc.trim());
        }

        // 4. Default source if missing
        if (entity.getSource() == null || entity.getSource().isBlank()) {
            entity.setSource("Petstore");
        }

        // 5. Determine initial availability status if currently in CREATED or unspecified business state.
        //    Business rule: if age < 1 => PENDING_ADOPTION (e.g., very young), otherwise AVAILABLE.
        //    If status already indicates ADOPTED or AVAILABLE or PENDING_ADOPTION, respect it.
        String status = entity.getStatus();
        if (status == null || status.isBlank() || "CREATED".equalsIgnoreCase(status)) {
            Integer age = entity.getAge();
            if (age == null) {
                // If age missing, keep as PENDING_ADOPTION to require manual review
                entity.setStatus("PENDING_ADOPTION");
            } else if (age < 1) {
                entity.setStatus("PENDING_ADOPTION");
            } else {
                entity.setStatus("AVAILABLE");
            }
            logger.info("Set initial status for pet id {} to {}", entity.getId(), entity.getStatus());
        } else {
            // Normalize known statuses to uppercase canonical values
            entity.setStatus(status.trim().toUpperCase());
        }

        // Additional note: do not add/update/delete the triggering entity via EntityService here.
        // Any further enrichment or persistence will be handled by downstream processors in the workflow.

        return entity;
    }
}