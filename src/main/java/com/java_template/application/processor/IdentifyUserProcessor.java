package com.java_template.application.processor;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class IdentifyUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IdentifyUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IdentifyUserProcessor(SerializerFactory serializerFactory,
                                 EntityService entityService,
                                 ObjectMapper objectMapper) {
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
        if (user == null) return null;

        // Business rule: If user presents valid credentials (handled elsewhere),
        // mark identificationStatus as IDENTIFIED. If already IDENTIFIED, do nothing.
        if ("IDENTIFIED".equalsIgnoreCase(user.getIdentificationStatus())) {
            logger.info("User {} already IDENTIFIED", user.getId());
            return user;
        }

        logger.info("Identifying user {}", user.getId());
        user.setIdentificationStatus("IDENTIFIED");

        // Link anonymous carts: find carts with no userId (anonymous) and assign to this user.
        // We fetch all carts and perform an in-memory filter for userId == null or blank,
        // then update those carts to reference the identified user.
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            if (items == null || items.isEmpty()) {
                logger.debug("No carts found to link for user {}", user.getId());
                return user;
            }

            List<CompletableFuture<UUID>> updateFutures = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                ObjectNode node = (ObjectNode) items.get(i);
                try {
                    Cart cart = objectMapper.treeToValue(node, Cart.class);
                    // Only link carts that appear anonymous (userId null or blank)
                    if (cart != null && (cart.getUserId() == null || cart.getUserId().isBlank())) {
                        // set the cart's userId to this user's id
                        cart.setUserId(user.getId());
                        try {
                            UUID technicalId = UUID.fromString(cart.getId());
                            CompletableFuture<UUID> updatedId = entityService.updateItem(
                                Cart.ENTITY_NAME,
                                String.valueOf(Cart.ENTITY_VERSION),
                                technicalId,
                                cart
                            );
                            updateFutures.add(updatedId);
                            logger.info("Scheduling update of anon cart {} to user {}", cart.getId(), user.getId());
                        } catch (IllegalArgumentException iae) {
                            // If cart.id is not a UUID, skip updating that cart (cannot obtain technicalId)
                            logger.warn("Skipping cart update - invalid cart.id as UUID: {}", cart.getId());
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to parse/handle cart item while linking anon carts: {}", ex.getMessage());
                }
            }

            // Wait for all update operations to be submitted/completed
            for (CompletableFuture<UUID> f : updateFutures) {
                try {
                    f.get();
                } catch (Exception ex) {
                    logger.warn("Failed updating cart for user {}: {}", user.getId(), ex.getMessage());
                }
            }

        } catch (Exception ex) {
            logger.warn("Error while linking anonymous carts for user {}: {}", user.getId(), ex.getMessage());
        }

        return user;
    }
}