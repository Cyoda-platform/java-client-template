package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ReserveInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReserveInventoryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReserveInventory for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for reservation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && "CHECKING_OUT".equals(cart.getStatus()) && cart.getLines() != null && !cart.getLines().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Attempt reservation by validating each product has sufficient available quantity
        try {
            boolean allAvailable = true;
            for (com.java_template.application.entity.cart.version_1.CartLine line : cart.getLines()) {
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
                    allAvailable = false;
                    break;
                }
                ObjectNode pNode = (ObjectNode) results.get(0);
                Product p = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Product.class).read(pNode);
                Integer available = p.getQuantityAvailable();
                if (available == null || available < line.getQty()) {
                    logger.warn("Insufficient inventory for sku={} needed={} available={}", line.getSku(), line.getQty(), available);
                    allAvailable = false;
                    break;
                }
            }

            if (!allAvailable) {
                // keep cart in CHECKING_OUT and let UI handle errors
                return cart;
            }

            // Apply reservation: decrement quantityAvailable and increment quantityReserved
            for (com.java_template.application.entity.cart.version_1.CartLine line : cart.getLines()) {
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
                ObjectNode pNode = (ObjectNode) results.get(0);
                Product p = SerializerFactory.createDefault().getDefaultProcessorSerializer().toEntity(Product.class).read(pNode);
                Integer available = p.getQuantityAvailable() == null ? 0 : p.getQuantityAvailable();
                Integer reserved = p.getQuantityReserved() == null ? 0 : p.getQuantityReserved();
                p.setQuantityAvailable(Math.max(0, available - line.getQty()));
                p.setQuantityReserved(reserved + line.getQty());
                p.setUpdatedAt(Instant.now().toString());

                // persist product update
                CompletableFuture<ObjectNode> update = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    java.util.UUID.fromString(p.getTechnicalId()),
                    SerializerFactory.createDefault().getDefaultProcessorSerializer().toObjectNode(p)
                );
                update.get();
            }

            cart.setStatus("RESERVED");
            cart.setExpiresAt(Instant.now().plusSeconds(15 * 60).toString());
            cart.setUpdatedAt(Instant.now().toString());
            return cart;
        } catch (Exception e) {
            logger.error("Exception while reserving inventory", e);
            return cart; // leave unchanged on error
        }
    }
}
