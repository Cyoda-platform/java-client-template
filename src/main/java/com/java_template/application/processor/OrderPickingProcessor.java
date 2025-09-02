package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class OrderPickingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderPickingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderPickingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order picking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderPicking)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.isValid();
    }

    private Order processOrderPicking(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        // Validate order is in WAITING_TO_FULFILL state
        String currentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"WAITING_TO_FULFILL".equals(currentState)) {
            throw new IllegalStateException("Order must be in WAITING_TO_FULFILL state to start picking");
        }

        // Validate all order lines have products in stock (simplified validation)
        if (order.getLines() == null || order.getLines().isEmpty()) {
            throw new IllegalArgumentException("Order must have line items");
        }

        for (Order.OrderLine line : order.getLines()) {
            if (line.getQty() == null || line.getQty() <= 0) {
                throw new IllegalArgumentException("Invalid quantity for line: " + line.getSku());
            }
        }

        // Set updatedAt timestamp
        order.setUpdatedAt(Instant.now());

        logger.info("Order {} picking started", order.getOrderId());
        return order;
    }
}
