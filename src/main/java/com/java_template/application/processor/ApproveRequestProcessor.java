package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ApproveRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApproveRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApproveRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // 1) Set the adoption request to APPROVED
        try {
            entity.setStatus("APPROVED");
            logger.info("AdoptionRequest {} status set to APPROVED", entity.getRequestId());
        } catch (Exception e) {
            logger.warn("Could not set status to APPROVED for request {}: {}", entity != null ? entity.getRequestId() : "unknown", e.getMessage(), e);
        }

        // 2) Determine payment status: if fee > 0 then PENDING, otherwise PAID
        try {
            Double fee = entity.getAdoptionFee();
            if (fee != null && fee > 0) {
                entity.setPaymentStatus("PENDING");
            } else {
                entity.setPaymentStatus("PAID");
            }
            logger.info("AdoptionRequest {} paymentStatus set to {}", entity.getRequestId(), entity.getPaymentStatus());
        } catch (Exception e) {
            logger.warn("Failed to determine payment status for request {}: {}", entity.getRequestId(), e.getMessage(), e);
            // default to PENDING to be safe
            entity.setPaymentStatus("PENDING");
        }

        // 3) Attempt to reserve the pet by updating the Pet entity status to "Reserved" and adding reservation metadata.
        // Try to resolve pet by technical id first, then by business petId attribute if needed.
        String petRef = entity.getPetId();
        if (petRef == null || petRef.isBlank()) {
            logger.warn("AdoptionRequest {} has no petId set, skipping pet reservation", entity.getRequestId());
            return entity;
        }

        // Try treat petRef as technical UUID
        boolean updatedPet = false;
        try {
            // First attempt: direct fetch by technical id (UUID)
            try {
                UUID petUuid = UUID.fromString(petRef);
                CompletableFuture<DataPayload> itemFuture = entityService.getItem(petUuid);
                DataPayload payload = itemFuture.get();
                if (payload != null && payload.getData() != null) {
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    if (pet != null) {
                        updatedPet = tryReservePet(pet, petUuid, entity);
                    } else {
                        logger.warn("Pet conversion returned null for technical id {}", petRef);
                    }
                } else {
                    logger.debug("No pet found by technical id {}", petRef);
                }
            } catch (IllegalArgumentException iae) {
                // Not a UUID, will search by business petId below
                logger.debug("Pet id {} is not a technical UUID, will search by business petId", petRef);
            }
        } catch (Exception e) {
            logger.error("Error while attempting to reserve pet by technical id for AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
        }

        if (!updatedPet) {
            // Second attempt: search by business petId (pet.petId)
            try {
                SearchConditionRequest cond = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petRef)
                );
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    cond,
                    true
                );
                List<DataPayload> dataPayloads = itemsFuture.get();
                if (dataPayloads != null && !dataPayloads.isEmpty()) {
                    // Use the first matching pet
                    DataPayload payload = dataPayloads.get(0);
                    Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    String technicalId = null;
                    if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                        technicalId = payload.getMeta().get("entityId").asText();
                    }
                    if (pet != null) {
                        if (technicalId != null && !technicalId.isBlank()) {
                            updatedPet = tryReservePet(pet, UUID.fromString(technicalId), entity);
                        } else {
                            // If technical id missing, still try to update metadata in-memory (best-effort)
                            try {
                                String currentStatus = pet.getStatus();
                                if (currentStatus == null || currentStatus.isBlank() || currentStatus.equalsIgnoreCase("Available")) {
                                    pet.setStatus("Reserved");
                                    Map<String, Object> metadata = pet.getMetadata();
                                    if (metadata == null) metadata = new HashMap<>();
                                    metadata.put("reservedBy", entity.getRequestId());
                                    pet.setMetadata(metadata);
                                    logger.warn("Found pet by business petId but no technical id present. Cannot persist update without technical id. Pet: {}", petRef);
                                } else {
                                    logger.info("Pet (business id {}) is not available for reservation (current status: {})", petRef, currentStatus);
                                }
                            } catch (Exception ee) {
                                logger.error("Failed to mark pet (business id {}) reserved in-memory: {}", petRef, ee.getMessage(), ee);
                            }
                        }
                    } else {
                        logger.warn("Pet conversion returned null when searching by business petId {}", petRef);
                    }
                } else {
                    logger.warn("No pet found with business petId {} while attempting reservation for request {}", petRef, entity.getRequestId());
                }
            } catch (Exception e) {
                logger.error("Error while searching for Pet by business petId {} for AdoptionRequest {}: {}", petRef, entity.getRequestId(), e.getMessage(), e);
            }
        }

        return entity;
    }

    /**
     * Try to reserve the pet: set status to Reserved and add metadata reservedBy; persist update via entityService.updateItem.
     * Returns true if an update was applied (persisted).
     */
    private boolean tryReservePet(Pet pet, UUID technicalId, AdoptionRequest request) {
        if (pet == null || technicalId == null) return false;
        try {
            String currentStatus = pet.getStatus();
            if (currentStatus == null || currentStatus.isBlank() || currentStatus.equalsIgnoreCase("Available")) {
                pet.setStatus("Reserved");
                Map<String, Object> metadata = pet.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
                metadata.put("reservedBy", request.getRequestId());
                pet.setMetadata(metadata);

                // Persist update
                entityService.updateItem(technicalId, pet).get();
                logger.info("Reserved Pet technicalId={} for AdoptionRequest {}", technicalId, request.getRequestId());
                return true;
            } else {
                logger.info("Pet {} is not available for reservation (current status: {})", technicalId, currentStatus);
            }
        } catch (Exception e) {
            logger.error("Failed to reserve pet technicalId={} for AdoptionRequest {}: {}", technicalId, request.getRequestId(), e.getMessage(), e);
        }
        return false;
    }
}