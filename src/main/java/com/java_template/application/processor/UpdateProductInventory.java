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

/**
 * Processor for updating product inventory levels
 * Handles inventory adjustments and quantity calculations
 */
@Component
public class UpdateProductInventory implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateProductInventory.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateProductInventory(SerializerFactory serializerFactory) {
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
     * Validate the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Product> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.getEntity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }

        Product product = entityWithMetadata.getEntity();
        if (!product.isValid()) {
            logger.error("Product entity is not valid: {}", product);
            return false;
        }

        return true;
    }

    /**
     * Process inventory update logic
     */
    private EntityWithMetadata<Product> processInventoryUpdate(EntityWithMetadata<Product> entityWithMetadata) {
        Product product = entityWithMetadata.getEntity();
        logger.info("Processing inventory update for product SKU: {}", product.getSku());

        try {
            // Calculate total available quantity from inventory nodes
            Integer totalAvailable = calculateTotalAvailableQuantity(product);
            
            // Update the quantityAvailable field
            product.setQuantityAvailable(totalAvailable);
            
            logger.info("Updated quantityAvailable for SKU {} to {}", product.getSku(), totalAvailable);
            
            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error processing inventory update for product SKU: {}", product.getSku(), e);
            throw new RuntimeException("Failed to process inventory update", e);
        }
    }

    /**
     * Calculate total available quantity from inventory nodes
     */
    private Integer calculateTotalAvailableQuantity(Product product) {
        if (product.getInventory() == null || product.getInventory().getNodes() == null) {
            logger.warn("No inventory data found for product SKU: {}", product.getSku());
            return product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
        }

        int totalAvailable = 0;

        for (Product.InventoryNode node : product.getInventory().getNodes()) {
            // Add direct qtyOnHand if available
            if (node.getQtyOnHand() != null) {
                totalAvailable += node.getQtyOnHand();
            }

            // Add quantities from lots (only released quality)
            if (node.getLots() != null) {
                for (Product.InventoryLot lot : node.getLots()) {
                    if ("Released".equals(lot.getQuality()) && lot.getQty() != null) {
                        totalAvailable += lot.getQty();
                    }
                }
            }

            // Subtract reserved quantities
            if (node.getReservations() != null) {
                for (Product.InventoryReservation reservation : node.getReservations()) {
                    if (reservation.getQty() != null) {
                        totalAvailable -= reservation.getQty();
                    }
                }
            }
        }

        return Math.max(0, totalAvailable); // Ensure non-negative
    }
}
