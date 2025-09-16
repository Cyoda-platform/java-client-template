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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderPlacementProcessor - Place order and reserve associated pets
 * 
 * Transition: place_order (none → placed)
 * Purpose: Place order and reserve associated pets
 */
@Component
public class OrderPlacementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderPlacementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderPlacementProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order placement for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order placement: {}", order.getOrderId());

        // 1. Validate order has required fields - already done in isValid()
        
        // 2. For each item in order.items: reserve pets
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                try {
                    // a. Find pet by petId using entityService
                    ModelSpec petModelSpec = new ModelSpec()
                            .withName(Pet.ENTITY_NAME)
                            .withVersion(Pet.ENTITY_VERSION);
                    
                    EntityWithMetadata<Pet> petEntity = entityService.findByBusinessId(
                            petModelSpec, item.getPetId(), "petId", Pet.class);
                    
                    if (petEntity == null) {
                        logger.error("Pet not found for order {}: {}", order.getOrderId(), item.getPetId());
                        continue;
                    }
                    
                    // c. If pet state is 'available', update pet to 'pending' state using transition 'reserve_pet'
                    String petState = petEntity.metadata().getState();
                    if ("available".equals(petState)) {
                        entityService.update(petEntity.metadata().getId(), petEntity.entity(), "reserve_pet");
                        logger.info("Pet {} reserved for order {}", item.getPetId(), order.getOrderId());
                    } else {
                        logger.warn("Pet {} is not available (state: {}) for order {}", 
                                item.getPetId(), petState, order.getOrderId());
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing pet {} for order {}: {}", 
                            item.getPetId(), order.getOrderId(), e.getMessage());
                }
            }
        }

        // 3. Set orderDate = current timestamp if not set
        if (order.getOrderDate() == null) {
            order.setOrderDate(LocalDateTime.now());
        }

        // 4. Set updatedAt = current timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} placed successfully", order.getOrderId());
        return entityWithMetadata;
    }
}
