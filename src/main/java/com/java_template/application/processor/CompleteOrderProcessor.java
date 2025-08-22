package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CompleteOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompleteOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) {
            logger.warn("Order entity is null in execution context");
            return order;
        }

        String status = order.getStatus();
        if (status == null) {
            logger.warn("Order {} has null status, skipping completion", order.getId());
            return order;
        }

        // Only complete orders that are approved or in staff_review (finalization expected)
        if (!"approved".equalsIgnoreCase(status) && !"staff_review".equalsIgnoreCase(status)) {
            logger.info("Order {} in status '{}' is not eligible for completion by this processor", order.getId(), status);
            return order;
        }

        String petBusinessId = order.getPetId();
        if (petBusinessId == null || petBusinessId.isBlank()) {
            logger.warn("Order {} has no petId; marking as completed with note", order.getId());
            String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
            order.setNotes(notes + "Pet not found for completion.");
            order.setStatus("completed");
            return order;
        }

        try {
            // Search for pet by business id (id field)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", petBusinessId)
            );

            ArrayNode results = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            ).join();

            if (results == null || results.size() == 0) {
                logger.warn("No pet found with business id {} for order {}", petBusinessId, order.getId());
                String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(notes + "Pet not found during completion.");
                order.setStatus("completed");
                return order;
            }

            // Expect first match to be the pet to update
            ObjectNode itemNode = (ObjectNode) results.get(0);

            // Attempt to extract technicalId and entity payload from returned node shapes
            String petTechnicalIdStr = null;
            Pet pet = null;

            if (itemNode.has("technicalId")) {
                petTechnicalIdStr = itemNode.get("technicalId").asText(null);
            }

            // The returned structure may be either the entity directly or an envelope with 'entity' field
            if (itemNode.has("entity") && itemNode.get("entity").isObject()) {
                pet = objectMapper.convertValue(itemNode.get("entity"), Pet.class);
            } else {
                // Try converting whole node to Pet (if service returns raw entity)
                pet = objectMapper.convertValue(itemNode, Pet.class);
            }

            if (pet == null) {
                logger.error("Failed to deserialize pet for order {}. Skipping pet update.", order.getId());
                order.setStatus("completed");
                String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(notes + "Failed to deserialize pet for completion.");
                return order;
            }

            // Apply business rule: adopted for 'adopt' type, otherwise 'reserved'
            String newPetStatus = "adopt".equalsIgnoreCase(order.getType()) ? "adopted" : "reserved";
            pet.setStatus(newPetStatus);

            // Update the pet entity using EntityService (we must not update the triggering Order via entityService)
            if (petTechnicalIdStr != null && !petTechnicalIdStr.isBlank()) {
                try {
                    UUID petTechnicalId = UUID.fromString(petTechnicalIdStr);
                    entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        petTechnicalId,
                        pet
                    ).join();
                    logger.info("Updated pet {} (technicalId={}) to status '{}' for order {}", pet.getId(), petTechnicalIdStr, newPetStatus, order.getId());
                } catch (IllegalArgumentException iae) {
                    logger.error("Invalid pet technicalId '{}' for pet {}: {}", petTechnicalIdStr, pet.getId(), iae.getMessage());
                    // Can't update without a valid technical id; still mark order completed but annotate notes
                    String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
                    order.setNotes(notes + "Pet update failed: invalid technical id.");
                }
            } else {
                logger.error("No technicalId available for pet {}. Cannot perform update via EntityService.", pet.getId());
                String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(notes + "Pet update skipped: missing technical id.");
            }

            // Finalize order
            order.setStatus("completed");
            logger.info("Order {} marked as completed", order.getId());
            return order;

        } catch (Exception e) {
            logger.error("Error completing order {}: {}", order.getId(), e.getMessage(), e);
            // Even on error, attempt to mark completed to avoid endless loops; append note
            String notes = order.getNotes() == null ? "" : order.getNotes() + " ";
            order.setNotes(notes + "Completion encountered an error: " + e.getMessage());
            order.setStatus("completed");
            return order;
        }
    }
}