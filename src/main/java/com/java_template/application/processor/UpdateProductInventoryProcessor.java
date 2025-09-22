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
 * UpdateProductInventoryProcessor - Handles product inventory updates
 * 
 * This processor is responsible for:
 * - Updating product inventory levels
 * - Recalculating quantityAvailable based on inventory nodes
 * - Validating inventory data consistency
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
        return product != null && product.isValid() && technicalId != null;
    }

    /**
     * Main inventory update processing logic
     */
    private EntityWithMetadata<Product> processInventoryUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing inventory update for product: {}", product.getSku());

        // Recalculate quantityAvailable based on inventory nodes
        recalculateQuantityAvailable(product);

        // Validate inventory consistency
        validateInventoryConsistency(product);

        logger.info("Product {} inventory updated successfully", product.getSku());

        return entityWithMetadata;
    }

    /**
     * Recalculate quantityAvailable based on inventory nodes
     */
    private void recalculateQuantityAvailable(Product product) {
        if (product.getInventory() == null || product.getInventory().getNodes() == null) {
            logger.debug("No inventory nodes found for product: {}", product.getSku());
            return;
        }

        int totalAvailable = 0;

        for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
            int nodeAvailable = 0;

            // Calculate from lots (released quality only)
            if (node.getLots() != null) {
                nodeAvailable += node.getLots().stream()
                        .filter(lot -> "Released".equals(lot.getQuality()))
                        .mapToInt(lot -> lot.getQty() != null ? lot.getQty() : 0)
                        .sum();
            }

            // Add simple qtyOnHand if available
            if (node.getQtyOnHand() != null) {
                nodeAvailable += node.getQtyOnHand();
            }

            // Subtract reservations
            if (node.getReservations() != null) {
                int reserved = node.getReservations().stream()
                        .mapToInt(reservation -> reservation.getQty() != null ? reservation.getQty() : 0)
                        .sum();
                nodeAvailable -= reserved;
            }

            totalAvailable += Math.max(0, nodeAvailable); // Don't allow negative
        }

        product.setQuantityAvailable(totalAvailable);
        logger.debug("Recalculated quantityAvailable for {}: {}", product.getSku(), totalAvailable);
    }

    /**
     * Validate inventory data consistency
     */
    private void validateInventoryConsistency(Product product) {
        if (product.getInventory() == null || product.getInventory().getNodes() == null) {
            return;
        }

        for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
            // Validate node has required fields
            if (node.getNodeId() == null || node.getNodeId().trim().isEmpty()) {
                logger.warn("Inventory node missing nodeId for product: {}", product.getSku());
                continue;
            }

            // Validate lot data
            if (node.getLots() != null) {
                for (Product.ProductLot lot : node.getLots()) {
                    if (lot.getLotId() == null || lot.getLotId().trim().isEmpty()) {
                        logger.warn("Lot missing lotId in node {} for product: {}", 
                                node.getNodeId(), product.getSku());
                    }
                    if (lot.getQty() == null || lot.getQty() < 0) {
                        logger.warn("Invalid lot quantity in node {} for product: {}", 
                                node.getNodeId(), product.getSku());
                    }
                }
            }

            // Validate reservation data
            if (node.getReservations() != null) {
                for (Product.ProductReservation reservation : node.getReservations()) {
                    if (reservation.getRef() == null || reservation.getRef().trim().isEmpty()) {
                        logger.warn("Reservation missing ref in node {} for product: {}", 
                                node.getNodeId(), product.getSku());
                    }
                    if (reservation.getQty() == null || reservation.getQty() < 0) {
                        logger.warn("Invalid reservation quantity in node {} for product: {}", 
                                node.getNodeId(), product.getSku());
                    }
                }
            }
        }
    }
}
