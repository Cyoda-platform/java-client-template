package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
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
 * DeliveryAssignmentProcessor - Handles delivery assignment workflow transition
 * Transition: READY_FOR_PICKUP → OUT_FOR_DELIVERY
 */
@Component
public class DeliveryAssignmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryAssignmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryAssignmentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery assignment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processDeliveryAssignment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<Order> processDeliveryAssignment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing delivery assignment: {}", order.getOrderId());

        order.setUpdatedAt(LocalDateTime.now());

        // Find best available delivery service and person
        assignDeliveryPerson(order);

        logger.info("Delivery assigned: {} to {}", order.getOrderId(), order.getDeliveryPersonId());
        
        return entityWithMetadata;
    }

    private void assignDeliveryPerson(Order order) {
        try {
            // Get restaurant information to find supported delivery services
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(order.getRestaurantId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            if (restaurants.isEmpty()) {
                throw new IllegalStateException("Restaurant not found for order");
            }

            // Find available delivery services (simplified - in real implementation would check region support)
            ModelSpec deliveryServiceModelSpec = new ModelSpec()
                    .withName(DeliveryService.ENTITY_NAME)
                    .withVersion(DeliveryService.ENTITY_VERSION);

            List<EntityWithMetadata<DeliveryService>> deliveryServices = entityService.findAll(deliveryServiceModelSpec, DeliveryService.class);

            for (EntityWithMetadata<DeliveryService> serviceWithMetadata : deliveryServices) {
                if (!"ACTIVE".equals(serviceWithMetadata.getState())) {
                    continue;
                }

                DeliveryService deliveryService = serviceWithMetadata.entity();
                
                // Find available delivery person for this service
                DeliveryPerson availablePerson = findAvailableDeliveryPerson(deliveryService.getDeliveryServiceId());
                
                if (availablePerson != null) {
                    // Assign delivery
                    order.setAssignedDeliveryService(deliveryService.getName());
                    order.setDeliveryPersonId(availablePerson.getDeliveryPersonId());
                    
                    // Update delivery person status to BUSY
                    updateDeliveryPersonStatus(availablePerson.getDeliveryPersonId());
                    
                    // Generate tracking URL (simplified)
                    order.setTrackingUrl("https://tracking.example.com/order/" + order.getOrderId());
                    
                    logger.info("Assigned delivery person {} from service {} to order {}", 
                            availablePerson.getName(), deliveryService.getName(), order.getOrderId());
                    return;
                }
            }

            throw new IllegalStateException("No available delivery person found");

        } catch (Exception e) {
            logger.error("Error assigning delivery person for order {}: {}", order.getOrderId(), e.getMessage());
            throw new IllegalStateException("No available delivery person found");
        }
    }

    private DeliveryPerson findAvailableDeliveryPerson(String deliveryServiceId) {
        try {
            ModelSpec deliveryPersonModelSpec = new ModelSpec()
                    .withName(DeliveryPerson.ENTITY_NAME)
                    .withVersion(DeliveryPerson.ENTITY_VERSION);

            SimpleCondition serviceCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryServiceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryServiceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(serviceCondition));

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(deliveryPersonModelSpec, condition, DeliveryPerson.class);

            for (EntityWithMetadata<DeliveryPerson> personWithMetadata : deliveryPersons) {
                if ("ACTIVE".equals(personWithMetadata.getState())) {
                    DeliveryPerson person = personWithMetadata.entity();
                    if (person.getIsAvailable() && person.getIsOnline()) {
                        return person;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Error finding available delivery person for service {}: {}", deliveryServiceId, e.getMessage());
            return null;
        }
    }

    private void updateDeliveryPersonStatus(String deliveryPersonId) {
        try {
            ModelSpec deliveryPersonModelSpec = new ModelSpec()
                    .withName(DeliveryPerson.ENTITY_NAME)
                    .withVersion(DeliveryPerson.ENTITY_VERSION);

            SimpleCondition personCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryPersonId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryPersonId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(personCondition));

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(deliveryPersonModelSpec, condition, DeliveryPerson.class);

            if (!deliveryPersons.isEmpty()) {
                EntityWithMetadata<DeliveryPerson> personWithMetadata = deliveryPersons.get(0);
                entityService.update(personWithMetadata.getId(), personWithMetadata.entity(), "assign_delivery");
            }

        } catch (Exception e) {
            logger.error("Error updating delivery person status for {}: {}", deliveryPersonId, e.getMessage());
        }
    }
}
