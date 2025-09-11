package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * CartRecalculateTotalsProcessor - Recalculates cart totals when items are added, updated, or removed.
 * 
 * Transitions: ADD_FIRST_ITEM, MODIFY_ITEMS
 * 
 * Business Logic:
 * - Validates all line SKUs exist in Product catalog
 * - Updates line prices and names from current product data
 * - Recalculates totalItems and grandTotal
 * - Updates timestamp
 */
@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartRecalculateTotalsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartRecalculation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && technicalId != null && cart.getLines() != null;
    }

    /**
     * Main business logic for cart recalculation
     */
    private EntityWithMetadata<Cart> processCartRecalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Initialize totals
        int totalItems = 0;
        double grandTotal = 0.0;

        // Process each line in the cart
        for (Cart.CartLine line : cart.getLines()) {
            // Validate SKU exists in Product catalog
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                throw new IllegalArgumentException("Line SKU cannot be null or empty");
            }

            // Get product data to update line price and name
            try {
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec,
                        line.getSku(),
                        "sku",
                        Product.class
                );
                
                Product product = productWithMetadata.entity();
                
                // Update line with current product data
                line.setPrice(product.getPrice());
                line.setName(product.getName());
                
                // Calculate line contribution to totals
                if (line.getQty() != null && line.getQty() > 0) {
                    double lineTotal = line.getPrice() * line.getQty();
                    totalItems += line.getQty();
                    grandTotal += lineTotal;
                }
                
                logger.debug("Updated line for SKU: {} with price: {} and name: {}", 
                           line.getSku(), line.getPrice(), line.getName());
                
            } catch (Exception e) {
                logger.error("Product not found for SKU: {}", line.getSku());
                throw new IllegalArgumentException("Product not found: " + line.getSku(), e);
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} recalculated: {} items, total: {}", 
                   cart.getCartId(), totalItems, grandTotal);

        return entityWithMetadata;
    }
}
