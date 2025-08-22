package com.java_template.application.processor;
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
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AddItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        if (cart == null) return null;

        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            // Nothing to add; ensure status remains consistent
            if (cart.getStatus() == null || cart.getStatus().isBlank()) {
                cart.setStatus("NEW");
            }
            // leave totals as-is
            return cart;
        }

        // Merge items by productId (sum quantities), ensure unitPrice resolved from Product when missing
        Map<String, CartItem> merged = new LinkedHashMap<>();
        for (CartItem item : items) {
            if (item == null || item.getProductId() == null) continue;
            String pid = item.getProductId();
            CartItem existing = merged.get(pid);
            if (existing == null) {
                // clone to avoid modifying original list instances unexpectedly
                CartItem copy = new CartItem();
                copy.setProductId(item.getProductId());
                copy.setQuantity(item.getQuantity() == null ? 0 : item.getQuantity());
                copy.setUnitPrice(item.getUnitPrice());
                merged.put(pid, copy);
            } else {
                int addQty = item.getQuantity() == null ? 0 : item.getQuantity();
                existing.setQuantity(existing.getQuantity() + addQty);
                // prefer existing unitPrice, if missing try to set from current item
                if (existing.getUnitPrice() == null && item.getUnitPrice() != null) {
                    existing.setUnitPrice(item.getUnitPrice());
                }
            }
        }

        // Resolve missing unit prices by querying Product entity (by business id -> id field)
        for (Map.Entry<String, CartItem> entry : merged.entrySet()) {
            CartItem ci = entry.getValue();
            if (ci.getUnitPrice() == null) {
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group(
                        "AND",
                        Condition.of("$.id", "EQUALS", entry.getKey())
                    );
                    ArrayNode result = entityService.getItemsByCondition(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        condition,
                        true
                    ).join();
                    if (result != null && result.size() > 0) {
                        Product prod = objectMapper.treeToValue(result.get(0), Product.class);
                        if (prod != null && prod.getPrice() != null) {
                            ci.setUnitPrice(prod.getPrice());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to resolve product price for productId={} : {}", entry.getKey(), e.getMessage());
                    // leave unitPrice null if resolution fails
                }
            }
        }

        // Build final items list and compute total amount
        List<CartItem> finalItems = merged.values().stream().collect(Collectors.toList());
        double total = 0.0;
        for (CartItem ci : finalItems) {
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity();
            double up = ci.getUnitPrice() == null ? 0.0 : ci.getUnitPrice();
            total += qty * up;
        }

        // Update cart with merged items and totals
        cart.setItems(finalItems);
        cart.setTotalAmount(total);

        // Set status to ACTIVE when items are present, unless already CONVERTED
        if (cart.getStatus() == null || !"CONVERTED".equalsIgnoreCase(cart.getStatus())) {
            cart.setStatus("ACTIVE");
        }

        // Update timestamp
        cart.setUpdatedAt(Instant.now().toString());

        return cart;
    }
}