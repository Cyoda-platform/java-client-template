package com.java_template.application.processor;

import com.java_template.application.entity.address.version_1.Address;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business rules:
        // - Ensure shipping and billing addresses exist in Address entity store.
        // - If both addresses exist, mark order as Confirmed.
        // - Do not perform any add/update/delete on the Order entity via EntityService (Cyoda will persist changes).
        // - Log any missing address information; leave status unchanged when addresses are missing.

        boolean billingExists = false;
        boolean shippingExists = false;

        String billingId = entity.getBillingAddressId();
        String shippingId = entity.getShippingAddressId();

        if (billingId != null && !billingId.isBlank()) {
            try {
                CompletableFuture<ObjectNode> billingFuture = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(billingId)
                );
                ObjectNode billingNode = billingFuture.join();
                billingExists = billingNode != null && !billingNode.isEmpty();
            } catch (Exception ex) {
                logger.warn("Failed to fetch billing address [{}]: {}", billingId, ex.getMessage());
            }
        } else {
            logger.warn("Order {} missing billingAddressId", entity.getId());
        }

        if (shippingId != null && !shippingId.isBlank()) {
            try {
                CompletableFuture<ObjectNode> shippingFuture = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(shippingId)
                );
                ObjectNode shippingNode = shippingFuture.join();
                shippingExists = shippingNode != null && !shippingNode.isEmpty();
            } catch (Exception ex) {
                logger.warn("Failed to fetch shipping address [{}]: {}", shippingId, ex.getMessage());
            }
        } else {
            logger.warn("Order {} missing shippingAddressId", entity.getId());
        }

        if (billingExists && shippingExists) {
            logger.info("Order {}: billing and shipping addresses validated. Marking as Confirmed.", entity.getId());
            entity.setStatus("Confirmed");
        } else {
            logger.warn("Order {}: address validation failed. billingExists={}, shippingExists={}. Leaving status unchanged.",
                entity.getId(), billingExists, shippingExists);
        }

        // Additional consistency checks (items / totals) are already enforced by entity.isValid() in validate step.
        return entity;
    }
}