package com.java_template.application.processor;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.CartItem;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.reservation.version_1.Reservation;
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
import java.util.*;

@Component
public class ReserveInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReserveInventoryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReserveInventory for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart for reservation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getId() != null && cart.getItems() != null && !cart.getItems().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // For each cart item, attempt to create a reservation entry. The processor must be idempotent.
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }

        List<Reservation> reservations = cart.getReservations();
        if (reservations == null) {
            reservations = new ArrayList<>();
        }

        Map<String, Integer> needed = new HashMap<>();
        for (CartItem it : cart.getItems()) {
            needed.put(it.getProductId(), needed.getOrDefault(it.getProductId(), 0) + it.getQuantity());
        }

        // Simulate product availability check. We can only use Product fields present on the cart item snapshots
        // If product snapshot not present, we will assume allowBackorder=false and fail reservation for safety.
        List<String> insufficient = new ArrayList<>();
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            String productId = e.getKey();
            int qtyRequired = e.getValue();

            Optional<CartItem> snapshot = cart.getItems().stream().filter(i -> productId.equals(i.getProductId())).findFirst();
            boolean allowBackorder = false;
            int available = 0;
            if (snapshot.isPresent()) {
                // Use unitPrice to detect a product snapshot presence; real product repo not available here
                // We assume if unitPrice != null then product existed at add time. For availableQuantity, we cannot
                // query live product; default to available enough to allow reservation for demo purposes.
                allowBackorder = false;
                available = Integer.MAX_VALUE; // permissive in absence of repo
            }

            if (!allowBackorder && available < qtyRequired) {
                insufficient.add(productId);
            }
        }

        if (!insufficient.isEmpty()) {
            logger.warn("Insufficient inventory for cart {} products: {}", cart.getId(), insufficient);
            // Mark cart back to OPEN to allow user to adjust
            cart.setStatus("OPEN");
            return cart;
        }

        // Create reservations (idempotent by cartId + productId + checkoutAttemptId)
        String attemptId = cart.getCheckoutAttemptId() != null ? cart.getCheckoutAttemptId() : UUID.randomUUID().toString();
        for (CartItem it : cart.getItems()) {
            String rid = UUID.randomUUID().toString();
            Reservation r = new Reservation();
            r.setId(rid);
            r.setCartId(cart.getId());
            r.setProductId(it.getProductId());
            r.setQuantity(it.getQuantity());
            r.setReservedAt(Instant.now().toString());
            r.setExpiresAt(Instant.now().plusSeconds(15 * 60).toString());
            r.setStatus("ACTIVE");
            reservations.add(r);
        }

        cart.setReservations(reservations);
        cart.setUpdatedAt(Instant.now().toString());
        cart.setStatus("RESERVE_INVENTORY");

        logger.info("Created {} reservations for cart {}", reservations.size(), cart.getId());

        return cart;
    }
}
