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
            order.setTotals(new Totals(items, grand));
            order.setStatus("WAITING_TO_FULFILL");
            order.setState_transitions(new ArrayList<>());

            // Persist Order via entityService
            CompletableFuture<UUID> addFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(order)
            );
            UUID orderId = addFuture.get();
            order.setOrderId(orderId.toString());

            // Inventory adjustments
            if ("RESERVED".equals(cart.getStatus())) {
                // convert reserved -> permanent (decrement reserved counts)
                for (CartLine line : cart.getLines()) {
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
                    Product p = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Product.class).read(pNode);
                    Integer reserved = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                    // In reservation conversion we decrease quantityReserved (note: entity Product lacks quantityReserved in model so we'll best-effort)
                    // Ensure we do not go negative
                    // As Product entity currently doesn't expose quantityReserved (older model), we'll only set updated_at here.
                    p.setUpdated_at(Instant.now().toString());
                    CompletableFuture<ObjectNode> update = entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(p.getSku()),
                        SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                    );
                    update.get();
                }
            } else {
                // no reservation existed: attempt to decrement available now
                for (CartLine line : cart.getLines()) {
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
                    Product p = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Product.class).read(pNode);
                    Integer available = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                    if (available < line.getQty()) {
                        // Inventory failure - mark order as FAILED and leave cart unchanged
                        order.setStatus("FAILED");
                        CompletableFuture<ObjectNode> updateOrder = entityService.updateItem(
                            Order.ENTITY_NAME,
                            String.valueOf(Order.ENTITY_VERSION),
                            UUID.fromString(order.getOrderId()),
                            SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(order)
                        );
                        updateOrder.get();
                        logger.warn("Insufficient inventory for sku={} when creating order", line.getSku());
                        return cart;
                    }
                    p.setQuantityAvailable(Math.max(0, available - line.getQty()));
                    p.setUpdated_at(Instant.now().toString());
                    CompletableFuture<ObjectNode> update = entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(p.getSku()),
                        SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                    );
                    update.get();
                }
            }

            // mark cart converted
            cart.setStatus("CONVERTED");
            cart.setUpdated_at(Instant.now().toString());
            logger.info("Cart {} converted to Order {}", cart.getCartId(), order.getOrderId());
            return cart;
        } catch (Exception e) {
            logger.error("Exception while creating order", e);
            return cart;
        }
    }
}
