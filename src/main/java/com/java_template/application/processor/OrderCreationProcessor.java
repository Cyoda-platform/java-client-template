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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public OrderCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation for request: {}", request.getId());

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

        // Validate pet exists and is available
        try {
            EntityResponse<Pet> petResponse = entityService.getItem(
                UUID.fromString(entity.getPetId().toString()), 
                Pet.class
            );
            Pet pet = petResponse.getData();
            String petState = petResponse.getMetadata().getState();

            if (!"available".equals(petState)) {
                throw new IllegalStateException("Pet is not available for order");
            }

            // Calculate total amount based on pet price
            if (pet.getPrice() != null) {
                BigDecimal totalAmount = pet.getPrice().multiply(BigDecimal.valueOf(entity.getQuantity()));
                entity.setTotalAmount(totalAmount);
            }

            // Set order date to current timestamp
            entity.setOrderDate(LocalDateTime.now());
            entity.setPaymentStatus("pending");
            entity.setComplete(false);

            // Update pet with reserve_pet transition
            entityService.update(petResponse.getMetadata().getId(), pet, "reserve_pet");
            logger.info("Pet reserved for order: {}", pet.getName());

        } catch (Exception e) {
            logger.error("Failed to validate pet or reserve for order: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }

        logger.info("Order created successfully for pet ID: {}", entity.getPetId());
        return entity;
    }
}
