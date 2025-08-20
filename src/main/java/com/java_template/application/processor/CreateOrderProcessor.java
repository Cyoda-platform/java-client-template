package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartLine;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderLine;
import com.java_template.application.entity.order.version_1.Order.Totals;
import com.java_template.application.entity.order.version_1.Order.StateTransition;
import com.java_template.application.entity.product.version_1.Product;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for order creation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && ("CHECKING_OUT".equals(cart.getStatus()) || "RESERVED".equals(cart.getStatus()));
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        try {
            // Validate presence of user and shipping address implicitly required by functional requirements
            if (cart.getUserId() == null || cart.getUserId().isBlank()) {
                logger.warn("Cannot create order without userId");
                return cart;
            }

            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cannot create order from empty cart");
                return cart;
            }

            // Build Order from Cart
            Order order = new Order();
            order.setUserId(cart.getUserId());
            order.setCreatedAt(Instant.now().toString());
            order.setUpdated_at(Instant.now().toString());
            List<OrderLine> orderLines = new ArrayList<>();
            int items = 0;
            double grand = 0.0;
            for (CartLine line : cart.getLines()) {
                OrderLine ol = new OrderLine();
                ol.setSku(line.getSku());
                ol.setName(line.getName());
                ol.setUnitPrice(line.getPrice());
                ol.setQty(line.getQty());
                ol.setLineTotal(line.getLineTotal());
                orderLines.add(ol);
                items += line.getQty();
                grand += line.getLineTotal();
            }
            order.setLines(orderLines);
            // Use setters instead of a non-existent constructor
            Totals totals = new Totals();
            totals.setItems(items);
            totals.setGrand(grand);
            order.setTotals(totals);
            order.setStatus("WAITING_TO_FULFILL");
            order.setState_transitions(new ArrayList<>());

            // Generate an orderNumber by scanning existing orders and taking max+1
            try {
                CompletableFuture<ArrayNode> allOrdersFuture = entityService.getItems(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION)
                );
                ArrayNode allOrders = allOrdersFuture.get();
                int nextOrderNumber = 1;
                if (allOrders != null && allOrders.size() > 0) {
                    // compute max orderNumber present
                    int max = 0;
                    for (int i = 0; i < allOrders.size(); i++) {
                        ObjectNode oNode = (ObjectNode) allOrders.get(i);
                        if (oNode.has("orderNumber")) {
                            try {
                                int val = Integer.parseInt(oNode.get("orderNumber").asText());
                                if (val > max) max = val;
                            } catch (Exception ignored) {}
                        }
                    }
                    nextOrderNumber = max + 1;
                }
                order.setOrderNumber(String.valueOf(nextOrderNumber));
            } catch (Exception e) {
                // fallback if sequence determination fails
                logger.warn("Failed to compute orderNumber via scanning, defaulting to 1", e);
                order.setOrderNumber("1");
            }

            // Persist Order via entityService
            CompletableFuture<UUID> addFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                this.serializer.toObjectNode(order)
            );
            UUID orderId = addFuture.get();
            order.setOrderId(orderId.toString());

            // Inventory adjustments
            if ("RESERVED".equals(cart.getStatus())) {
                // convert reserved -> permanent (best-effort)
                for (CartLine line : cart.getLines()) {
                    try {
                        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.sku", "EQUALS", line.getSku())
                        );
                        CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            condition,
                            true
                        );
                        ArrayNode results = future.get();
                        if (results == null || results.size() == 0) {
                            logger.warn("Product not found for sku={}", line.getSku());
                            continue;
                        }
                        ObjectNode pNode = (ObjectNode) results.get(0);
                        Product p = this.serializer.toEntity(Product.class).read(pNode);

                        // NOTE: Product model in this codebase does not include quantityReserved.
                        // Reservation conversion is therefore best-effort: ensure timestamps updated and do not drive negative inventory.
                        Integer available = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                        // If reserved was already decremented from available at reservation time, we do not change available here.
                        // Ensure invariants
                        if (available < 0) p.setQuantityAvailable(0);
                        p.setUpdated_at(Instant.now().toString());

                        // persist product update with simple retry
                        boolean updated = false;
                        for (int attempt = 0; attempt < 3 && !updated; attempt++) {
                            try {
                                CompletableFuture<ObjectNode> update = entityService.updateItem(
                                    Product.ENTITY_NAME,
                                    String.valueOf(Product.ENTITY_VERSION),
                                    UUID.fromString(p.getSku()),
                                    this.serializer.toObjectNode(p)
                                );
                                update.get();
                                updated = true;
                            } catch (Exception ex) {
                                logger.warn("Retrying product update for sku={} attempt={} due to {}", p.getSku(), attempt + 1, ex.getMessage());
                                // refetch product state before retry
                                try {
                                    CompletableFuture<ArrayNode> refetch = entityService.getItemsByCondition(
                                        Product.ENTITY_NAME,
                                        String.valueOf(Product.ENTITY_VERSION),
                                        SearchConditionRequest.group("AND", Condition.of("$.sku", "EQUALS", line.getSku())),
                                        true
                                    );
                                    ArrayNode ref = refetch.get();
                                    if (ref != null && ref.size() > 0) {
                                        p = this.serializer.toEntity(Product.class).read((ObjectNode) ref.get(0));
                                        p.setUpdated_at(Instant.now().toString());
                                    }
                                } catch (Exception ignore) {}
                            }
                        }

                        if (!updated) logger.error("Failed to persist product update for sku={} after retries", line.getSku());

                    } catch (Exception e) {
                        logger.warn("Exception while converting reservation for sku={}: {}", line.getSku(), e.getMessage());
                    }
                }
            } else {
                // no reservation existed: attempt to decrement available now (atomically via retries)
                for (CartLine line : cart.getLines()) {
                    try {
                        boolean success = false;
                        for (int attempt = 0; attempt < 3 && !success; attempt++) {
                            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                                Condition.of("$.sku", "EQUALS", line.getSku())
                            );
                            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                                Product.ENTITY_NAME,
                                String.valueOf(Product.ENTITY_VERSION),
                                condition,
                                true
                            );
                            ArrayNode results = future.get();
                            if (results == null || results.size() == 0) {
                                logger.warn("Product not found for sku={}", line.getSku());
                                break;
                            }
                            ObjectNode pNode = (ObjectNode) results.get(0);
                            Product p = this.serializer.toEntity(Product.class).read(pNode);
                            Integer available = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                            if (available < line.getQty()) {
                                // Inventory failure - mark order as FAILED and leave cart unchanged
                                order.setStatus("FAILED");
                                try {
                                    CompletableFuture<ObjectNode> updateOrder = entityService.updateItem(
                                        Order.ENTITY_NAME,
                                        String.valueOf(Order.ENTITY_VERSION),
                                        UUID.fromString(order.getOrderId()),
                                        this.serializer.toObjectNode(order)
                                    );
                                    updateOrder.get();
                                } catch (Exception ex) {
                                    logger.warn("Failed to persist failed order status for orderId={}", order.getOrderId());
                                }
                                logger.warn("Insufficient inventory for sku={} when creating order", line.getSku());
                                return cart;
                            }

                            p.setQuantityAvailable(Math.max(0, available - line.getQty()));
                            p.setUpdated_at(Instant.now().toString());

                            try {
                                CompletableFuture<ObjectNode> update = entityService.updateItem(
                                    Product.ENTITY_NAME,
                                    String.valueOf(Product.ENTITY_VERSION),
                                    UUID.fromString(p.getSku()),
                                    this.serializer.toObjectNode(p)
                                );
                                update.get();
                                success = true;
                            } catch (Exception ex) {
                                logger.warn("Conflict updating product sku={} attempt={} : {}", p.getSku(), attempt + 1, ex.getMessage());
                                // on retry, loop will refetch and try again
                            }
                        }
                        if (!success) {
                            logger.error("Failed to decrement inventory for sku={} after retries", line.getSku());
                            // mark order failed
                            order.setStatus("FAILED");
                            try {
                                CompletableFuture<ObjectNode> updateOrder = entityService.updateItem(
                                    Order.ENTITY_NAME,
                                    String.valueOf(Order.ENTITY_VERSION),
                                    UUID.fromString(order.getOrderId()),
                                    this.serializer.toObjectNode(order)
                                );
                                updateOrder.get();
                            } catch (Exception ex) {}
                            return cart;
                        }

                    } catch (Exception e) {
                        logger.error("Exception while decrementing inventory for sku={}: {}", line.getSku(), e.getMessage());
                        return cart;
                    }
                }
            }

            // mark cart converted
            cart.setStatus("CONVERTED");
            cart.setUpdated_at(Instant.now().toString());

            // Append a state transition to the order
            try {
                StateTransition st = new StateTransition();
                st.setFrom("" /* cart status prior to conversion */);
                st.setTo(order.getStatus());
                st.setActor("system");
                st.setTimestamp(Instant.now().toString());
                st.setNote("Order created from cart " + cart.getCartId());
                if (order.getState_transitions() == null) order.setState_transitions(new ArrayList<>());
                order.getState_transitions().add(st);
                // persist order with state transition
                try {
                    entityService.updateItem(
                        Order.ENTITY_NAME,
                        String.valueOf(Order.ENTITY_VERSION),
                        UUID.fromString(order.getOrderId()),
                        this.serializer.toObjectNode(order)
                    ).get();
                } catch (Exception ex) {
                    logger.warn("Failed to persist order state transition for orderId={}", order.getOrderId());
                }
            } catch (Exception ignored) {}

            logger.info("Cart {} converted to Order {}", cart.getCartId(), order.getOrderId());
            return cart;
        } catch (Exception e) {
            logger.error("Exception while creating order", e);
            return cart;
        }
    }
}
