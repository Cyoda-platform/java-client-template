package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        try {
            // 1) Fetch pet by business id (order.petId)
            SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", order.getPetId())
            );

            CompletableFuture<ArrayNode> petItemsFuture = entityService.getItemsByCondition(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                petCondition,
                true
            );

            ArrayNode petItems = petItemsFuture.join();
            if (petItems == null || petItems.isEmpty()) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "pet not found";
                order.setNotes(note);
                logger.info("Order {} validation failed: pet not found {}", order.getId(), order.getPetId());
                return order;
            }

            ObjectNode petNode = (ObjectNode) petItems.get(0);
            Pet pet = objectMapper.convertValue(petNode, Pet.class);
            String petTechnicalId = petNode.has("technicalId") && !petNode.get("technicalId").isNull()
                ? petNode.get("technicalId").asText()
                : null;

            // Check pet availability: use only existing Pet properties (status)
            if (pet.getStatus() == null || !"available".equalsIgnoreCase(pet.getStatus())) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "pet not available";
                order.setNotes(note);
                logger.info("Order {} validation failed: pet not available (status={})", order.getId(), pet.getStatus());
                return order;
            }

            // 2) Fetch user by business id (order.userId)
            SearchConditionRequest userCondition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", order.getUserId())
            );

            CompletableFuture<ArrayNode> userItemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                userCondition,
                true
            );

            ArrayNode userItems = userItemsFuture.join();
            if (userItems == null || userItems.isEmpty()) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "user not found";
                order.setNotes(note);
                logger.info("Order {} validation failed: user not found {}", order.getId(), order.getUserId());
                return order;
            }

            ObjectNode userNode = (ObjectNode) userItems.get(0);
            User user = objectMapper.convertValue(userNode, User.class);

            // 3) Check verification policy: require verification for 'adopt' orders
            if ("adopt".equalsIgnoreCase(order.getType())) {
                Boolean verified = user.getVerified();
                if (verified == null || !verified) {
                    order.setStatus("pending_verification");
                    String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "user not verified";
                    order.setNotes(note);
                    logger.info("Order {} set to pending_verification: user {} not verified", order.getId(), user.getId());
                    return order;
                }
            }

            // 4) Attempt to place a hold on the pet by updating pet.status -> 'held' (update other entity)
            if (petTechnicalId == null || petTechnicalId.isBlank()) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "pet technical id missing";
                order.setNotes(note);
                logger.warn("Order {} cannot obtain pet technical id for pet {}", order.getId(), pet.getId());
                return order;
            }

            // Reload latest pet state by technicalId to reduce race window
            ObjectNode latestPetNode = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(petTechnicalId)
            ).join();

            if (latestPetNode == null || latestPetNode.isEmpty()) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "pet not found by technical id";
                order.setNotes(note);
                logger.info("Order {} validation failed: pet not found by technical id {}", order.getId(), petTechnicalId);
                return order;
            }

            Pet latestPet = objectMapper.convertValue(latestPetNode, Pet.class);
            if (latestPet.getStatus() == null || !"available".equalsIgnoreCase(latestPet.getStatus())) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "pet no longer available";
                order.setNotes(note);
                logger.info("Order {} validation failed: pet no longer available (status={})", order.getId(), latestPet.getStatus());
                return order;
            }

            // Set to held and update pet via entityService (allowed: update other entities)
            latestPet.setStatus("held");

            try {
                CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(petTechnicalId),
                    latestPet
                );
                updateFuture.join();
            } catch (Exception e) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "failed to obtain hold";
                order.setNotes(note);
                logger.error("Order {} failed to place hold on pet {}: {}", order.getId(), latestPet.getId(), e.getMessage(), e);
                return order;
            }

            // Hold created -> move order to payment_pending and set an expiry
            order.setStatus("payment_pending");
            // set a short TTL (15 minutes) for the hold as an expiresAt on the order
            String expiry = Instant.now().plusSeconds(15 * 60).toString();
            order.setExpiresAt(expiry);
            String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "hold placed on pet " + latestPet.getId();
            order.setNotes(note);
            logger.info("Order {} hold placed on pet {} and set to payment_pending", order.getId(), latestPet.getId());

            return order;

        } catch (Exception ex) {
            logger.error("Order {} validation encountered error: {}", order != null ? order.getId() : "unknown", ex.getMessage(), ex);
            if (order != null) {
                order.setStatus("validation_failed");
                String note = (order.getNotes() == null ? "" : order.getNotes() + " | ") + "validation error: " + ex.getMessage();
                order.setNotes(note);
            }
            return order;
        }
    }
}