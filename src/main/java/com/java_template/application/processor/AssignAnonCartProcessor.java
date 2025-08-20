package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.CartLine;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class AssignAnonCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssignAnonCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AssignAnonCartProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AssignAnonCart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user state for assignment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && "IDENTIFIED".equals(user.getIdentityState());
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        try {
            // Find anonymous cart(s) by user session id stored in user.technicalId or similar
            // We'll attempt to find carts with null userId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.userId", "EQUALS", "null")
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode carts = future.get();
            if (carts == null || carts.size() == 0) return user;

            // Strategy: preserve newest cart (by updatedAt)
            ObjectNode newest = null;
            for (int i = 0; i < carts.size(); i++) {
                ObjectNode c = (ObjectNode) carts.get(i);
                if (newest == null) newest = c;
                else {
                    String t1 = c.has("updatedAt") ? c.get("updatedAt").asText() : null;
                    String t2 = newest.has("updatedAt") ? newest.get("updatedAt").asText() : null;
                    if (t1 != null && t2 != null && t1.compareTo(t2) > 0) newest = c;
                }
            }

            if (newest == null) return user;
            Cart anonCart = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Cart.class).read(newest);

            // If user has existing active cart, merge lines (prefer newest cart)
            SearchConditionRequest userCartCond = SearchConditionRequest.group("AND",
                Condition.of("$.userId", "EQUALS", user.getUserId())
            );
            CompletableFuture<ArrayNode> userCartFuture = entityService.getItemsByCondition(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                userCartCond,
                true
            );
            ArrayNode userCarts = userCartFuture.get();
            Cart userCart = null;
            if (userCarts != null && userCarts.size() > 0) {
                userCart = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Cart.class).read((ObjectNode) userCarts.get(0));
            }

            if (userCart == null) {
                // Assign anon cart to user
                anonCart.setUserId(user.getUserId());
                anonCart.setUpdatedAt(Instant.now().toString());
                // persist update
                entityService.updateItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    java.util.UUID.fromString(anonCart.getTechnicalId()),
                    SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(anonCart)
                ).get();
            } else {
                // Merge: prefer newest cart (anonCart) into userCart
                for (CartLine line : anonCart.getLines()) {
                    boolean found = false;
                    for (CartLine ul : userCart.getLines()) {
                        if (ul.getSku().equals(line.getSku())) {
                            ul.setQty(ul.getQty() + line.getQty());
                            ul.setLineTotal(ul.getLineTotal() + line.getLineTotal());
                            found = true;
                            break;
                        }
                    }
                    if (!found) userCart.getLines().add(line);
                }
                userCart.setUpdatedAt(Instant.now().toString());
                entityService.updateItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    java.util.UUID.fromString(userCart.getTechnicalId()),
                    SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(userCart)
                ).get();

                // Delete or archive anon cart (we'll delete)
                entityService.deleteItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    java.util.UUID.fromString(anonCart.getTechnicalId())
                ).get();
            }
        } catch (Exception e) {
            logger.error("Exception while assigning anon cart to user", e);
        }
        return user;
    }
}
