package com.java_template.application.processor;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RejectRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RejectRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RejectRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Only allow rejection if the request is in a reviewable state.
        String currentStatus = entity.getStatus();
        String statusNorm = currentStatus == null ? "" : currentStatus.trim();
        if (!"PENDING_REVIEW".equalsIgnoreCase(statusNorm) && !"CREATED".equalsIgnoreCase(statusNorm)) {
            logger.warn("AdoptionRequest {} not in a reviewable state (current: {}). Skipping rejection.", entity.getRequestId(), currentStatus);
            return entity;
        }

        // Set the request status to REJECTED
        try {
            entity.setStatus("REJECTED");
            logger.info("AdoptionRequest {} marked as REJECTED", entity.getRequestId());
        } catch (Exception e) {
            // Defensive: if setters are not available or fail, log and continue (entity persistence is done by workflow).
            logger.warn("Failed to set status to REJECTED on AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
        }

        // If the request had reserved the pet, attempt to release the pet reservation.
        try {
            if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.petId", "EQUALS", entity.getPetId())
                );

                CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    condition,
                    true
                );

                List<DataPayload> dataPayloads = filteredItemsFuture.get();
                if (dataPayloads != null && !dataPayloads.isEmpty()) {
                    // Use the first matched pet (petId is expected to be unique)
                    DataPayload payload = dataPayloads.get(0);
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);

                    if (pet != null) {
                        String petStatus = pet.getStatus();
                        Map<String, Object> metadata = pet.getMetadata();
                        boolean reservedByThisRequest = false;

                        if (metadata != null) {
                            // Support multiple reservation key names used across processors
                            Object reservedBy = metadata.get("reservedBy");
                            if (reservedBy == null) {
                                reservedBy = metadata.get("reservedByRequestId");
                            }
                            if (reservedBy != null && reservedBy.toString().equals(entity.getRequestId())) {
                                reservedByThisRequest = true;
                            }
                        }

                        if (petStatus != null && petStatus.equalsIgnoreCase("Reserved") && reservedByThisRequest) {
                            try {
                                pet.setStatus("Available");
                            } catch (Exception e) {
                                logger.warn("Unable to set pet status via setter for petId {}: {}", pet.getPetId(), e.getMessage());
                            }

                            if (metadata != null) {
                                metadata.remove("reservedBy");
                                metadata.remove("reservedByRequestId");
                                try {
                                    pet.setMetadata(metadata);
                                } catch (Exception e) {
                                    logger.warn("Unable to set pet metadata via setter for petId {}: {}", pet.getPetId(), e.getMessage());
                                }
                            }

                            // Persist the change to the pet entity
                            String technicalId = null;
                            if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                                technicalId = payload.getMeta().get("entityId").asText();
                            }
                            if (technicalId != null && !technicalId.isBlank()) {
                                try {
                                    CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(technicalId), pet);
                                    updatedId.get();
                                    logger.info("Released reservation for pet {} (entityId={}) due to rejection of request {}", pet.getPetId(), technicalId, entity.getRequestId());
                                } catch (Exception ex) {
                                    logger.error("Failed to update Pet entity after rejecting request {}: {}", entity.getRequestId(), ex.getMessage(), ex);
                                }
                            } else {
                                logger.warn("Could not determine technicalId for pet with petId {} while releasing reservation", pet.getPetId());
                            }
                        } else {
                            logger.debug("No reservation to release for petId {} (petStatus={}, reservedByThisRequest={})", entity.getPetId(), petStatus, reservedByThisRequest);
                        }
                    }
                } else {
                    logger.debug("No Pet found with petId {} while processing rejection for request {}", entity.getPetId(), entity.getRequestId());
                }
            }
        } catch (Exception ex) {
            logger.error("Error while attempting to release pet reservation for request {}: {}", entity.getRequestId(), ex.getMessage(), ex);
            // Do not fail the processor — the adoption request is still marked as REJECTED.
        }

        return entity;
    }
}