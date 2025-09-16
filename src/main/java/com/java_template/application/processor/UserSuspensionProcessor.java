package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.order.version_1.Order;
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
import java.util.ArrayList;
import java.util.List;

/**
 * UserSuspensionProcessor - Suspend user account and cancel active orders
 * 
 * Transition: suspend_user (active → suspended)
 * Purpose: Suspend user account and cancel active orders
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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        User entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        logger.debug("Processing user suspension: {}", user.getUserId());

        // 1. Find all orders for this user in 'placed' or 'approved' states using entityService
        try {
            ModelSpec orderModelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            // Create search condition for user's orders
            SimpleCondition userCondition = new SimpleCondition()
                    .withJsonPath("$.userId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(user.getUserId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(userCondition));

            List<EntityWithMetadata<Order>> userOrders = entityService.search(orderModelSpec, condition, Order.class);

            // 2. For each active order: cancel it
            for (EntityWithMetadata<Order> orderWithMetadata : userOrders) {
                String orderState = orderWithMetadata.metadata().getState();
                
                if ("placed".equals(orderState)) {
                    // Update order to 'cancelled' state using transition 'cancel_order'
                    entityService.update(orderWithMetadata.metadata().getId(), 
                            orderWithMetadata.entity(), "cancel_order");
                    logger.info("Order {} cancelled due to user suspension", 
                            orderWithMetadata.entity().getOrderId());
                } else if ("approved".equals(orderState)) {
                    // Update order to 'cancelled' state using transition 'cancel_approved_order'
                    entityService.update(orderWithMetadata.metadata().getId(), 
                            orderWithMetadata.entity(), "cancel_approved_order");
                    logger.info("Approved order {} cancelled due to user suspension", 
                            orderWithMetadata.entity().getOrderId());
                }
            }

        } catch (Exception e) {
            logger.error("Error cancelling orders for user suspension {}: {}", user.getUserId(), e.getMessage());
        }

        // 3. Set updatedAt = current timestamp
        user.setUpdatedAt(LocalDateTime.now());

        // 4. Log suspension action with user ID and timestamp
        logger.info("User {} suspended at {}", user.getUserId(), LocalDateTime.now());

        return entityWithMetadata;
    }
}
