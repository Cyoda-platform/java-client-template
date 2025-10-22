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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * ABOUTME: Processor for updating product inventory levels and managing
 * stock-related operations like quantity decrements from order fulfillment.
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
     * Process inventory updates for the product
     */
    private EntityWithMetadata<Product> processInventoryUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing inventory update for product: {}", product.getSku());

        // Update inventory tracking
        updateInventoryTracking(product);

        // Add inventory event
        addInventoryEvent(product, "InventoryUpdated");

        logger.info("Product {} inventory updated successfully", product.getSku());

        return entityWithMetadata;
    }

    /**
     * Update inventory tracking and calculations
     */
    private void updateInventoryTracking(Product product) {
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            int totalAvailable = 0;
            
            for (Product.Node node : product.getInventory().getNodes()) {
                int nodeTotal = 0;
                
                // Calculate from lots
                if (node.getLots() != null) {
                    for (Product.Lot lot : node.getLots()) {
                        if ("Released".equals(lot.getQuality()) && lot.getQty() != null) {
                            nodeTotal += lot.getQty();
                        }
                    }
                }
                
                // Add direct qtyOnHand if available
                if (node.getQtyOnHand() != null) {
                    nodeTotal += node.getQtyOnHand();
                }
                
                // Subtract reservations
                if (node.getReservations() != null) {
                    for (Product.Reservation reservation : node.getReservations()) {
                        if (reservation.getQty() != null) {
                            nodeTotal -= reservation.getQty();
                        }
                    }
                }
                
                totalAvailable += Math.max(0, nodeTotal);
            }
            
            // Update the quick projection field
            product.setQuantityAvailable(totalAvailable);
        }
    }

    /**
     * Add inventory event to product history
     */
    private void addInventoryEvent(Product product, String eventType) {
        if (product.getEvents() == null) {
            product.setEvents(new ArrayList<>());
        }

        Product.Event event = new Product.Event();
        event.setType(eventType);
        event.setAt(LocalDateTime.now().toString());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", product.getSku());
        payload.put("quantityAvailable", product.getQuantityAvailable());
        event.setPayload(payload);

        product.getEvents().add(event);
    }
}
