package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DecrementStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DecrementStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DecrementStockProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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
        if (order == null) return null;

        // Defensive: ensure lines exist
        List<Order.Line> lines = order.getLines();
        if (lines == null || lines.isEmpty()) {
            logger.info("Order {} has no lines, nothing to decrement.", order.getOrderId());
            return order;
        }

        for (Order.Line line : lines) {
            if (line == null) continue;
            String sku = line.getSku();
            Integer qty = line.getQty();
            if (sku == null || sku.isBlank() || qty == null || qty <= 0) {
                logger.warn("Skipping invalid line in order {}: sku='{}', qty='{}'.", order.getOrderId(), sku, qty);
                continue;
            }

            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", sku)
                );

                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    condition,
                    true
                );

                List<DataPayload> dataPayloads = itemsFuture.get();
                if (dataPayloads == null || dataPayloads.isEmpty()) {
                    logger.warn("Product with sku {} not found for order {}. Skipping decrement.", sku, order.getOrderId());
                    continue;
                }

                // Use first matching product
                DataPayload payload = dataPayloads.get(0);
                if (payload == null || payload.getData() == null) {
                    logger.warn("Empty payload for product sku {} while processing order {}. Skipping.", sku, order.getOrderId());
                    continue;
                }

                Product product = objectMapper.treeToValue(payload.getData(), Product.class);

                // extract technical id from meta safely
                String technicalId = null;
                if (payload.getMeta() != null && payload.getMeta().has("entityId") && !payload.getMeta().get("entityId").isNull()) {
                    try {
                        technicalId = payload.getMeta().get("entityId").asText();
                    } catch (Exception e) {
                        logger.warn("Failed to read technical entityId meta for product sku {}: {}", sku, e.getMessage());
                    }
                }

                if (product == null) {
                    logger.warn("Deserialized product null for sku {} while processing order {}. Skipping.", sku, order.getOrderId());
                    continue;
                }

                Integer currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                int newQty = currentQty - qty;
                if (newQty < 0) {
                    logger.warn("Decrement would make negative inventory for sku {} (current={}, decrement={}). Clamping to 0.", sku, currentQty, qty);
                    newQty = 0;
                }
                product.setQuantityAvailable(newQty);

                if (technicalId == null || technicalId.isBlank()) {
                    logger.warn("No technical entityId found for product sku {} while processing order {}. Skipping update.", sku, order.getOrderId());
                    continue;
                }

                // Update product in datastore
                CompletableFuture<UUID> updateFuture = entityService.updateItem(UUID.fromString(technicalId), product);
                UUID updatedId = updateFuture.get();
                logger.info("Updated product sku={} quantityAvailable: {} -> {} (entityId={})",
                    sku, currentQty, newQty, updatedId);

            } catch (Exception ex) {
                logger.error("Failed to decrement stock for sku {} on order {}: {}", sku, order.getOrderId(), ex.getMessage(), ex);
                // continue processing other lines; do not fail the whole order processing here
            }
        }

        return order;
    }
}