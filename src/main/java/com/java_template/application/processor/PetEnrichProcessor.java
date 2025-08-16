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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PetEnrichProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetEnrichProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet enrichment for request: {}", request.getId());

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

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.getPetId() != null && !pet.getPetId().isEmpty();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            String status = pet.getStatus();
            if (status == null || !"VALIDATED".equals(status)) {
                logger.info("Pet {} is not in VALIDATED state - skipping enrichment", pet.getPetId());
                return pet;
            }

            pet.setStatus("ENRICHING");

            // Enrichment example: ensure at least one image and some tags
            try {
                List<String> images = pet.getImages();
                if (images == null || images.isEmpty()) {
                    pet.setImages(Collections.singletonList("https://example.com/images/placeholder.jpg"));
                }
            } catch (Throwable t) {
                // If images field doesn't exist, skip
            }

            try {
                List<String> tags = pet.getTags();
                if (tags == null) {
                    pet.setTags(new ArrayList<>());
                }
                // add a generic tag if none
                if (pet.getTags().isEmpty()) {
                    pet.getTags().add("unspecified");
                }
            } catch (Throwable t) {
                // ignore absence of tags field
            }

            // Mark available if enrichment successful
            pet.setStatus("AVAILABLE");
            logger.info("Pet {} enrichment completed and marked AVAILABLE", pet.getPetId());
            return pet;
        } catch (Exception e) {
            logger.error("Unhandled error while enriching pet {}", pet == null ? "<null>" : pet.getPetId(), e);
            if (pet != null) {
                pet.setStatus("FAILED");
                try {
                    pet.setDescription("Enrichment processor error: " + e.getMessage());
                } catch (Throwable ignore) {
                }
            }
            return pet;
        }
    }
}
