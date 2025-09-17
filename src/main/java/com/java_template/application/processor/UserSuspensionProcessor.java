package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.user.version_1.User;
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
 * UserSuspensionProcessor - Suspends a user account
 * 
 * Transition: suspend_user (active → suspended)
 * Purpose: Suspends user account and cancels their placed orders
 */
@Component
public class UserSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserSuspensionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User suspension for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(User.class)
                .validate(this::isValidEntityWithMetadata, "Invalid user entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        User entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "active".equals(currentState);
    }

    /**
     * Main business logic processing method
     * Suspends user and cancels their orders
     */
    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing user suspension: {} in state: {}", user.getUsername(), currentState);

        // Cancel all placed orders for this user
        cancelUserOrders(user);

        logger.info("User {} suspended successfully", user.getUsername());

        return entityWithMetadata;
    }

    /**
     * Cancel all placed orders for the user
     */
    private void cancelUserOrders(User user) {
        try {
            // Find orders for this user
            ModelSpec modelSpec = new ModelSpec().withName(Order.ENTITY_NAME).withVersion(Order.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.customerInfo.customerId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(user.getUserId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<Order>> orders = entityService.search(modelSpec, condition, Order.class);

            for (EntityWithMetadata<Order> orderWithMetadata : orders) {
                Order order = orderWithMetadata.entity();
                String orderState = orderWithMetadata.metadata().getState();
                
                // Cancel placed orders
                if ("placed".equals(orderState)) {
                    order.setUpdatedAt(LocalDateTime.now());
                    entityService.update(orderWithMetadata.metadata().getId(), order, "cancel_order");
                    logger.debug("Cancelled order {} for suspended user {}", order.getOrderId(), user.getUsername());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not cancel orders for suspended user {}: {}", user.getUsername(), e.getMessage());
        }
    }
}
