package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PetSaleProcessor - Processes the sale of a pet
 * 
 * Transitions: sell_pet_direct (available → sold), complete_sale (pending → sold)
 * Purpose: Processes pet sale and updates associated order
 */
@Component
public class PetSaleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetSaleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetSaleProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet sale for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && 
               ("available".equals(currentState) || "pending".equals(currentState));
    }

    /**
     * Main business logic processing method
     * Processes pet sale and updates associated order
     */
    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing pet sale: {} in state: {}", pet.getPetId(), currentState);

        // Update timestamps
        pet.setUpdatedAt(LocalDateTime.now());

        // Update associated order if exists
        updateAssociatedOrder(pet);

        logger.info("Pet {} sale processed successfully", pet.getPetId());

        return entityWithMetadata;
    }

    /**
     * Update associated order entity
     */
    private void updateAssociatedOrder(Pet pet) {
        try {
            // Find orders for this pet
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(pet.getPetId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);

            for (EntityWithMetadata<Order> orderWithMetadata : orders) {
                Order order = orderWithMetadata.entity();
                String orderState = orderWithMetadata.metadata().getState();
                
                // Update order to placed state if it's in initial state
                if ("initial_state".equals(orderState) || "placed".equals(orderState)) {
                    order.setUpdatedAt(LocalDateTime.now());
                    entityService.update(orderWithMetadata.metadata().getId(), order, "create_order");
                    logger.debug("Updated order {} for pet sale", order.getOrderId());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not update associated order for pet {}: {}", pet.getPetId(), e.getMessage());
        }
    }
}
