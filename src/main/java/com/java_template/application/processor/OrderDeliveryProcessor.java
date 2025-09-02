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
public class OrderDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public OrderDeliveryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order delivery for request: {}", request.getId());

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

        // Set delivery completion details
        entity.setComplete(true);
        entity.setNotes((entity.getNotes() != null ? entity.getNotes() + " " : "") + 
                       "Order delivered on " + LocalDateTime.now().toString());

        // Update associated pet to complete adoption
        try {
            EntityResponse<Pet> petResponse = entityService.getItem(
                UUID.fromString(entity.getPetId().toString()), 
                Pet.class
            );
            Pet pet = petResponse.getData();
            
            // Update pet with complete_adoption transition
            entityService.update(petResponse.getMetadata().getId(), pet, "complete_adoption");
            logger.info("Pet adoption completed for order delivery: {}", pet.getName());

        } catch (Exception e) {
            logger.error("Failed to complete pet adoption for order delivery: {}", e.getMessage(), e);
            // Don't fail the delivery if pet update fails
        }

        logger.info("Order delivered successfully: {}", entity.getId());
        return entity;
    }
}
