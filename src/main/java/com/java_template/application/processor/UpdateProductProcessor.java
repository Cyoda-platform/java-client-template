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
 * ABOUTME: Processor for updating Product entities, handling validation,
 * timestamp updates, and product event logging.
 */
@Component
public class UpdateProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateProductProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Product.class)
                .validate(this::isValidEntityWithMetadata, "Invalid product entity wrapper")
                .map(this::processProductUpdate)
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
        return product != null && product.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for product updates
     */
    private EntityWithMetadata<Product> processProductUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing product update for SKU: {}", product.getSku());

        // Validate business rules
        validateProductBusinessRules(product);

        // Update product events with the update event
        addProductUpdateEvent(product);

        // Validate inventory consistency
        validateInventoryConsistency(product);

        logger.info("Product {} updated successfully", product.getSku());

        return entityWithMetadata;
    }

    /**
     * Validate product business rules
     */
    private void validateProductBusinessRules(Product product) {
        // Ensure price is not negative
        if (product.getPrice() < 0) {
            throw new IllegalArgumentException("Product price cannot be negative");
        }

        // Ensure quantity available is not negative
        if (product.getQuantityAvailable() < 0) {
            throw new IllegalArgumentException("Product quantity available cannot be negative");
        }

        // Validate SKU format (basic validation)
        if (product.getSku().length() < 3) {
            throw new IllegalArgumentException("Product SKU must be at least 3 characters long");
        }

        logger.debug("Product business rules validated for SKU: {}", product.getSku());
    }

    /**
     * Add product update event to the events list
     */
    private void addProductUpdateEvent(Product product) {
        if (product.getEvents() == null) {
            product.setEvents(new ArrayList<>());
        }

        Product.ProductEvent updateEvent = new Product.ProductEvent();
        updateEvent.setType("ProductUpdated");
        updateEvent.setAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", product.getSku());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("quantityAvailable", product.getQuantityAvailable());
        updateEvent.setPayload(payload);

        product.getEvents().add(updateEvent);
        
        logger.debug("Added ProductUpdated event for SKU: {}", product.getSku());
    }

    /**
     * Validate inventory consistency
     */
    private void validateInventoryConsistency(Product product) {
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            int totalInventory = product.getInventory().getNodes().stream()
                    .mapToInt(node -> {
                        if (node.getQtyOnHand() != null) {
                            return node.getQtyOnHand();
                        }
                        if (node.getLots() != null) {
                            return node.getLots().stream()
                                    .filter(lot -> "Released".equals(lot.getQuality()))
                                    .mapToInt(lot -> lot.getQty() != null ? lot.getQty() : 0)
                                    .sum();
                        }
                        return 0;
                    })
                    .sum();

            // Log warning if quantityAvailable doesn't match calculated inventory
            if (Math.abs(totalInventory - product.getQuantityAvailable()) > 0) {
                logger.warn("Inventory mismatch for SKU {}: quantityAvailable={}, calculated={}",
                        product.getSku(), product.getQuantityAvailable(), totalInventory);
            }
        }
    }
}
