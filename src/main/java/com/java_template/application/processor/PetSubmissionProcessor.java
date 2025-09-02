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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;

@Component
public class PetSubmissionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetSubmissionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetSubmissionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet submission for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
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
        Pet entity = context.entity();

        // Validate all required fields are present
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Pet name is required");
        }

        // Validate at least one photo URL exists
        if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
            throw new IllegalArgumentException("Pet must have at least one photo URL");
        }

        // Validate photo URLs format
        for (String photoUrl : entity.getPhotoUrls()) {
            if (!isValidUrl(photoUrl)) {
                throw new IllegalArgumentException("Invalid photo URL format: " + photoUrl);
            }
        }

        // Validate price is positive if provided
        if (entity.getPrice() != null && entity.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Pet price must be positive");
        }

        // Validate birth date is not in future
        if (entity.getBirthDate() != null && entity.getBirthDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Pet birth date cannot be in the future");
        }

        // Validate weight is positive if provided
        if (entity.getWeight() != null && entity.getWeight() <= 0) {
            throw new IllegalArgumentException("Pet weight must be positive");
        }

        logger.info("Pet submitted for review successfully: {}", entity.getName());
        return entity;
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
