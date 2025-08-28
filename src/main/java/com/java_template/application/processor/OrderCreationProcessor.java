package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderCreationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        try {
            // Only act on APPROVED payments
            if (!"APPROVED".equalsIgnoreCase(payment.getStatus())) {
                logger.debug("Payment {} is not APPROVED, skipping Order creation.", payment.getId());
                return payment;
            }

            // Load Cart
            if (payment.getCartId() == null || payment.getCartId().isBlank()) {
                logger.warn("Payment {} has no cartId, skipping.", payment.getId());
                return payment;
            }
            CompletableFuture<DataPayload> cartFuture = entityService.getItem(Cart.ENTITY_NAME, Cart.ENTITY_VERSION, UUID.fromString(payment.getCartId()));
            DataPayload cartPayload = cartFuture.get();
            if (cartPayload == null || cartPayload.getData() == null) {
                logger.warn("Cart not found for payment {} cartId={}", payment.getId(), payment.getCartId());
                return payment;
            }
            Cart cart = objectMapper.treeToValue(cartPayload.getData(), Cart.class);

            // Load User snapshot
            User user = null;
            if (cart.getUserId() != null && !cart.getUserId().isBlank()) {
                try {
                    CompletableFuture<DataPayload> userFuture = entityService.getItem(User.ENTITY_NAME, User.ENTITY_VERSION, UUID.fromString(cart.getUserId()));
                    DataPayload userPayload = userFuture.get();
                    if (userPayload != null && userPayload.getData() != null) {
                        user = objectMapper.treeToValue(userPayload.getData(), User.class);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load user {} for cart {}: {}", cart.getUserId(), cart.getId(), e.getMessage());
                }
            }

            // Fetch Reservations for this cart (ACTIVE reservations only)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.cartId", "EQUALS", cart.getId()),
                    Condition.of("$.status", "EQUALS", "ACTIVE")
            );
            CompletableFuture<List<DataPayload>> reservationsFuture = entityService.getItemsByCondition(Reservation.ENTITY_NAME, Reservation.ENTITY_VERSION, condition, true);
            List<DataPayload> reservationPayloads = reservationsFuture.get();
            List<Reservation> reservations = new ArrayList<>();
            if (reservationPayloads != null) {
                for (DataPayload p : reservationPayloads) {
                    if (p != null && p.getData() != null) {
                        Reservation r = objectMapper.treeToValue(p.getData(), Reservation.class);
                        reservations.add(r);
                    }
                }
            }

            // Map reservations by productId and warehouseId
            Map<String, Map<String, Integer>> reservedByProductAndWarehouse = new HashMap<>();
            for (Reservation r : reservations) {
                String productId = r.getProductId();
                String warehouseId = r.getWarehouseId();
                Integer qty = r.getQty() != null ? r.getQty() : 0;
                reservedByProductAndWarehouse
                    .computeIfAbsent(productId, k -> new HashMap<>())
                    .merge(warehouseId, qty, Integer::sum);
            }

            // Build order items: include only quantities that have reservations (available)
            List<Order.OrderItem> orderItems = new ArrayList<>();
            Map<String, Integer> totalReservedByProduct = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> e : reservedByProductAndWarehouse.entrySet()) {
                String productId = e.getKey();
                int totalReserved = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
                totalReservedByProduct.put(productId, totalReserved);
            }

            // For each cart item, determine qtyOrdered = min(cart.qty, totalReserved)
            for (Cart.CartItem cartItem : cart.getItems()) {
                String prodId = cartItem.getProductId();
                Integer cartQty = cartItem.getQty() != null ? cartItem.getQty() : 0;
                Integer reservedQty = totalReservedByProduct.getOrDefault(prodId, 0);
                int qtyOrdered = Math.min(cartQty, reservedQty);
                if (qtyOrdered <= 0) continue; // skip unavailable items
                Order.OrderItem oi = new Order.OrderItem();
                oi.setProductId(prodId);
                oi.setQtyOrdered(qtyOrdered);
                oi.setQtyFulfilled(0);
                oi.setPrice(cartItem.getPriceSnapshot());
                orderItems.add(oi);
            }

            if (orderItems.isEmpty()) {
                logger.warn("No available items to create Order for payment {} cart {}", payment.getId(), cart.getId());
                return payment;
            }

            // Compute totalAmount
            double totalAmount = 0.0;
            for (Order.OrderItem oi : orderItems) {
                totalAmount += (oi.getPrice() != null ? oi.getPrice() : 0.0) * oi.getQtyOrdered();
            }

            // Create Order entity
            Order order = new Order();
            order.setId(UUID.randomUUID().toString());
            order.setCartId(cart.getId());
            order.setCreatedAt(Instant.now().toString());
            order.setItems(orderItems);
            order.setOrderNumber(generateOrderNumber());
            order.setStatus("PICKING");
            order.setTotalAmount(totalAmount);

            // Build user snapshot if available
            if (user != null) {
                Order.UserSnapshot us = new Order.UserSnapshot();
                us.setEmail(user.getEmail());
                us.setName(user.getName());
                Order.Address addr = new Order.Address();
                if (user.getPrimaryAddress() != null) {
                    addr.setLine1(user.getPrimaryAddress().getLine1());
                    addr.setLine2(user.getPrimaryAddress().getLine2());
                    addr.setCity(user.getPrimaryAddress().getCity());
                    addr.setCountry(user.getPrimaryAddress().getCountry());
                    addr.setPostal(user.getPrimaryAddress().getPostal());
                }
                us.setAddress(addr);
                order.setUserSnapshot(us);
            } else {
                // if no user, set blank snapshot to satisfy validation minimally
                Order.UserSnapshot us = new Order.UserSnapshot();
                us.setEmail("unknown");
                us.setName("unknown");
                Order.Address addr = new Order.Address();
                addr.setLine1("unknown");
                addr.setCity("unknown");
                addr.setCountry("unknown");
                addr.setPostal("unknown");
                us.setAddress(addr);
                order.setUserSnapshot(us);
            }

            // Persist Order
            CompletableFuture<java.util.UUID> orderAddFuture = entityService.addItem(Order.ENTITY_NAME, Order.ENTITY_VERSION, order);
            java.util.UUID createdOrderId = orderAddFuture.get();
            // note: createdOrderId is the technical id; our order.id already contains UUID string
            logger.info("Created Order {} for Payment {}", order.getId(), payment.getId());

            // Create Shipments per warehouse and allocate quantities not exceeding qtyOrdered
            // Build a map productId -> remainingQty to allocate across warehouses
            Map<String, Integer> remainingToAllocate = orderItems.stream()
                    .collect(Collectors.toMap(Order.OrderItem::getProductId, Order.OrderItem::getQtyOrdered, Integer::sum));

            // For each warehouse, create a shipment with allocations for that warehouse
            Map<String, Map<String, Integer>> warehouseAllocations = new HashMap<>(); // warehouseId -> (productId -> qty)
            for (Map.Entry<String, Map<String, Integer>> prodEntry : reservedByProductAndWarehouse.entrySet()) {
                String productId = prodEntry.getKey();
                Map<String, Integer> byWarehouse = prodEntry.getValue();
                for (Map.Entry<String, Integer> whEntry : byWarehouse.entrySet()) {
                    String warehouseId = whEntry.getKey();
                    Integer reservedQty = whEntry.getValue();
                    int remaining = remainingToAllocate.getOrDefault(productId, 0);
                    if (remaining <= 0) continue;
                    int allocate = Math.min(remaining, reservedQty);
                    if (allocate <= 0) continue;
                    warehouseAllocations
                            .computeIfAbsent(warehouseId, k -> new HashMap<>())
                            .merge(productId, allocate, Integer::sum);
                    remainingToAllocate.put(productId, remaining - allocate);
                }
            }

            // Persist shipments
            for (Map.Entry<String, Map<String, Integer>> wa : warehouseAllocations.entrySet()) {
                String warehouseId = wa.getKey();
                Map<String, Integer> prodMap = wa.getValue();
                Shipment shipment = new Shipment();
                shipment.setId(UUID.randomUUID().toString());
                shipment.setShipmentNumber(generateOrderNumber());
                shipment.setOrderId(order.getId());
                shipment.setStatus("PENDING_PICK");
                shipment.setCreatedAt(Instant.now().toString());
                shipment.setWarehouseId(warehouseId);

                List<Shipment.ShipmentItem> shipmentItems = new ArrayList<>();
                for (Map.Entry<String, Integer> pe : prodMap.entrySet()) {
                    Shipment.ShipmentItem si = new Shipment.ShipmentItem();
                    si.setProductId(pe.getKey());
                    si.setQty(pe.getValue());
                    shipmentItems.add(si);
                }
                shipment.setItems(shipmentItems);

                CompletableFuture<java.util.UUID> shipFuture = entityService.addItem(Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, shipment);
                java.util.UUID createdShipmentId = shipFuture.get();
                logger.info("Created Shipment {} for Order {} warehouse {}", shipment.getId(), order.getId(), warehouseId);
            }

            // Link Order to Payment (modify the triggering entity). Allowed to change current entity state; persistence handled by workflow.
            payment.setOrderId(order.getId());

        } catch (Exception e) {
            logger.error("Failed to create Order for payment {}: {}", payment != null ? payment.getId() : "null", e.getMessage(), e);
            // Do not throw - return original entity so workflow can continue; error can be retried by platform if needed.
        }
        return payment;
    }

    private String generateOrderNumber() {
        // Lightweight unique order number using timestamp and random UUID fragment.
        // Attempts to be monotonically increasing via timestamp prefix.
        String ts = Long.toString(Instant.now().toEpochMilli(), 36).toUpperCase();
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return ts + rand;
    }
}