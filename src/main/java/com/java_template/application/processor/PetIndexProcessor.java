package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class PetIndexProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetIndexProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetIndexProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
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
        Pet pet = context.entity();

        try {
            // Determine whether the pet is currently reserved/under request by checking AdoptionRequest entities.
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.petId", "EQUALS", pet.getId())
            );

            CompletableFuture<ArrayNode> requestsFuture = entityService.getItemsByCondition(
                AdoptionRequest.ENTITY_NAME,
                String.valueOf(AdoptionRequest.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode requests = requestsFuture.join();

            // Define statuses that indicate the pet is not available for general adoption (i.e., reserved/pending)
            Set<String> nonAvailableStatuses = new HashSet<>(Arrays.asList(
                "reserved",        // explicitly reserved
                "requested",       // newly requested and not yet reviewed
                "under_review",    // admin review in progress
                "approved",        // approved but not completed pickup
                "pending"          // generic pending state
            ));

            boolean hasActiveRequest = false;
            if (requests != null && requests.size() > 0) {
                for (int i = 0; i < requests.size(); i++) {
                    String status = null;
                    if (requests.get(i).has("status") && !requests.get(i).get("status").isNull()) {
                        status = requests.get(i).get("status").asText();
                    }
                    if (status != null && nonAvailableStatuses.contains(status.toLowerCase())) {
                        hasActiveRequest = true;
                        break;
                    }
                }
            }

            // Set pet status according to reservation presence.
            if (hasActiveRequest) {
                pet.setStatus("pending");
                logger.info("Pet {} marked as pending due to active adoption request.", pet.getId());
            } else {
                // If pet was marked removed/adopted explicitly, don't override.
                String current = pet.getStatus();
                if (current == null || current.isBlank() || "pending".equalsIgnoreCase(current)) {
                    pet.setStatus("available");
                    logger.info("Pet {} marked as available for adoption.", pet.getId());
                } else {
                    logger.debug("Pet {} retains existing status: {}", pet.getId(), current);
                }
            }

            // Optionally add an index tag so downstream systems can detect indexing has occurred.
            // Only add if tags exists and doesn't already contain marker.
            if (pet.getTags() != null) {
                boolean containsIndex = pet.getTags().stream().anyMatch(t -> "indexed".equalsIgnoreCase(t));
                if (!containsIndex) {
                    pet.getTags().add("indexed");
                }
            }
        } catch (Exception ex) {
            // On any failure, log and leave entity state unchanged. The workflow can handle retries or failures.
            logger.error("Failed to evaluate adoption requests for pet {}: {}", pet.getId(), ex.getMessage(), ex);
        }

        return pet;
    }
}