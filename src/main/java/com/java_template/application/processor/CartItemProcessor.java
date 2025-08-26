package com.java_template.application.processor;

import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
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

import java.time.Instant;
import java.util.Iterator;
import java.util.List;

@Component
public class CartItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ShoppingCart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart entity) {
        return entity != null && entity.isValid();
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart entity = context.entity();

        // Business logic:
        // - Apply item changes semantics: remove invalid items (quantity <= 0, missing sku, negative price)
        // - Update modifiedAt timestamp to current time (ISO format)
        if (entity != null) {
            List<ShoppingCart.Item> items = entity.getItems();
            if (items != null) {
                Iterator<ShoppingCart.Item> it = items.iterator();
                while (it.hasNext()) {
                    ShoppingCart.Item item = it.next();
                    boolean remove = false;
                    if (item == null) {
                        remove = true;
                    } else {
                        if (item.getProductSku() == null || item.getProductSku().isBlank()) {
                            remove = true;
                        } else if (item.getQuantity() == null || item.getQuantity() <= 0) {
                            // Treat zero or negative quantity as removal request
                            remove = true;
                        } else if (item.getPriceAtAdd() == null || item.getPriceAtAdd() < 0.0) {
                            // Invalid price -> remove
                            remove = true;
                        }
                    }
                    if (remove) {
                        it.remove();
                        logger.debug("Removed invalid cart item during processing for cartId={}", entity.getCartId());
                    }
                }
            }
            // Update modifiedAt to current time
            entity.setModifiedAt(Instant.now().toString());
            logger.info("Updated ShoppingCart.modifiedAt for cartId={}, itemsCount={}", entity.getCartId(),
                    entity.getItems() != null ? entity.getItems().size() : 0);
        }

        return entity;
    }
}