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
 * ABOUTME: Processor that handles product inventory updates and validates
 * inventory levels to ensure data consistency.
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
                .map(this::processInventoryUpdateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Product> entityWithMetadata) {
        Product product = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return product != null && product.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Product> processInventoryUpdateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        logger.debug("Processing inventory update for product: {}", product.getSku());

        // Validate inventory levels
        if (product.getQuantityAvailable() < 0) {
            logger.warn("Negative inventory detected for product {}: {}", 
                       product.getSku(), product.getQuantityAvailable());
            // Set to 0 to prevent negative inventory
            product.setQuantityAvailable(0);
        }

        // Update inventory events if they exist
        if (product.getEvents() != null) {
            Product.ProductEvent inventoryEvent = new Product.ProductEvent();
            inventoryEvent.setType("InventoryUpdated");
            inventoryEvent.setAt(LocalDateTime.now());
            inventoryEvent.setPayload(java.util.Map.of(
                "sku", product.getSku(),
                "newQuantity", product.getQuantityAvailable()
            ));
            product.getEvents().add(inventoryEvent);
        }

        logger.info("Inventory updated for product {}: {} units available", 
                   product.getSku(), product.getQuantityAvailable());

        return entityWithMetadata;
    }
}
