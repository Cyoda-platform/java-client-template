package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
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
public class StartPickingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartPickingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StartPickingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartPicking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity((Class) ObjectNode.class)
            .validate(this::isValidPayload, "Invalid start picking payload")
            .map(ctx -> processEntityLogic(ctx))
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayload(ObjectNode payload) {
        return payload != null && payload.hasNonNull("orderId");
    }

    private ObjectNode processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<?> context) {
        ObjectNode payload = (ObjectNode) context.entity();
        try {
            UUID orderId = UUID.fromString(payload.get("orderId").asText());
            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> orderFuture = entityService.getItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), orderId);
            com.fasterxml.jackson.databind.node.ObjectNode orderNode = orderFuture.get();
            Order order = serializer.convert(orderNode, Order.class);
            if (!"WAITING_TO_FULFILL".equalsIgnoreCase(order.getStatus())) {
                logger.warn("StartPicking called for order {} in status {}", order.getOrderId(), order.getStatus());
                return payload;
            }
            order.setStatus("PICKING");
            entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), orderId, order);
            logger.info("Order {} moved to PICKING", order.getOrderId());
            payload.put("orderStatus", "PICKING");
        } catch (Exception e) {
            logger.error("Error in StartPickingProcessor", e);
        }
        return payload;
    }
}
