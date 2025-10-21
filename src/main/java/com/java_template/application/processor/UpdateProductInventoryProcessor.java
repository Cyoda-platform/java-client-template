package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for updating product inventory information
 * including quantity available, lot information, and inventory policies.
 */
@Component
public class UpdateProductInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateProductInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateProductInventoryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Product.class)
                .validate(this::isValidEntityWithMetadata, "Invalid product entity wrapper")
                .map(this::processInventoryUpdate)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Product> entityWithMetadata) {
        Product product = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return product != null && product.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Process inventory update logic
     */
    private EntityWithMetadata<Product> processInventoryUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing inventory update for product: {}", product.getSku());

        // Update quantityAvailable based on inventory nodes
        updateQuantityAvailable(product);

        // Add inventory update event
        addInventoryUpdateEvent(product);

        logger.info("Product inventory updated for SKU: {}", product.getSku());

        return entityWithMetadata;
    }

    /**
     * Update quantityAvailable based on inventory nodes
     */
    private void updateQuantityAvailable(Product product) {
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            int totalAvailable = 0;
            
            for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
                if (node.getLots() != null) {
                    // Sum up quantities from all lots with "Released" quality
                    int nodeAvailable = node.getLots().stream()
                            .filter(lot -> "Released".equals(lot.getQuality()))
                            .mapToInt(lot -> lot.getQty() != null ? lot.getQty() : 0)
                            .sum();
                    totalAvailable += nodeAvailable;
                } else if (node.getQtyOnHand() != null) {
                    // For 3PL nodes, use qtyOnHand directly
                    totalAvailable += node.getQtyOnHand();
                }
            }
            
            product.setQuantityAvailable(totalAvailable);
            logger.debug("Updated quantityAvailable to {} for product {}", totalAvailable, product.getSku());
        }
    }

    /**
     * Add inventory update event to product events
     */
    private void addInventoryUpdateEvent(Product product) {
        if (product.getEvents() == null) {
            product.setEvents(new java.util.ArrayList<>());
        }

        Product.ProductEvent event = new Product.ProductEvent();
        event.setType("InventoryUpdated");
        event.setAt(LocalDateTime.now());
        
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("sku", product.getSku());
        payload.put("quantityAvailable", product.getQuantityAvailable());
        event.setPayload(payload);

        product.getEvents().add(event);
    }
}
