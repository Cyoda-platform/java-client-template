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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PetPublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetPublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetPublishProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) return null;

        try {
            // Business rule: Publish pet by marking status AVAILABLE.
            // Only change the status of the entity that triggered the workflow (no external persistence calls here).
            String currentStatus = entity.getStatus();
            if (currentStatus == null || !currentStatus.equalsIgnoreCase("AVAILABLE")) {
                entity.setStatus("AVAILABLE");
                logger.info("Pet {} status set to AVAILABLE", entity.getPetId());
            } else {
                logger.info("Pet {} already AVAILABLE", entity.getPetId());
            }

            // Ensure tags list exists and contains a 'listed' tag to indicate it is published/listed.
            List<String> tags = entity.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
                entity.setTags(tags);
            }
            boolean hasListed = false;
            for (String t : tags) {
                if (t != null && t.equalsIgnoreCase("listed")) {
                    hasListed = true;
                    break;
                }
            }
            if (!hasListed) {
                tags.add("listed");
            }

            // Provide a friendly description if missing.
            String desc = entity.getDescription();
            if (desc == null || desc.isBlank()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Meet ").append(entity.getName() != null ? entity.getName() : "this pet");
                if (entity.getAge() != null) {
                    sb.append(", approx ").append(entity.getAge()).append(" year");
                    if (entity.getAge() != 1) sb.append("s");
                }
                if (entity.getSpecies() != null) {
                    sb.append(" (").append(entity.getSpecies()).append(")");
                }
                sb.append(". Now available for adoption/purchase!");
                entity.setDescription(sb.toString());
            }

        } catch (Exception ex) {
            logger.error("Error while publishing pet {}: {}", entity.getPetId(), ex.getMessage(), ex);
            // Do not throw; letting the serializer pipeline handle errors via ErrorInfo if needed.
        }

        return entity;
    }
}