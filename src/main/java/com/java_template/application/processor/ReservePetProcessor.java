package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReservePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(ctx -> processEntityLogic(ctx, request)) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context,
                                   EntityProcessorCalculationRequest request) {
        Pet entity = context.entity();
        if (entity == null) return null;

        try {
            String currentStatus = entity.getStatus();
            if (currentStatus != null && currentStatus.equalsIgnoreCase("Available")) {
                // Set status to Reserved
                entity.setStatus("Reserved");

                // Ensure metadata map exists
                Map<String, Object> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                    entity.setMetadata(metadata);
                }

                // Record reservation timestamp
                metadata.put("reservedAt", OffsetDateTime.now().toString());

                // Attempt to find a related AdoptionRequest for this pet and record the requestId if found.
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group(
                        "AND",
                        Condition.of("$.petId", "EQUALS", entity.getPetId())
                    );

                    CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                        AdoptionRequest.ENTITY_NAME,
                        AdoptionRequest.ENTITY_VERSION,
                        condition,
                        true
                    );

                    List<DataPayload> dataPayloads = itemsFuture.get();
                    if (dataPayloads != null && !dataPayloads.isEmpty()) {
                        // Prefer CREATED or PENDING_REVIEW requests; otherwise pick the first
                        AdoptionRequest matched = null;
                        for (DataPayload payload : dataPayloads) {
                            try {
                                AdoptionRequest ar = objectMapper.treeToValue(payload.getData(), AdoptionRequest.class);
                                if (ar == null) continue;
                                String s = ar.getStatus();
                                if (s != null && (s.equalsIgnoreCase("CREATED") || s.equalsIgnoreCase("PENDING_REVIEW"))) {
                                    matched = ar;
                                    break;
                                }
                                if (matched == null) matched = ar;
                            } catch (Exception e) {
                                logger.warn("Failed to parse AdoptionRequest payload into object: {}", e.getMessage());
                            }
                        }
                        if (matched != null && matched.getRequestId() != null && !matched.getRequestId().isBlank()) {
                            metadata.put("reservedByRequestId", matched.getRequestId());
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while searching for AdoptionRequest: {}", ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.error("Execution error when searching for AdoptionRequest: {}", ee.getMessage(), ee);
                } catch (Exception ex) {
                    logger.error("Unexpected error when attempting to associate AdoptionRequest: {}", ex.getMessage(), ex);
                }

                // Add an audit entry to metadata if desired
                List<String> audit = (List<String>) metadata.get("audit");
                if (audit == null) {
                    audit = new ArrayList<>();
                    metadata.put("audit", audit);
                }
                audit.add(OffsetDateTime.now().toString() + " - status changed to Reserved by " + className);
            } else {
                // Not available to reserve - record a note in metadata
                Map<String, Object> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                    entity.setMetadata(metadata);
                }
                metadata.put("reserveAttemptedAt", OffsetDateTime.now().toString());
                metadata.put("reserveAttemptedBy", className);
                metadata.put("reserveAttemptedStatus", entity.getStatus());
                logger.info("Pet {} is not available for reservation. Current status: {}", entity.getPetId(), entity.getStatus());
            }
        } catch (Exception e) {
            logger.error("Error processing reserve logic for pet {}: {}", entity.getPetId(), e.getMessage(), e);
        }

        return entity;
    }
}