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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * ABOUTME: Processor for updating Product inventory, handling stock decrements
 * for order fulfillment and inventory adjustments.
 */
@Component
public class UpdateInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateInventoryProcessor(SerializerFactory serializerFactory) {
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
     * Validates the EntityWithMetadata wrapper for Product
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Product> entityWithMetadata) {
        Product product = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return product != null && product.isValid() && technicalId != null;
    }

    /**
     * Main business logic for inventory updates
     */
    private EntityWithMetadata<Product> processInventoryUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing inventory update for SKU: {}", product.getSku());

        // Recalculate quantityAvailable from inventory nodes
        recalculateQuantityAvailable(product);

        // Add inventory update event
        addInventoryUpdateEvent(product);

        // Validate inventory constraints
        validateInventoryConstraints(product);

        logger.info("Inventory updated for product SKU: {}, new quantity: {}", 
                   product.getSku(), product.getQuantityAvailable());

        return entityWithMetadata;
    }

    /**
     * Recalculate quantityAvailable from inventory nodes
     */
    private void recalculateQuantityAvailable(Product product) {
        if (product.getInventory() == null || product.getInventory().getNodes() == null) {
            logger.debug("No inventory nodes found for SKU: {}", product.getSku());
            return;
        }

        int totalAvailable = 0;

        for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
            int nodeAvailable = 0;

            // For simple nodes with qtyOnHand
            if (node.getQtyOnHand() != null) {
                nodeAvailable = node.getQtyOnHand();
            }
            // For complex nodes with lots
            else if (node.getLots() != null) {
                nodeAvailable = node.getLots().stream()
                        .filter(lot -> "Released".equals(lot.getQuality()))
                        .mapToInt(lot -> lot.getQty() != null ? lot.getQty() : 0)
                        .sum();
            }

            // Subtract reservations
            if (node.getReservations() != null) {
                int reservedQty = node.getReservations().stream()
                        .mapToInt(reservation -> reservation.getQty() != null ? reservation.getQty() : 0)
                        .sum();
                nodeAvailable = Math.max(0, nodeAvailable - reservedQty);
            }

            totalAvailable += nodeAvailable;
            logger.debug("Node {} available quantity: {}", node.getNodeId(), nodeAvailable);
        }

        product.setQuantityAvailable(totalAvailable);
        logger.debug("Recalculated total available quantity for SKU {}: {}", 
                    product.getSku(), totalAvailable);
    }

    /**
     * Add inventory update event to the events list
     */
    private void addInventoryUpdateEvent(Product product) {
        if (product.getEvents() == null) {
            product.setEvents(new ArrayList<>());
        }

        Product.ProductEvent inventoryEvent = new Product.ProductEvent();
        inventoryEvent.setType("InventoryUpdated");
        inventoryEvent.setAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", product.getSku());
        payload.put("quantityAvailable", product.getQuantityAvailable());
        
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            payload.put("nodeCount", product.getInventory().getNodes().size());
        }
        
        inventoryEvent.setPayload(payload);
        product.getEvents().add(inventoryEvent);
        
        logger.debug("Added InventoryUpdated event for SKU: {}", product.getSku());
    }

    /**
     * Validate inventory constraints and policies
     */
    private void validateInventoryConstraints(Product product) {
        // Check for negative inventory
        if (product.getQuantityAvailable() < 0) {
            logger.warn("Negative inventory detected for SKU: {}, quantity: {}", 
                       product.getSku(), product.getQuantityAvailable());
        }

        // Check oversell guard if configured
        if (product.getInventory() != null && 
            product.getInventory().getPolicies() != null && 
            product.getInventory().getPolicies().getOversellGuard() != null) {
            
            Product.ProductInventoryOversellGuard guard = product.getInventory().getPolicies().getOversellGuard();
            if (guard.getMaxPercent() != null && product.getQuantityAvailable() < 0) {
                int maxOversell = Math.abs(product.getQuantityAvailable() * guard.getMaxPercent() / 100);
                if (Math.abs(product.getQuantityAvailable()) > maxOversell) {
                    logger.error("Oversell limit exceeded for SKU: {}, current: {}, max allowed: -{}", 
                               product.getSku(), product.getQuantityAvailable(), maxOversell);
                }
            }
        }

        logger.debug("Inventory constraints validated for SKU: {}", product.getSku());
    }
}
