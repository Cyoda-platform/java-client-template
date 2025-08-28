package com.java_template.application.processor;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OnAdoptionRequestApprovedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OnAdoptionRequestApprovedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OnAdoptionRequestApprovedProcessor(SerializerFactory serializerFactory,
                                              EntityService entityService,
                                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Business logic:
        // - When an adoption request has been approved, update the referenced Pet.status -> "adopted"
        // - Persist the Pet update via EntityService
        // - Update the AdoptionRequest processedAt timestamp and move request to "completed"
        // - If Pet cannot be found or updated, log and set request to "rejected" with processedAt

        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in execution context");
            return null;
        }

        String petId = entity.getPetId();
        if (petId == null || petId.isBlank()) {
            logger.warn("AdoptionRequest {} has no petId", entity.getId());
            // mark as rejected and processed
            entity.setStatus("rejected");
            entity.setProcessedAt(Instant.now().toString());
            return entity;
        }

        try {
            // Build search condition to find Pet by business id (Pet.id)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", petId)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.warn("No Pet found with id={} for AdoptionRequest={}", petId, entity.getId());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            // Use the first matching pet
            DataPayload petPayload = dataPayloads.get(0);
            if (petPayload == null || petPayload.getData() == null) {
                logger.warn("Invalid DataPayload for petId={} on AdoptionRequest={}", petId, entity.getId());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);
            if (pet == null) {
                logger.warn("Failed to deserialize Pet for petId={} on AdoptionRequest={}", petId, entity.getId());
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            // Update pet status to adopted and set updatedAt
            pet.setStatus("adopted");
            pet.setUpdatedAt(Instant.now().toString());

            // Persist pet update using technical id from payload meta
            String technicalId = null;
            JsonNode meta = petPayload.getMeta();
            if (meta != null) {
                JsonNode techNode = meta.get("technicalId");
                if (techNode == null) techNode = meta.get("id");
                if (techNode != null && !techNode.isNull()) {
                    technicalId = techNode.asText();
                }
            }

            if (technicalId == null || technicalId.isBlank()) {
                // If technical id is not available, can't update; mark request rejected
                logger.error("Missing technicalId for Pet payload (petId={}) - cannot update Pet", petId);
                entity.setStatus("rejected");
                entity.setProcessedAt(Instant.now().toString());
                return entity;
            }

            CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(UUID.fromString(technicalId), pet);
            updateFuture.get(); // wait for completion

            // After successful pet update, mark adoption request as completed
            entity.setProcessedAt(Instant.now().toString());
            entity.setStatus("completed");

            logger.info("AdoptionRequest {} processed: Pet {} marked as adopted", entity.getId(), petId);
            return entity;

        } catch (Exception ex) {
            logger.error("Error while processing AdoptionRequest {}: {}", entity.getId(), ex.getMessage(), ex);
            // on exception, mark request as rejected and record processedAt
            try {
                entity.setProcessedAt(Instant.now().toString());
            } catch (Exception ignore) {}
            entity.setStatus("rejected");
            return entity;
        }
    }
}