package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReleaseStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReleaseStockProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReleaseStock for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Order> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            if (order.getItems() != null) {
                for (Order.OrderItem it : order.getItems()) {
                    try {
                        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> fut = entityService.getItemsByCondition(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.id", "EQUALS", it.getProductId())),
                            true
                        );
                        com.fasterxml.jackson.databind.node.ArrayNode found = fut.get();
                        if (found == null || found.size() == 0) {
                            logger.warn("Product not found to release reservation: {}", it.getProductId());
                            continue;
                        }
                        ObjectNode pnode = (ObjectNode) found.get(0);
                        int reserved = pnode.has("reservedQuantity") ? pnode.get("reservedQuantity").asInt() : 0;
                        int newReserved = Math.max(0, reserved - it.getQuantity());
                        pnode.put("reservedQuantity", newReserved);
                        String prodTid = pnode.has("technicalId") ? pnode.get("technicalId").asText() : null;
                        if (prodTid != null) {
                            entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), UUID.fromString(prodTid), com.java_template.common.util.Json.mapper().convertValue(pnode, Product.class)).whenComplete((id, ex) -> {
                                if (ex != null) logger.error("Failed to release product reservation for {}", it.getProductId(), ex);
                            });
                        }
                    } catch (Exception ex) {
                        logger.error("Error releasing reservation for product {}", it.getProductId(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error in ReleaseStockProcessor", ex);
        }
        return context;
    }
}
