package com.java_template.application.processor;

import com.java_template.application.entity.adoptionorder.version_1.AdoptionOrder;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AdminApproveAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdminApproveAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdminApproveAdoptionProcessor(SerializerFactory serializerFactory,
                                         EntityService entityService,
                                         ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionOrder.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionOrder entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionOrder> context) {
        AdoptionOrder order = context.entity();
        if (order == null) return null;

        // Idempotency: if already approved and has an approvedDate, do nothing
        if ("approved".equalsIgnoreCase(order.getStatus()) && order.getApprovedDate() != null && !order.getApprovedDate().isBlank()) {
            logger.info("AdoptionOrder {} already approved at {}, skipping.", order.getId(), order.getApprovedDate());
            return order;
        }

        // Find the Pet by business id (petId)
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", order.getPetId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();
            if (results == null || results.size() == 0) {
                logger.warn("Pet with id {} not found for AdoptionOrder {}. Declining order.", order.getPetId(), order.getId());
                order.setStatus("declined");
                String notes = order.getNotes() == null ? "" : order.getNotes() + " | ";
                order.setNotes(notes + "Pet not found during approval");
                return order;
            }

            // Use the first matching pet
            JsonNode petRecord = results.get(0);
            String petTechnicalId = null;
            JsonNode petEntityNode = null;

            if (petRecord.has("technicalId")) {
                petTechnicalId = petRecord.get("technicalId").asText(null);
            }
            if (petRecord.has("entity")) {
                petEntityNode = petRecord.get("entity");
            } else {
                petEntityNode = petRecord;
            }

            Pet pet = null;
            if (petEntityNode != null && !petEntityNode.isNull()) {
                pet = objectMapper.treeToValue(petEntityNode, Pet.class);
            }

            if (pet == null) {
                logger.warn("Unable to deserialize Pet for AdoptionOrder {}. Declining order.", order.getId());
                order.setStatus("declined");
                String notes = order.getNotes() == null ? "" : order.getNotes() + " | ";
                order.setNotes(notes + "Pet data unreadable during approval");
                return order;
            }

            // If pet is not available, decline the order
            if (pet.getStatus() == null || !"available".equalsIgnoreCase(pet.getStatus())) {
                logger.info("Pet {} status is '{}', not available. Declining AdoptionOrder {}", pet.getId(), pet.getStatus(), order.getId());
                order.setStatus("declined");
                String notes = order.getNotes() == null ? "" : order.getNotes() + " | ";
                order.setNotes(notes + "Pet not available at approval time");
                return order;
            }

            // Approve the order
            String now = Instant.now().toString();
            order.setApprovedDate(now);
            order.setStatus("approved");
            String notes = order.getNotes() == null ? "" : order.getNotes() + " | ";
            order.setNotes(notes + "Approved by admin at " + now);

            // Hold the pet: set pet.status = "pending" and update via EntityService
            pet.setStatus("pending");

            if (petTechnicalId != null && !petTechnicalId.isBlank()) {
                try {
                    UUID petUuid = UUID.fromString(petTechnicalId);
                    CompletableFuture<UUID> updated = entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        petUuid,
                        pet
                    );
                    updated.join();
                    logger.info("Pet {} (technicalId={}) set to pending for AdoptionOrder {}", pet.getId(), petTechnicalId, order.getId());
                } catch (IllegalArgumentException ex) {
                    // technicalId not a valid UUID - attempt best-effort by logging and skipping update
                    logger.error("Pet technicalId '{}' is not a valid UUID. Pet status set in-memory but could not be persisted via EntityService.", petTechnicalId, ex);
                    // Note: The AdoptionOrder will still be updated/persisted by Cyoda; pet update could not be persisted.
                } catch (Exception ex) {
                    logger.error("Failed to update Pet {} to pending for AdoptionOrder {}: {}", pet.getId(), order.getId(), ex.getMessage(), ex);
                }
            } else {
                logger.warn("No technicalId for Pet {} found. Pet status set in-memory but cannot be persisted via EntityService.", pet.getId());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while processing AdminApproveAdoptionProcessor for order {}: {}", order.getId(), ex.getMessage(), ex);
            // On unexpected error, fail-safe: set order to under_review to allow manual handling
            order.setStatus("under_review");
            String notes = order.getNotes() == null ? "" : order.getNotes() + " | ";
            order.setNotes(notes + "Approval encountered error: " + ex.getMessage());
            return order;
        }

        // Return the modified order; Cyoda will persist this entity
        return order;
    }
}