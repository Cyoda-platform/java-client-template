package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
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
import java.util.List;

/**
 * DeliveryCompletionProcessor - Handles delivery completion workflow transition
 * Transition: OUT_FOR_DELIVERY → DELIVERED
 */
@Component
public class DeliveryCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processDeliveryCompletion)
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

    private EntityWithMetadata<Order> processDeliveryCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing delivery completion: {}", order.getOrderId());

        // Update timestamps
        order.setUpdatedAt(LocalDateTime.now());
        order.setActualDeliveryTime(LocalDateTime.now());

        // Update delivery person status back to available
        if (order.getDeliveryPersonId() != null) {
            updateDeliveryPersonStatus(order.getDeliveryPersonId());
        }

        // Note: In a real implementation, we would:
        // - Send delivery confirmation to customer
        // - Process final payment
        // - Update delivery person ratings and statistics
        // - Send completion notification to restaurant
        
        logger.info("Delivery completed: {}", order.getOrderId());
        
        return entityWithMetadata;
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
                // Update delivery person back to ACTIVE status
                entityService.update(personWithMetadata.getId(), personWithMetadata.entity(), "complete_delivery");
                logger.info("Updated delivery person {} status to available", deliveryPersonId);
            }

        } catch (Exception e) {
            logger.error("Error updating delivery person status for {}: {}", deliveryPersonId, e.getMessage());
        }
    }
}
