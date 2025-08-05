package com.java_template.application.processor;

import com.java_template.application.entity.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public OrderProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidOrder)
            .map(this::processOrderLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null
                && order.getPetId() != null && !order.getPetId().isBlank()
                && order.getQuantity() != null && order.getQuantity() > 0;
    }

    private Order processOrderLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        // Calculate estimated ship date if not set
        if (order.getShipDate() == null || order.getShipDate().isBlank()) {
            // For demonstration, set ship date to current date + 3 days (ISO format)
            java.time.LocalDate shipDate = java.time.LocalDate.now().plusDays(3);
            order.setShipDate(shipDate.toString());
        }

        // Check stock availability - simulate check
        boolean stockAvailable = true; // Simulate stock check here

        if (!stockAvailable) {
            order.setStatus("rejected");
            order.setComplete(false);
        } else {
            // Approve the order
            order.setStatus("approved");
            order.setComplete(true);
        }

        return order;
    }
}
