package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OrderCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public OrderCancellationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order cancellation for request: {}", request.getId());

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
        Order entity = context.entity();

        // Set cancellation details
        entity.setPaymentStatus("cancelled");
        entity.setComplete(false);
        entity.setNotes((entity.getNotes() != null ? entity.getNotes() + " " : "") + 
                       "Order cancelled on " + LocalDateTime.now().toString());

        // Update associated pet to cancel reservation
        try {
            EntityResponse<Pet> petResponse = entityService.getItem(
                UUID.fromString(entity.getPetId().toString()), 
                Pet.class
            );
            Pet pet = petResponse.getData();
            
            // Update pet with cancel_reservation transition
            entityService.update(petResponse.getMetadata().getId(), pet, "cancel_reservation");
            logger.info("Pet reservation cancelled for order cancellation: {}", pet.getName());

        } catch (Exception e) {
            logger.error("Failed to cancel pet reservation for order: {}", e.getMessage(), e);
            // Don't fail the order cancellation if pet update fails
        }

        logger.info("Order cancelled successfully: {}", entity.getId());
        return entity;
    }
}
