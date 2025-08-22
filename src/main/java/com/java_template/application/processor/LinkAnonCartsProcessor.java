package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
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

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class LinkAnonCartsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LinkAnonCartsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LinkAnonCartsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        // Only act when user has been identified
        if (user.getIdentificationStatus() == null || !"IDENTIFIED".equalsIgnoreCase(user.getIdentificationStatus())) {
            logger.debug("User {} is not IDENTIFIED, skipping linking carts.", user.getId());
            return user;
        }

        try {
            // Fetch all carts and filter in-memory for anonymous carts (userId missing/null/blank)
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION)
            );
            ArrayNode carts = itemsFuture.join();
            if (carts == null || carts.isEmpty()) {
                logger.debug("No carts found to evaluate for linking.");
                return user;
            }

            int linkedCount = 0;
            Iterator<JsonNode> it = carts.elements();
            while (it.hasNext()) {
                JsonNode itemNode = it.next();
                // Support two possible shapes: wrapper { "technicalId": "...", "entity": { ... } } or direct entity node
                ObjectNode cartNode;
                String technicalIdCandidate = null;

                if (itemNode.has("entity") && itemNode.get("entity").isObject()) {
                    cartNode = (ObjectNode) itemNode.get("entity");
                    if (itemNode.has("technicalId")) {
                        technicalIdCandidate = itemNode.get("technicalId").asText(null);
                    }
                } else if (itemNode.isObject()) {
                    cartNode = (ObjectNode) itemNode;
                    if (itemNode.has("technicalId")) {
                        technicalIdCandidate = itemNode.get("technicalId").asText(null);
                    }
                } else {
                    // unexpected shape, skip
                    continue;
                }

                if (cartNode == null) continue;

                // Determine current userId on cart
                String cartUserId = null;
                if (cartNode.has("userId") && !cartNode.get("userId").isNull()) {
                    cartUserId = cartNode.get("userId").asText(null);
                }

                // Skip carts already linked
                if (cartUserId != null && !cartUserId.isBlank()) {
                    continue;
                }

                // Skip converted carts (immutable)
                String status = cartNode.has("status") && !cartNode.get("status").isNull() ? cartNode.get("status").asText("") : "";
                if ("CONVERTED".equalsIgnoreCase(status)) {
                    continue;
                }

                // Set userId on cart to the identified user's id
                cartNode.put("userId", user.getId());

                // Ensure we have a technicalId to call update; try to infer from entity id if necessary
                String technicalId = technicalIdCandidate;
                if ((technicalId == null || technicalId.isBlank()) && cartNode.has("id") && !cartNode.get("id").isNull()) {
                    technicalId = cartNode.get("id").asText(null);
                }
                if (technicalId == null || technicalId.isBlank()) {
                    logger.warn("Cannot determine technicalId for cart entity, skipping update. Cart node: {}", cartNode);
                    continue;
                }

                // Try update other entity (Cart). Convert technicalId to UUID if possible.
                try {
                    UUID techIdUuid = UUID.fromString(technicalId);
                    CompletableFuture<UUID> updated = entityService.updateItem(
                        Cart.ENTITY_NAME,
                        String.valueOf(Cart.ENTITY_VERSION),
                        techIdUuid,
                        cartNode
                    );
                    updated.join();
                    linkedCount++;
                } catch (IllegalArgumentException iae) {
                    // technicalId is not a UUID - attempt to log and skip
                    logger.warn("TechnicalId for cart is not a valid UUID ({}), skipping update.", technicalId);
                } catch (Exception ex) {
                    logger.error("Failed to update cart with technicalId {}: {}", technicalId, ex.getMessage(), ex);
                }
            }

            logger.info("LinkAnonCartsProcessor linked {} carts to user {}", linkedCount, user.getId());
        } catch (Exception ex) {
            logger.error("Error while linking anonymous carts for user {}: {}", user.getId(), ex.getMessage(), ex);
        }

        return user;
    }
}