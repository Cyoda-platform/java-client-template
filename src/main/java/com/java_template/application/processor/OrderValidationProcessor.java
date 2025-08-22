package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
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

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
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

        try {
            // Validate presence of billing and shipping address IDs
            String billingId = entity.getBillingAddressId();
            String shippingId = entity.getShippingAddressId();

            if (billingId == null || billingId.isBlank()) {
                logger.warn("Order {} missing billingAddressId", entity.getId());
                return entity;
            }
            if (shippingId == null || shippingId.isBlank()) {
                logger.warn("Order {} missing shippingAddressId", entity.getId());
                return entity;
            }

            boolean billingExists = false;
            boolean shippingExists = false;

            try {
                CompletableFuture<ObjectNode> billingFuture = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(billingId)
                );
                ObjectNode billingNode = billingFuture != null ? billingFuture.join() : null;
                billingExists = billingNode != null;
            } catch (Exception ex) {
                logger.warn("Order {}: failed to fetch billing address {}: {}", entity.getId(), billingId, ex.getMessage());
                billingExists = false;
            }

            try {
                CompletableFuture<ObjectNode> shippingFuture = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(shippingId)
                );
                ObjectNode shippingNode = shippingFuture != null ? shippingFuture.join() : null;
                shippingExists = shippingNode != null;
            } catch (Exception ex) {
                logger.warn("Order {}: failed to fetch shipping address {}: {}", entity.getId(), shippingId, ex.getMessage());
                shippingExists = false;
            }

            if (!billingExists) {
                logger.warn("Order {}: billing address {} not found", entity.getId(), billingId);
                return entity;
            }
            if (!shippingExists) {
                logger.warn("Order {}: shipping address {} not found", entity.getId(), shippingId);
                return entity;
            }

            // Validate totals consistency
            List<Order.OrderItem> items = entity.getItems();
            if (items == null || items.isEmpty()) {
                logger.warn("Order {} has no items to validate totals", entity.getId());
                return entity;
            }

            double sum = 0.0;
            for (Order.OrderItem it : items) {
                if (it == null) {
                    logger.warn("Order {} contains null item", entity.getId());
                    return entity;
                }
                Integer qty = it.getQuantity();
                Double unitPrice = it.getUnitPrice();
                if (qty == null || unitPrice == null) {
                    logger.warn("Order {} has item with null quantity or unitPrice", entity.getId());
                    return entity;
                }
                sum += unitPrice * qty;
            }

            if (entity.getTotal() == null) {
                logger.warn("Order {} total is null", entity.getId());
                return entity;
            }

            if (Math.abs(entity.getTotal() - sum) > 0.01) {
                logger.warn("Order {} total mismatch. Declared total={}, computed sum={}", entity.getId(), entity.getTotal(), sum);
                return entity;
            }

            // All validations passed -> mark Confirmed
            logger.info("Order {}: billing and shipping addresses validated and totals consistent. Marking as Confirmed.", entity.getId());
            entity.setStatus("Confirmed");
            return entity;

        } catch (Exception ex) {
            logger.error("Unexpected error in OrderValidationProcessor for order {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            return entity;
        }
    }
}