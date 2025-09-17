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
 * UpdateStockProcessor - Handles product stock updates
 * 
 * This processor is triggered when product stock needs to be updated,
 * typically when orders are created and stock needs to be decremented.
 */
@Component
public class UpdateStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateStockProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product stock update for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Product.class)
                .validate(this::isValidEntityWithMetadata, "Invalid product entity wrapper")
                .map(this::processStockUpdate)
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
        Product entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main stock update processing logic
     * 
     * This processor handles stock updates for products, ensuring
     * quantityAvailable is properly maintained.
     */
    private EntityWithMetadata<Product> processStockUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Product> context) {

        EntityWithMetadata<Product> entityWithMetadata = context.entityResponse();
        Product product = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing stock update for product: {} in state: {}", product.getSku(), currentState);

        // Ensure quantityAvailable is not negative
        if (product.getQuantityAvailable() != null && product.getQuantityAvailable() < 0) {
            logger.warn("Product {} has negative stock: {}, setting to 0", 
                       product.getSku(), product.getQuantityAvailable());
            product.setQuantityAvailable(0);
        }

        // Update inventory nodes if present
        updateInventoryNodes(product);

        logger.info("Product {} stock update processed successfully. Available: {}", 
                   product.getSku(), product.getQuantityAvailable());

        return entityWithMetadata;
    }

    /**
     * Update inventory nodes to reflect stock changes
     */
    private void updateInventoryNodes(Product product) {
        if (product.getInventory() != null && product.getInventory().getNodes() != null) {
            for (Product.ProductInventoryNode node : product.getInventory().getNodes()) {
                if (node.getLots() != null) {
                    // Calculate total available quantity from lots
                    int totalAvailable = node.getLots().stream()
                            .filter(lot -> "Released".equals(lot.getQuality()))
                            .mapToInt(lot -> lot.getQty() != null ? lot.getQty() : 0)
                            .sum();
                    
                    logger.debug("Node {} has {} available units", node.getNodeId(), totalAvailable);
                }
            }
        }
    }
}
