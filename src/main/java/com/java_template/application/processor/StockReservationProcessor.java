package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class StockReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StockReservationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StockReservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();

        try {
            // Simple approach: sequentially check and reserve product quantities by updating reservedQuantity
            for (ShoppingCart.CartItem it : cart.getItems()) {
                String prodId = it.getProductId();
                Integer qty = it.getQuantity();
                // Lookup product by id (not technicalId). We will search by $.id equals prodId
                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> fut = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", prodId)),
                    true
                );
                com.fasterxml.jackson.databind.node.ArrayNode found = fut.get();
                if (found == null || found.size() == 0) {
                    throw new InsufficientStockException("Product not found: " + prodId);
                }
                ObjectNode pnode = (ObjectNode) found.get(0);
                Integer stock = pnode.has("stockQuantity") ? pnode.get("stockQuantity").asInt() : 0;
                Integer reserved = pnode.has("reservedQuantity") ? pnode.get("reservedQuantity").asInt() : 0;
                int effective = stock - reserved;
                if (effective < qty) {
                    throw new InsufficientStockException("Insufficient stock for product " + prodId + ": required=" + qty + " available=" + effective);
                }
                // Update reservedQuantity
                int newReserved = reserved + qty;
                pnode.put("reservedQuantity", newReserved);
                // Persist update
                String technicalId = pnode.has("technicalId") ? pnode.get("technicalId").asText() : null;
                if (technicalId == null) throw new InsufficientStockException("Product technicalId missing for " + prodId);
                CompletableFuture<UUID> updateFut = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    com.java_template.common.util.Json.mapper().convertValue(pnode, Product.class)
                );
                updateFut.get();
            }
        } catch (InsufficientStockException ex) {
            logger.warn("Reservation failed: {}", ex.getMessage());
            // Mark cart as CHECKOUT_FAILED by persisting update via entityService update
            try {
                if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                    String tid = (String) context.attributes().get("technicalId");
                    com.fasterxml.jackson.databind.node.ObjectNode cartNode = com.java_template.common.util.Json.mapper().convertValue(cart, com.fasterxml.jackson.databind.node.ObjectNode.class);
                    cartNode.put("status", "CHECKOUT_FAILED");
                    entityService.updateItem(ShoppingCart.ENTITY_NAME, String.valueOf(ShoppingCart.ENTITY_VERSION), UUID.fromString(tid), cartNode).whenComplete((id, ex2) -> {
                        if (ex2 != null) logger.error("Failed to update cart status to CHECKOUT_FAILED", ex2);
                    });
                }
            } catch (Exception e) {
                logger.error("Error marking cart checkout failed", e);
            }
        } catch (Exception ex) {
            logger.error("Error during stock reservation", ex);
        }

        return context;
    }

    private static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String msg) { super(msg); }
    }
}
