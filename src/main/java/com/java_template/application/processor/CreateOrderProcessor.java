package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CreateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
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
        Order order = context.entity();

        try {
            if (order.getItemsSnapshot() == null || order.getItemsSnapshot().isEmpty()) {
                logger.info("Order {} has no items to reserve", order.getId());
                return order;
            }

            for (Order.Item item : order.getItemsSnapshot()) {
                if (item == null) {
                    logger.error("Order {} contains null item - skipping", order.getId());
                    continue;
                }

                String productId = item.getProductId();
                Integer qtyRequested = item.getQuantity() != null ? item.getQuantity() : 0;

                if (productId == null || productId.isBlank()) {
                    logger.error("Order {} contains item with invalid productId", order.getId());
                    throw new RuntimeException("Invalid productId in order " + order.getId());
                }
                if (qtyRequested <= 0) {
                    logger.error("Order {} contains item with non-positive quantity for product {}", order.getId(), productId);
                    throw new RuntimeException("Invalid item quantity for product " + productId + " in order " + order.getId());
                }

                // Attempt to resolve product by treating productId as technical UUID.
                UUID productTechnicalId = null;
                ObjectNode productNode = null;
                try {
                    productTechnicalId = UUID.fromString(productId);
                    productNode = entityService.getItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            productTechnicalId
                    ).join();
                } catch (IllegalArgumentException iae) {
                    // productId is not a UUID -> cannot fetch by technical id.
                    logger.warn("Product id '{}' is not a technical UUID. Skipping inventory reservation for order {}.", productId, order.getId());
                } catch (Exception ex) {
                    logger.error("Error while fetching product {} for order {}: {}", productId, order.getId(), ex.getMessage(), ex);
                    throw new RuntimeException("Error while fetching product " + productId, ex);
                }

                if (productNode == null) {
                    logger.warn("Product with id {} not found or not retrievable as technical UUID while processing order {}", productId, order.getId());
                    // We choose to fail-fast because inventory cannot be reserved without product technical id
                    throw new RuntimeException("Product not found for id " + productId + " while processing order " + order.getId());
                }

                // Convert product JSON to Product entity
                Product product;
                try {
                    product = objectMapper.convertValue(productNode, Product.class);
                } catch (Exception ex) {
                    logger.error("Failed to deserialize product {} for order {}: {}", productId, order.getId(), ex.getMessage(), ex);
                    throw new RuntimeException("Failed to deserialize product " + productId, ex);
                }

                Integer available = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;
                if (available < qtyRequested) {
                    logger.error("Insufficient stock for product {}: requested {}, available {}", productId, qtyRequested, available);
                    throw new RuntimeException("Stock not available for product " + productId);
                }

                // Reserve inventory by decrementing availableQuantity on Product entity
                product.setAvailableQuantity(available - qtyRequested);

                try {
                    // Persist updated product using EntityService (update other entity is allowed)
                    entityService.updateItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            productTechnicalId,
                            product
                    ).join();

                    logger.info("Reserved {} units of product {} for order {}. New available={}", qtyRequested, productId, order.getId(), product.getAvailableQuantity());
                } catch (Exception ee) {
                    logger.error("Error while reserving inventory for product {} in order {}: {}", productId, order.getId(), ee.getMessage(), ee);
                    throw new RuntimeException("Failed to reserve inventory for product " + productId + " in order " + order.getId(), ee);
                }
            }

            // All items processed successfully
            logger.info("Inventory reservation completed for order {}", order.getId());
            return order;

        } catch (RuntimeException re) {
            logger.error("CreateOrderProcessor failed for order {}: {}", order.getId(), re.getMessage(), re);
            throw re;
        } catch (Exception ex) {
            logger.error("Unexpected error in CreateOrderProcessor for order {}: {}", order.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Unexpected error processing order " + order.getId(), ex);
        }
    }
}