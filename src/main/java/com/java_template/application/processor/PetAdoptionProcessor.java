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
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class PetAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public PetAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet adoption for request: {}", request.getId());

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

        // Find the associated order for this pet
        try {
            Condition petIdCondition = Condition.of("$.petId", "EQUALS", entity.getId().toString());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(petIdCondition));

            List<EntityResponse<Order>> orders = entityService.getItemsByCondition(
                Order.class, 
                Order.ENTITY_NAME, 
                Order.ENTITY_VERSION, 
                condition, 
                true
            );

            if (!orders.isEmpty()) {
                EntityResponse<Order> orderResponse = orders.get(0);
                Order order = orderResponse.getData();
                
                // Update order with confirm_order transition
                order.setPaymentStatus("confirmed");
                order.setComplete(true);
                
                entityService.update(orderResponse.getMetadata().getId(), order, "confirm_order");
                logger.info("Order confirmed for pet adoption: {}", entity.getName());
            } else {
                logger.warn("No order found for adopted pet: {}", entity.getName());
            }
        } catch (Exception e) {
            logger.error("Failed to update order for pet adoption: {}", e.getMessage(), e);
            // Don't fail the adoption process if order update fails
        }

        logger.info("Pet adoption completed successfully: {}", entity.getName());
        return entity;
    }
}
