package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.CartLine;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.OrderLine;
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
            // Build Order from Cart
            Order order = new Order();
            order.setUserId(cart.getUserId());
            order.setCreatedAt(Instant.now().toString());
            order.setUpdatedAt(Instant.now().toString());
            List<OrderLine> orderLines = new ArrayList<>();
            int items = 0;
            double grand = 0.0;
            for (CartLine line : cart.getLines()) {
                OrderLine ol = new OrderLine();
                ol.setSku(line.getSku());
                ol.setName(line.getName());
                ol.setUnitPrice(line.getUnitPrice());
                ol.setQty(line.getQty());
                ol.setLineTotal(line.getLineTotal());
                orderLines.add(ol);
                items += line.getQty();
                grand += line.getLineTotal();
            }
            order.setLines(orderLines);
            order.setTotals(new com.java_template.application.entity.order.version_1.OrderTotals(items, grand));
            order.setStatus("WAITING_TO_FULFILL");
            order.setStateTransitions(new ArrayList<>());

            // Persist Order via entityService
            CompletableFuture<UUID> addFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(order)
            );
            UUID orderId = addFuture.get();
            order.setTechnicalId(orderId.toString());

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
                    Integer reserved = p.getQuantityReserved() == null ? 0 : p.getQuantityReserved();
                    p.setQuantityReserved(Math.max(0, reserved - line.getQty()));
                    p.setUpdatedAt(Instant.now().toString());
                    CompletableFuture<ObjectNode> update = entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(p.getTechnicalId()),
                        SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                    );
                    update.get();
                }
            } else {
                // no reservation existed: decrement available now
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
                            UUID.fromString(order.getTechnicalId()),
                            SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(order)
                        );
                        updateOrder.get();
                        logger.warn("Insufficient inventory for sku={} when creating order", line.getSku());
                        return cart;
                    }
                    p.setQuantityAvailable(Math.max(0, available - line.getQty()));
                    p.setUpdatedAt(Instant.now().toString());
                    CompletableFuture<ObjectNode> update = entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(p.getTechnicalId()),
                        SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                    );
                    update.get();
                }
            }

            // mark cart converted
            cart.setStatus("CONVERTED");
            cart.setUpdatedAt(Instant.now().toString());
            logger.info("Cart {} converted to Order {}", cart.getCartId(), order.getTechnicalId());
            return cart;
        } catch (Exception e) {
            logger.error("Exception while creating order", e);
            return cart;
        }
    }
}
