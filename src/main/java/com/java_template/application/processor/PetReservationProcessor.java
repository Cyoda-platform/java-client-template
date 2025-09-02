package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PetReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public PetReservationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet reservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
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

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Validate pet is still available
        // Note: In a real implementation, we would check the current state from the entity service
        // For now, we'll assume the pet is available if this processor is called

        // Create Order entity for this reservation
        Order order = new Order();
        order.setPetId(entity.getId());
        order.setQuantity(1);
        order.setOrderDate(LocalDateTime.now());
        order.setTotalAmount(entity.getPrice() != null ? entity.getPrice() : BigDecimal.ZERO);
        order.setPaymentStatus("pending");
        order.setComplete(false);

        // Note: Customer information would come from the request context
        // For now, we'll set placeholder values
        order.setCustomerName("Customer");
        order.setCustomerEmail("customer@example.com");
        order.setCustomerPhone("+1-555-000-0000");
        order.setCustomerAddress("Address");
        order.setPaymentMethod("credit_card");
        order.setShippingMethod("pickup");

        try {
            // Save the order with create_order transition
            entityService.save(order);
            logger.info("Order created for pet reservation: {}", entity.getName());
        } catch (Exception e) {
            logger.error("Failed to create order for pet reservation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order for pet reservation", e);
        }

        logger.info("Pet reserved successfully: {}", entity.getName());
        return entity;
    }
}
