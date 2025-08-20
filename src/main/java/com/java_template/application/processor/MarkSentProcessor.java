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
public class MarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public MarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkSent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity((Class) ObjectNode.class)
            .validate(this::isValidPayload, "Invalid mark sent payload")
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
            if (!"PICKING".equalsIgnoreCase(order.getStatus())) {
                logger.warn("MarkSent called for order {} in status {}", order.getOrderId(), order.getStatus());
                return payload;
            }
            order.setStatus("SENT");
            entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), orderId, order);
            logger.info("Order {} moved to SENT", order.getOrderId());
            payload.put("orderStatus", "SENT");
        } catch (Exception e) {
            logger.error("Error in MarkSentProcessor", e);
        }
        return payload;
    }
}
