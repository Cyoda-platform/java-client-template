package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FulfillmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FulfillmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FulfillmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            // Only process fulfillment when order is in expected state or proceed regardless but check availability
            // Find Pet by business petId (Pet.petId)
            String targetPetId = order.getPetId();
            if (targetPetId == null || targetPetId.isBlank()) {
                logger.warn("Order {} has no petId, cancelling order", order.getOrderId());
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Missing petId during fulfillment.");
                return order;
            }

            // Build simple search condition: $.petId EQUALS targetPetId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", targetPetId)
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No pet found for petId={} referenced by order={}. Cancelling order.", targetPetId, order.getOrderId());
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Pet not found for fulfillment.");
                return order;
            }

            // Use first matching pet
            DataPayload petPayload = dataPayloads.get(0);
            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);

            if (pet == null) {
                logger.error("Failed to map pet payload to Pet object for petId={}", targetPetId);
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Failed to resolve pet during fulfillment.");
                return order;
            }

            String petStatus = pet.getStatus();
            if (petStatus == null || !petStatus.equalsIgnoreCase("AVAILABLE")) {
                logger.info("Pet {} is not available (status={}) for order={}, cancelling order.", pet.getPetId(), petStatus, order.getOrderId());
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Pet not available for fulfillment.");
                return order;
            }

            // Reserve/mark the pet as adopted or sold based on order type
            String orderType = order.getType();
            if ("adoption".equalsIgnoreCase(orderType)) {
                pet.setStatus("ADOPTED");
            } else {
                pet.setStatus("SOLD");
            }

            // Persist pet update using technicalId from payload meta
            String technicalId = null;
            JsonNode meta = petPayload.getMeta();
            if (meta != null && meta.has("entityId")) {
                technicalId = meta.get("entityId").asText();
            }

            if (technicalId == null || technicalId.isBlank()) {
                logger.error("Missing technical entityId for pet with petId={}. Cannot update pet entity.", pet.getPetId());
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Missing technical id for pet; could not reserve.");
                return order;
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), pet);
            UUID updatedId = updatedIdFuture.get();
            logger.info("Updated pet entity id={} for petId={} status={}", updatedId, pet.getPetId(), pet.getStatus());

            // Update order status to COMPLETED
            order.setStatus("COMPLETED");
            String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
            order.setNotes(existingNotes + "Fulfillment completed. Pet reserved.");

            return order;
        } catch (Exception ex) {
            logger.error("Error during fulfillment processing for order {}: {}", order != null ? order.getOrderId() : "unknown", ex.getMessage(), ex);
            if (order != null) {
                order.setStatus("CANCELLED");
                String existingNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(existingNotes + "Fulfillment failed: " + ex.getMessage());
            }
            return order;
        }
    }
}