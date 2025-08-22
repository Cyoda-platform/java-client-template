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
import java.util.List;

@Component
public class PetIndexProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetIndexProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetIndexProcessor(SerializerFactory serializerFactory) {
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
        Pet entity = context.entity();

        if (entity == null) {
            logger.warn("Pet entity is null in execution context");
            return null;
        }

        // Ensure tags list is initialized
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
            entity.setTags(tags);
        }

        // Business logic:
        // - If pet is explicitly reserved (tag "reserved") or status is "pending", keep/mark as pending.
        // - Otherwise, move ENRICHED (or blank) to AVAILABLE for publishing/indexing.
        // - Add an "indexed" tag to indicate it has been processed by the indexer.

        boolean hasReservedTag = tags.stream()
            .anyMatch(t -> t != null && t.equalsIgnoreCase("reserved"));

        String currentStatus = entity.getStatus();
        if (currentStatus != null && currentStatus.equalsIgnoreCase("pending")) {
            // already pending - no change, but ensure indexed tag added for the processing trace
            logger.debug("Pet {} already in pending status; leaving status unchanged", entity.getId());
        } else if (hasReservedTag) {
            logger.info("Pet {} has reserved tag; marking as pending", entity.getId());
            entity.setStatus("pending");
        } else {
            // Default to available when indexing unless explicitly pending/reserved
            if (currentStatus == null || currentStatus.isBlank() || currentStatus.equalsIgnoreCase("enriched")) {
                entity.setStatus("available");
                logger.info("Pet {} marked as available by PetIndexProcessor", entity.getId());
            } else {
                // If status is something else (e.g., available/adopted/removed), leave as-is but log
                logger.debug("Pet {} status is '{}'; no status change applied by indexer", entity.getId(), currentStatus);
            }
        }

        // Ensure "indexed" tag is present to indicate indexing step completed
        boolean hasIndexed = tags.stream()
            .anyMatch(t -> t != null && t.equalsIgnoreCase("indexed"));
        if (!hasIndexed) {
            tags.add("indexed");
        }

        // Additional minor enrichment: normalize tags to lowercase for consistent filtering (non-destructive)
        List<String> normalized = new ArrayList<>();
        for (String t : tags) {
            if (t != null) {
                normalized.add(t.trim().toLowerCase());
            }
        }
        entity.setTags(normalized);

        return entity;
    }
}