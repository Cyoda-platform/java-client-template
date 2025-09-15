package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DeliveryAvailabilityCriterion - Ensures delivery can be assigned to the order
 * Transition: READY_FOR_PICKUP → OUT_FOR_DELIVERY
 */
@Component
public class DeliveryAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateDeliveryAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if entity is null
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify restaurant is still active
        if (!isRestaurantActive(order.getRestaurantId())) {
            return EvaluationOutcome.fail("Restaurant is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if delivery services are available for this restaurant
        List<EntityWithMetadata<DeliveryService>> availableServices = getAvailableDeliveryServices();
        if (availableServices.isEmpty()) {
            return EvaluationOutcome.fail("No active delivery services available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if any delivery person is available
        boolean hasAvailableDeliveryPerson = false;
        for (EntityWithMetadata<DeliveryService> serviceWithMetadata : availableServices) {
            if (hasAvailableDeliveryPerson(serviceWithMetadata.entity().getDeliveryServiceId())) {
                hasAvailableDeliveryPerson = true;
                break;
            }
        }

        if (!hasAvailableDeliveryPerson) {
            return EvaluationOutcome.fail("No available delivery person found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Delivery availability criteria passed for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private boolean isRestaurantActive(String restaurantId) {
        try {
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            return !restaurants.isEmpty() && "ACTIVE".equals(restaurants.get(0).getState());

        } catch (Exception e) {
            logger.error("Error checking restaurant status for {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }

    private List<EntityWithMetadata<DeliveryService>> getAvailableDeliveryServices() {
        try {
            ModelSpec deliveryServiceModelSpec = new ModelSpec()
                    .withName(DeliveryService.ENTITY_NAME)
                    .withVersion(DeliveryService.ENTITY_VERSION);

            List<EntityWithMetadata<DeliveryService>> allServices = entityService.findAll(deliveryServiceModelSpec, DeliveryService.class);

            return allServices.stream()
                    .filter(serviceWithMetadata -> "ACTIVE".equals(serviceWithMetadata.getState()))
                    .toList();

        } catch (Exception e) {
            logger.error("Error getting available delivery services: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean hasAvailableDeliveryPerson(String deliveryServiceId) {
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

            return deliveryPersons.stream()
                    .anyMatch(personWithMetadata -> {
                        String state = personWithMetadata.getState();
                        DeliveryPerson person = personWithMetadata.entity();
                        return "ACTIVE".equals(state) && 
                               person.getIsAvailable() != null && person.getIsAvailable() &&
                               person.getIsOnline() != null && person.getIsOnline();
                    });

        } catch (Exception e) {
            logger.error("Error checking available delivery persons for service {}: {}", deliveryServiceId, e.getMessage());
            return false;
        }
    }
}
