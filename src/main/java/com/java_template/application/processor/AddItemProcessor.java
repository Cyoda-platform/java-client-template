package com.java_template.application.processor;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.CartItem;
import com.java_template.application.entity.product.version_1.Product;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart AddItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getId() != null;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Basic validation
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }

        // The incoming event may contain a single item delta in metadata or payload snapshot. We will assume
        // the serializer populated cart.items with the intended final state or delta. To be safe, we merge items
        // by productId: if an item exists increment quantity, otherwise add.
        List<CartItem> items = cart.getItems();
        List<CartItem> merged = new ArrayList<>();

        for (CartItem incoming : items) {
            Optional<CartItem> existingOpt = merged.stream()
                .filter(i -> i.getProductId() != null && i.getProductId().equals(incoming.getProductId()))
                .findFirst();
            if (existingOpt.isPresent()) {
                CartItem existing = existingOpt.get();
                existing.setQuantity(existing.getQuantity() + incoming.getQuantity());
            } else {
                // Ensure unitPrice snapshot exists; if missing, attempt to use unitPrice on incoming or zero
                if (incoming.getUnitPrice() == null) {
                    incoming.setUnitPrice(BigDecimal.ZERO);
                }
                merged.add(incoming);
            }
        }

        cart.setItems(merged);

        // Recalculate totalAmount (sum of quantity * unitPrice)
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem it : merged) {
            if (it.getUnitPrice() == null) {
                it.setUnitPrice(BigDecimal.ZERO);
            }
            BigDecimal line = it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
            total = total.add(line);
        }
        cart.setTotalAmount(total);

        cart.setUpdatedAt(Instant.now().toString());
        // Note: Cart model does not include lastActivityAt field; we avoid setting non-existing fields

        logger.info("Cart {} updated: {} items, total={}", cart.getId(), cart.getItems().size(), cart.getTotalAmount());

        return cart;
    }
}
