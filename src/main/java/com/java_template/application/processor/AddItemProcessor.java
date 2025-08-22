package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AddItemProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        // Allow processing of carts in any state (we don't enforce full entity.isValid() here,
        // because initial NEW carts may be created with empty items list).
        return entity != null;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        if (cart == null) return null;

        // Ensure items list is initialized
        List<CartItem> items = cart.getItems();
        if (items == null) {
            items = new ArrayList<>();
        }

        // Ensure status is set (default to NEW if absent)
        if (cart.getStatus() == null || cart.getStatus().isBlank()) {
            cart.setStatus("NEW");
        }

        // Normalize items: for each item, ensure quantity >= 0 and attempt to resolve unitPrice from Product if missing
        for (CartItem ci : items) {
            if (ci == null) continue;
            // Defensive defaults
            if (ci.getQuantity() == null) ci.setQuantity(0);
            // If unitPrice missing attempt to read product price via EntityService
            if (ci.getUnitPrice() == null && ci.getProductId() != null && !ci.getProductId().isBlank()) {
                try {
                    // product technical id is expected to be a UUID string; if not, getItem may fail and we handle gracefully
                    UUID productTechnicalId = UUID.fromString(ci.getProductId());
                    ObjectNode prodNode = entityService.getItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        productTechnicalId
                    ).join();

                    if (prodNode != null) {
                        // the returned structure may contain an "entity" wrapper; try both
                        JsonNode entityNode = prodNode.has("entity") ? prodNode.get("entity") : prodNode;
                        if (entityNode != null && entityNode.has("price") && !entityNode.get("price").isNull()) {
                            try {
                                double price = entityNode.get("price").asDouble();
                                ci.setUnitPrice(price);
                            } catch (Exception e) {
                                logger.debug("Failed to parse product.price for product {}: {}", ci.getProductId(), e.getMessage());
                            }
                        }
                    }
                } catch (IllegalArgumentException ie) {
                    // product id is not a valid UUID - skip resolving price
                    logger.debug("Product id {} is not a valid UUID - skipping price resolution", ci.getProductId());
                } catch (Exception ex) {
                    logger.warn("Failed to fetch product {} to resolve unitPrice: {}", ci.getProductId(), ex.getMessage());
                }
            }
        }

        // Compute totals
        double total = 0.0;
        for (CartItem ci : items) {
            if (ci == null) continue;
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity();
            double up = ci.getUnitPrice() == null ? 0.0 : ci.getUnitPrice();
            total += qty * up;
        }

        // Update cart fields
        cart.setItems(items);
        cart.setTotalAmount(total);

        // Transition to ACTIVE when there are items and cart is not CONVERTED
        boolean hasItems = items.stream().anyMatch(i -> i != null && i.getQuantity() != null && i.getQuantity() > 0);
        if (hasItems) {
            String status = cart.getStatus();
            if (status == null || !"CONVERTED".equalsIgnoreCase(status)) {
                cart.setStatus("ACTIVE");
            }
        }

        // Update timestamp
        cart.setUpdatedAt(Instant.now().toString());

        logger.debug("AddItemProcessor completed for cart id={}. Items={}, total={}", cart.getId(), items.size(), cart.getTotalAmount());
        return cart;
    }
}