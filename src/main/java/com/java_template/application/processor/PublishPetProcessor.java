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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PublishPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishPetProcessor(SerializerFactory serializerFactory,
                               EntityService entityService,
                               ObjectMapper objectMapper) {
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
            // 1) Duplicate detection based on name + breed + location (case-insensitive)
            boolean duplicateFound = false;
            if (entity.getName() != null && !entity.getName().isBlank()
                    && entity.getBreed() != null && !entity.getBreed().isBlank()
                    && entity.getLocation() != null && !entity.getLocation().isBlank()) {

                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.name", "IEQUALS", entity.getName()),
                        Condition.of("$.breed", "IEQUALS", entity.getBreed()),
                        Condition.of("$.location", "IEQUALS", entity.getLocation())
                );

                CompletableFuture<List<DataPayload>> filteredItemsFuture =
                        entityService.getItemsByCondition(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, condition, true);

                List<DataPayload> dataPayloads = filteredItemsFuture.get();
                if (dataPayloads != null) {
                    for (DataPayload payload : dataPayloads) {
                        try {
                            if (payload == null || payload.getData() == null) continue;
                            Pet candidate = objectMapper.treeToValue(payload.getData(), Pet.class);
                            if (candidate == null) continue;

                            // If candidate is not the same technicalId or external id, treat as duplicate
                            String candidateTechnicalId = candidate.getTechnicalId();
                            String currentTechnicalId = entity.getTechnicalId();
                            String candidateExternalId = candidate.getId();
                            String currentExternalId = entity.getId();

                            boolean sameTechnical = (currentTechnicalId != null && currentTechnicalId.equals(candidateTechnicalId));
                            boolean sameExternal = (currentExternalId != null && currentExternalId.equals(candidateExternalId));

                            if (!sameTechnical || (currentExternalId != null && !sameExternal)) {
                                // Found another record that matches by name/breed/location and differs by id
                                duplicateFound = true;
                                logger.info("Duplicate pet detected. Current technicalId={}, candidate technicalId={}", currentTechnicalId, candidateTechnicalId);
                                break;
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to parse candidate pet payload during duplicate check: {}", e.getMessage());
                        }
                    }
                }
            }

            if (duplicateFound) {
                // Mark as removed/duplicate; do not perform external updates on this entity (Cyoda will persist)
                entity.setStatus("removed");
                logger.info("Pet marked as removed due to duplicate detection: name={}, breed={}, location={}",
                        entity.getName(), entity.getBreed(), entity.getLocation());
                return entity;
            }

            // 2) Compute initial publish status.
            // Preserve explicit reserved/adopted/removed states.
            String currentStatus = entity.getStatus();
            if (currentStatus == null || currentStatus.isBlank()) {
                // Default to available when no explicit status provided
                entity.setStatus("available");
                logger.info("Pet status set to available (no previous status). technicalId={}", entity.getTechnicalId());
                return entity;
            }

            String normalized = currentStatus.trim().toLowerCase();
            if ("reserved".equals(normalized) || "adopted".equals(normalized) || "removed".equals(normalized) || "available".equals(normalized)) {
                // Keep existing canonical statuses
                logger.info("Pet status retained as '{}'. technicalId={}", currentStatus, entity.getTechnicalId());
                return entity;
            }

            // For any other intermediate statuses (e.g., PUBLISHED, ENRICHED, PET_CREATED), transition to AVAILABLE
            entity.setStatus("available");
            logger.info("Pet status transitioned from '{}' to 'available'. technicalId={}", currentStatus, entity.getTechnicalId());

            return entity;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during duplicate detection: {}", ie.getMessage(), ie);
            return entity;
        } catch (ExecutionException ee) {
            logger.error("Execution error during duplicate detection: {}", ee.getMessage(), ee);
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error in PublishPetProcessor: {}", ex.getMessage(), ex);
            return entity;
        }
    }
}