package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AdoptionHoldProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionHoldProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionHoldProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionOrder for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionOrder.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        try {
            // Only act when the adoption order is approved
            if (order.getStatus() == null || !"approved".equalsIgnoreCase(order.getStatus())) {
                logger.debug("AdoptionHoldProcessor invoked for order {} but status is not 'approved' (status={}). No action taken.", order.getId(), order.getStatus());
                return order;
            }

            // Resolve the Pet by its business id (order.petId)
            if (order.getPetId() == null || order.getPetId().isBlank()) {
                logger.warn("AdoptionOrder {} has no petId; cannot apply hold.", order.getId());
                return order;
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", order.getPetId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = itemsFuture.get(10, TimeUnit.SECONDS);
            if (items == null || items.size() == 0) {
                logger.warn("No Pet found with id={} for AdoptionOrder {}. Cannot place hold.", order.getPetId(), order.getId());
                return order;
            }

            // Use the first matched pet
            JsonNode itemNode = items.get(0);
            String petTechnicalId = null;
            ObjectNode petEntityNode = null;

            if (itemNode.has("technicalId")) {
                petTechnicalId = itemNode.get("technicalId").asText(null);
            }
            if (itemNode.has("entity") && itemNode.get("entity").isObject()) {
                petEntityNode = (ObjectNode) itemNode.get("entity");
            } else if (itemNode.isObject()) {
                petEntityNode = (ObjectNode) itemNode;
            }

            if (petEntityNode == null) {
                logger.warn("Unable to resolve pet entity node for petId={} (order={}).", order.getPetId(), order.getId());
                return order;
            }

            Pet pet = objectMapper.treeToValue(petEntityNode, Pet.class);

            // Only place a hold if pet is currently available
            String currentStatus = pet.getStatus();
            if (currentStatus == null) currentStatus = "";
            if ("available".equalsIgnoreCase(currentStatus)) {
                pet.setStatus("pending");

                if (petTechnicalId == null || petTechnicalId.isBlank()) {
                    logger.warn("Pet technicalId missing for petId={} (order={}); cannot update pet status.", order.getPetId(), order.getId());
                    return order;
                }

                // Update the Pet entity (allowed: we must not update the triggering AdoptionOrder)
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(petTechnicalId),
                    pet
                );

                try {
                    updateFuture.get(10, TimeUnit.SECONDS);
                    logger.info("Placed hold on pet (petId={}, technicalId={}) for adoption order {}. Pet status set to 'pending'.",
                        order.getPetId(), petTechnicalId, order.getId());
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.error("Failed to update Pet {} status to pending for order {}: {}", order.getPetId(), order.getId(), e.getMessage(), e);
                }
            } else if ("pending".equalsIgnoreCase(currentStatus)) {
                // Idempotent: already held
                logger.info("Pet {} is already pending. No action taken for order {}.", order.getPetId(), order.getId());
            } else {
                // Pet not available for hold
                logger.warn("Pet {} status is '{}' and cannot be held for order {}. No change applied.", order.getPetId(), currentStatus, order.getId());
            }

        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            logger.error("Error while attempting to place hold for AdoptionOrder {}: {}", order.getId(), ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.error("Unexpected error in AdoptionHoldProcessor for order {}: {}", order.getId(), ex.getMessage(), ex);
        }

        // Return the order entity unchanged; Cyoda will persist it as part of the workflow where appropriate
        return order;
    }
}