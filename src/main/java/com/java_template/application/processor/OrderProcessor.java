package com.java_template.application.processor;

import com.java_template.application.entity.Order;
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

import java.util.concurrent.CompletableFuture;

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
        // Basic validation: petId not null or blank, quantity positive
        return entity != null
                && entity.getPetId() != null && !entity.getPetId().isBlank()
                && entity.getQuantity() != null && entity.getQuantity() > 0;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        String technicalId = context.request().getEntityId();

        // Calculate estimated ship date if not set
        if (order.getShipDate() == null || order.getShipDate().isBlank()) {
            // Set default ship date to current date + 7 days (ISO string)
            java.time.LocalDate shipDate = java.time.LocalDate.now().plusDays(7);
            order.setShipDate(shipDate.toString());
        }

        // Verify stock availability (simplified as always available for this prototype)
        // In real scenario, call external inventory or stock service

        // Set status to approved if validation passed
        order.setStatus("approved");

        logger.info("Processed Order id {} with petId {} and quantity {}", technicalId, order.getPetId(), order.getQuantity());

        // No EntityService update call on current entity allowed, modifications done directly

        return order;
    }
}
