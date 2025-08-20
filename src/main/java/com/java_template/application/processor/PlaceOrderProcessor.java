package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.java_template.common.config.Config.*;

@Component
public class PlaceOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PlaceOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    // simple in-memory sequence per day - acceptable for prototype but should be replaced in prod
    private final AtomicInteger dailySequence = new AtomicInteger(1);
    private String lastSequenceDate = null;

    public PlaceOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PlaceOrder for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ObjectNode.class)
            .validate(this::isValidPayload, "Invalid place order payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayload(ObjectNode payload) {
        if (payload == null) return false;
        if (!payload.hasNonNull("cartId")) return false;
        if (!payload.hasNonNull("userId")) return false;
        if (!payload.hasNonNull("addressId")) return false;
        return true;
    }

    private ObjectNode processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ObjectNode> context) {
        ObjectNode payload = context.entity();
        try {
            String cartId = payload.get("cartId").asText();
            // fetch cart
            CompletableFuture<ObjectNode> cartFuture = entityService.getItem(Cart.ENTITY_NAME, String.valueOf(Cart.ENTITY_VERSION), UUID.fromString(cartId));
            ObjectNode cartNode = cartFuture.get();
            Cart cart = context.serializer().convert(cartNode, Cart.class);

            // validate stock
            List<String> failures = new ArrayList<>();
            for (Cart.CartLine line : cart.getLines()) {
                String sku = line.getSku();
                SearchConditionRequest cond = SearchConditionRequest.group("AND", Condition.of("$.sku", "IEQUALS", sku));
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> prodFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), cond, true);
                com.fasterxml.jackson.databind.node.ArrayNode prods = prodFuture.get();
                if (prods == null || prods.size() == 0) {
                    failures.add(sku + ":SKU_NOT_FOUND");
                    continue;
                }
                ObjectNode p = (ObjectNode) prods.get(0);
                Product product = context.serializer().convert(p, Product.class);
                if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < line.getQty()) {
                    failures.add(sku + ":INSUFFICIENT_STOCK");
                }
            }

            if (!failures.isEmpty()) {
                logger.warn("PlaceOrder aborted due to stock failures: {}", failures);
                // Attach failures to payload for visibility
                payload.putPOJO("stockFailures", failures);
                return payload;
            }

            // Build order snapshot
            Order order = new Order();
            order.setOrderId(null);
            // allocate orderNumber
            String today = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (!today.equals(lastSequenceDate)) {
                dailySequence.set(1);
                lastSequenceDate = today;
            }
            int seq = dailySequence.getAndIncrement();
            String orderNumber = String.format("ORD-%s-%04d", today, seq);
            order.setOrderNumber(orderNumber);
            order.setUserId(payload.get("userId").asText());
            order.setShippingAddressId(payload.get("addressId").asText());

            List<Order.OrderLine> olines = new ArrayList<>();
            int items = 0;
            double grand = 0.0;
            for (Cart.CartLine line : cart.getLines()) {
                Order.OrderLine ol = new Order.OrderLine();
                ol.setSku(line.getSku());
                ol.setName(line.getName());
                ol.setUnitPrice(line.getUnitPrice());
                ol.setQty(line.getQty());
                ol.setLineTotal(line.getUnitPrice() * line.getQty());
                items += ol.getQty();
                grand += ol.getLineTotal();
                olines.add(ol);
            }
            Order.Totals totals = new Order.Totals();
            totals.setItems(items);
            totals.setGrand(grand);
            order.setLines(olines);
            order.setTotals(totals);
            order.setStatus("WAITING_TO_FULFILL");
            order.setCreatedAt(OffsetDateTime.now());

            // persist order
            CompletableFuture<UUID> orderIdFuture = entityService.addItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), order);
            UUID orderTechnicalId = orderIdFuture.get();
            logger.info("Order created with technicalId {} and orderNumber {}", orderTechnicalId, orderNumber);

            // Decrement stock (simple approach)
            for (Order.OrderLine ol : olines) {
                SearchConditionRequest cond = SearchConditionRequest.group("AND", Condition.of("$.sku", "IEQUALS", ol.getSku()));
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> prodFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), cond, true);
                com.fasterxml.jackson.databind.node.ArrayNode prods = prodFuture.get();
                if (prods == null || prods.size() == 0) continue;
                ObjectNode p = (ObjectNode) prods.get(0);
                Product product = context.serializer().convert(p, Product.class);
                int remaining = product.getQuantityAvailable() - ol.getQty();
                product.setQuantityAvailable(remaining);
                // update product
                String technicalId = p.get("technicalId").asText();
                entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), UUID.fromString(technicalId), product);
            }

            // attach order technical id to payload
            payload.put("orderTechnicalId", orderTechnicalId.toString());
            payload.put("orderNumber", orderNumber);
            payload.put("orderStatus", order.getStatus());

            return payload;

        } catch (Exception e) {
            logger.error("Error in PlaceOrderProcessor", e);
        }
        return payload;
    }
}
