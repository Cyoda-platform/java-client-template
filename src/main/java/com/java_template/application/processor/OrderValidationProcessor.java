package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderValidationProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
            // Only operate on newly initiated orders or as appropriate
            String currentStatus = order.getStatus() == null ? "" : order.getStatus().trim().toLowerCase();

            // Basic sanity: if already moved beyond validation, do nothing
            if (!currentStatus.isEmpty() && !currentStatus.equals("initiated") && !currentStatus.equals("validation_failed")) {
                logger.info("Order {} in status {}, skipping validation logic", order.getId(), order.getStatus());
                return order;
            }

            // 1) Validate pet availability
            boolean petAvailable = false;
            Optional<ObjectNode> petNodeOpt = tryFetchEntityNode(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, order.getPetId());
            if (petNodeOpt.isPresent()) {
                ObjectNode petNode = petNodeOpt.get();
                String petStatus = getStringSafely(petNode, "status");
                String mediaStatus = getStringSafely(petNode, "mediaStatus");
                petAvailable = "available".equalsIgnoreCase(petStatus) && ("processed".equalsIgnoreCase(mediaStatus) || mediaStatus == null);
                if (!petAvailable) {
                    order.setStatus("validation_failed");
                    String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                    order.setNotes(prevNotes + "Pet not available for order at validation time.");
                    logger.info("Order {} validation failed: pet not available (status={}, mediaStatus={})", order.getId(), petStatus, mediaStatus);
                    return order;
                }
            } else {
                // Pet not found by technical id (or id not a UUID) - mark validation failed
                order.setStatus("validation_failed");
                String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(prevNotes + "Pet not found for id: " + order.getPetId());
                logger.warn("Order {} validation failed: pet not found (petId={})", order.getId(), order.getPetId());
                return order;
            }

            // 2) Check user verification if required by policy
            boolean requiresVerification = requiresVerificationForType(order.getType());
            if (requiresVerification) {
                Optional<ObjectNode> userNodeOpt = tryFetchEntityNode(User.ENTITY_NAME, User.ENTITY_VERSION, order.getUserId());
                boolean userVerified = false;
                if (userNodeOpt.isPresent()) {
                    ObjectNode userNode = userNodeOpt.get();
                    // Prefer boolean 'verified' field, fallback to 'verificationStatus'
                    if (userNode.has("verified") && !userNode.get("verified").isNull()) {
                        userVerified = userNode.get("verified").asBoolean(false);
                    } else {
                        String verificationStatus = getStringSafely(userNode, "verificationStatus");
                        userVerified = "verified".equalsIgnoreCase(verificationStatus);
                    }
                } else {
                    // If user not found, treat as not verified
                    userVerified = false;
                }

                if (!userVerified) {
                    order.setStatus("pending_verification");
                    String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                    order.setNotes(prevNotes + "User verification required before payment.");
                    logger.info("Order {} requires verification; moving to pending_verification", order.getId());
                    return order;
                }
            }

            // 3) Attempt to create a simple hold by updating pet status -> 'held'
            // NOTE: This is a best-effort naive implementation. Real atomic holds should be implemented
            // with DB row locks or a dedicated Hold entity with unique constraint / TTL.
            try {
                UUID petTechnicalId = UUID.fromString(order.getPetId());
                Optional<ObjectNode> petNodeOpt2 = tryFetchEntityNode(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, order.getPetId());
                if (petNodeOpt2.isPresent()) {
                    ObjectNode petNode = petNodeOpt2.get();
                    String existingStatus = getStringSafely(petNode, "status");
                    if (!"available".equalsIgnoreCase(existingStatus)) {
                        // Someone else changed pet status - fail validation
                        order.setStatus("validation_failed");
                        String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                        order.setNotes(prevNotes + "Failed to obtain hold; pet status changed to " + existingStatus);
                        logger.info("Order {} failed to obtain hold: pet status is {}", order.getId(), existingStatus);
                        return order;
                    }

                    petNode.put("status", "held");
                    petNode.put("updatedAt", Instant.now().toString());
                    try {
                        CompletableFuture<UUID> fut = entityService.updateItem(
                                Pet.ENTITY_NAME,
                                String.valueOf(Pet.ENTITY_VERSION),
                                petTechnicalId,
                                petNode
                        );
                        fut.join();
                        // update successful - mark order for payment
                        order.setStatus("payment_pending");
                        String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                        order.setNotes(prevNotes + "Hold placed on pet.");
                        logger.info("Order {} placed hold on pet {}", order.getId(), order.getPetId());
                        return order;
                    } catch (Exception e) {
                        logger.warn("Failed to update pet to held for order {}: {}", order.getId(), e.getMessage());
                        order.setStatus("validation_failed");
                        String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                        order.setNotes(prevNotes + "Failed to obtain hold due to update error.");
                        return order;
                    }
                } else {
                    order.setStatus("validation_failed");
                    String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                    order.setNotes(prevNotes + "Pet not found when attempting to create hold.");
                    logger.warn("Order {} pet not found when attempting hold (petId={})", order.getId(), order.getPetId());
                    return order;
                }
            } catch (IllegalArgumentException iae) {
                // petId is not a UUID - cannot perform entityService update; fall back to optimistic status change on order only
                logger.warn("Order {} petId is not a UUID, skipping external pet update; marking order payment_pending optimistically", order.getId());
                order.setStatus("payment_pending");
                String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(prevNotes + "Optimistic hold assumed (petId not UUID).");
                return order;
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while validating order {}: {}", order == null ? "<null>" : order.getId(), ex.getMessage(), ex);
            if (order != null) {
                order.setStatus("validation_failed");
                String prevNotes = order.getNotes() == null ? "" : order.getNotes() + " ";
                order.setNotes(prevNotes + "Validation encountered unexpected error.");
            }
            return order;
        }
    }

    private boolean requiresVerificationForType(String type) {
        if (type == null) return false;
        // By policy: adoption requires verification; purchase/reserve may not
        return "adopt".equalsIgnoreCase(type);
    }

    private Optional<ObjectNode> tryFetchEntityNode(String entityModel, int entityVersion, String technicalIdStr) {
        if (technicalIdStr == null || technicalIdStr.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID technicalId = UUID.fromString(technicalIdStr);
            CompletableFuture<ObjectNode> future = entityService.getItem(entityModel, String.valueOf(entityVersion), technicalId);
            ObjectNode node = future.join();
            return Optional.ofNullable(node);
        } catch (IllegalArgumentException iae) {
            // Not a UUID - cannot fetch using EntityService in this environment
            logger.debug("Identifier {} for model {} is not a UUID, cannot fetch via EntityService", technicalIdStr, entityModel);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to fetch entity {} v{} id={}: {}", entityModel, entityVersion, technicalIdStr, e.getMessage());
            return Optional.empty();
        }
    }

    private String getStringSafely(ObjectNode node, String fieldName) {
        if (node == null || fieldName == null) return null;
        try {
            if (!node.has(fieldName) || node.get(fieldName).isNull()) return null;
            return node.get(fieldName).asText();
        } catch (Exception e) {
            return null;
        }
    }
}