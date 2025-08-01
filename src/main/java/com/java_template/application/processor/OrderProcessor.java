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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private final EntityService entityService;

    public OrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

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

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        // Business logic from processOrder()
        logger.info("Starting processing Order: {}", context.request().getEntityId());

        if (entity.getOrderItems() == null || entity.getOrderItems().isEmpty()) {
            logger.error("Order has no items: {}", context.request().getEntityId());
            entity.setStatus("FAILED");
            return entity;
        }

        // Validate stock availability for each order item (simulate, as no direct stock service here)
        // Assuming external check is done elsewhere, here we just proceed

        // Deduct stock (not implemented here, would be external service call)

        // Calculate total amount (just recalc from items)
        BigDecimal totalAmount = entity.getOrderItems().stream()
                .map(item -> {
                    BigDecimal price = item.getPriceAtPurchase();
                    Integer qty = item.getQuantity();
                    return price.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entity.setTotalAmount(totalAmount);

        // Set status to CONFIRMED
        entity.setStatus("CONFIRMED");

        logger.info("Completed processing Order: {}", context.request().getEntityId());

        return entity;
    }

}
