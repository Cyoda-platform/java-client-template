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

import java.util.List;

/**
 * UpdateInventory Processor
 * 
 * Updates product inventory after order completion.
 * Decrements inventory for each product in the order.
 */
@Component
public class UpdateInventory implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateInventory.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UpdateInventory(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UpdateInventory for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::updateInventoryLogic)
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

    private EntityWithMetadata<Order> updateInventoryLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Updating inventory for order: {}", order.getOrderId());

        // Update inventory for each item in the order
        for (Order.OrderItem item : order.getItems()) {
            updateProductInventory(item);
        }

        logger.info("Inventory updated successfully for order: {}", order.getOrderId());
        return entityWithMetadata;
    }

    /**
     * Updates inventory for a single product
     */
    private void updateProductInventory(Order.OrderItem item) {
        try {
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
                return;
            }
            
            EntityWithMetadata<Product> productWithMetadata = products.get(0);
            Product product = productWithMetadata.entity();
            
            // Decrement inventory
            int newInventory = product.getInventory() - item.getQuantity();
            product.setInventory(Math.max(0, newInventory));
            
            logger.debug("Updated inventory for product: {} from {} to {}", 
                    product.getProductId(), 
                    product.getInventory() + item.getQuantity(), 
                    product.getInventory());
            
            // Update product in database
            entityService.update(productWithMetadata.metadata().getId(), product, null);
            
        } catch (Exception e) {
            logger.error("Failed to update inventory for item: {}", item.getItemId(), e);
            // Don't fail the order if inventory update fails - log and continue
        }
    }
}

