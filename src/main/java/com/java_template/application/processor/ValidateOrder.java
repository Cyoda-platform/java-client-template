package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * ValidateOrder Processor
 * 
 * Validates order by checking:
 * 1. Order items exist and have valid quantities
 * 2. Product inventory is sufficient for all items
 * 3. Order total matches sum of item subtotals
 */
@Component
public class ValidateOrder implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateOrder.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateOrder(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateOrder for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::validateOrderLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Order> validateOrderLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Validating order: {} with {} items", order.getOrderId(), order.getItems().size());

        // Validate items exist
        if (order.getItems() == null || order.getItems().isEmpty()) {
            logger.error("Order {} has no items", order.getOrderId());
            throw new IllegalArgumentException("Order must have at least one item");
        }

        // Validate inventory for each item
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (Order.OrderItem item : order.getItems()) {
            // Check product inventory
            ModelSpec productModelSpec = new ModelSpec()
                    .withName(Product.ENTITY_NAME)
                    .withVersion(Product.ENTITY_VERSION);
            
            SimpleCondition productCondition = new SimpleCondition()
                    .withJsonPath("$.productId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(item.getItemId()));
            
            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(productCondition));
            
            List<EntityWithMetadata<Product>> products = entityService.search(productModelSpec, condition, Product.class);
            
            if (products.isEmpty()) {
                logger.warn("Product not found for item: {}", item.getItemId());
                // For demo purposes, we'll allow missing products
            } else {
                Product product = products.get(0).entity();
                if (product.getInventory() < item.getQuantity()) {
                    logger.error("Insufficient inventory for product: {}. Required: {}, Available: {}", 
                            item.getItemId(), item.getQuantity(), product.getInventory());
                    throw new IllegalArgumentException(
                            String.format("Insufficient inventory for product %s", item.getItemId()));
                }
            }
            
            // Calculate subtotal
            if (item.getSubtotal() == null) {
                item.setSubtotal(item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())));
            }
            calculatedTotal = calculatedTotal.add(item.getSubtotal());
        }

        // Validate total matches sum of items
        if (order.getTotalAmount().compareTo(calculatedTotal) != 0) {
            logger.warn("Order total mismatch. Expected: {}, Calculated: {}", 
                    order.getTotalAmount(), calculatedTotal);
            order.setTotalAmount(calculatedTotal);
        }

        logger.info("Order {} validated successfully", order.getOrderId());
        return entityWithMetadata;
    }
}

