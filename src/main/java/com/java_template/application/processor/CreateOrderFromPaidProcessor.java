package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.Line;
import com.java_template.application.entity.order.version_1.Order.Address;
import com.java_template.application.entity.order.version_1.Order.Totals;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.Line as ShipmentLine;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import java.util.concurrent.CompletableFuture;

@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory,
                                        EntityService entityService,
                                        ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid() && "PAID".equalsIgnoreCase(entity.getStatus());
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        try {
            // Load Cart
            CompletableFuture<ObjectNode> cartFuture = entityService.getItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                UUID.fromString(payment.getCartId())
            );
            ObjectNode cartNode = cartFuture.join();
            if (cartNode == null) {
                logger.warn("Cart not found for payment.cartId={}", payment.getCartId());
                return payment;
            }
            Cart cart = objectMapper.treeToValue(cartNode, Cart.class);

            // Validate cart not empty
            if (cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.warn("Cart is empty for cartId={}", cart.getCartId());
                // leave cart as ACTIVE and return
                cart.setStatus("ACTIVE");
                entityService.updateItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    UUID.fromString(cart.getCartId()),
                    cart
                ).join();
                return payment;
            }

            // Validate reservations active for batch
            if (cart.getReservationBatchId() == null || cart.getReservationBatchId().isBlank()) {
                logger.warn("No reservationBatchId for cartId={}", cart.getCartId());
                // Treat as failure: revert cart to ACTIVE
                cart.setStatus("ACTIVE");
                entityService.updateItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    UUID.fromString(cart.getCartId()),
                    cart
                ).join();
                return payment;
            }

            SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.reservationBatchId", "EQUALS", cart.getReservationBatchId()),
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );

            CompletableFuture<ArrayNode> reservationsFuture = entityService.getItemsByCondition(
                Reservation.ENTITY_NAME,
                String.valueOf(Reservation.ENTITY_VERSION),
                cond,
                true
            );
            ArrayNode reservationsArray = reservationsFuture.join();
            if (reservationsArray == null || reservationsArray.size() == 0) {
                logger.warn("No active reservations for batchId={}", cart.getReservationBatchId());
                cart.setStatus("ACTIVE");
                entityService.updateItem(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    UUID.fromString(cart.getCartId()),
                    cart
                ).join();
                return payment;
            }

            // Check product availability & prepare updates
            List<Reservation> reservations = new ArrayList<>();
            for (int i = 0; i < reservationsArray.size(); i++) {
                ObjectNode rNode = (ObjectNode) reservationsArray.get(i);
                Reservation r = objectMapper.treeToValue(rNode, Reservation.class);
                reservations.add(r);
            }

            // For each reservation, check product availability
            for (Reservation r : reservations) {
                // find product by sku
                SearchConditionRequest pcond = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", r.getSku())
                );
                CompletableFuture<ArrayNode> pFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    pcond,
                    true
                );
                ArrayNode pArr = pFuture.join();
                if (pArr == null || pArr.size() == 0) {
                    logger.warn("Product not found for sku={} while committing reservation={}", r.getSku(), r.getReservationId());
                    // fail and revert cart
                    cart.setStatus("ACTIVE");
                    entityService.updateItem(
                        Cart.ENTITY_NAME,
                        String.valueOf(Cart.ENTITY_VERSION),
                        UUID.fromString(cart.getCartId()),
                        cart
                    ).join();
                    return payment;
                }
                ObjectNode pNode = (ObjectNode) pArr.get(0);
                Product product = objectMapper.treeToValue(pNode, Product.class);

                if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < r.getQty()) {
                    logger.warn("Insufficient stock for sku={} required={} available={}", r.getSku(), r.getQty(), product.getQuantityAvailable());
                    // revert cart to ACTIVE and return
                    cart.setStatus("ACTIVE");
                    entityService.updateItem(
                        Cart.ENTITY_NAME,
                        String.valueOf(Cart.ENTITY_VERSION),
                        UUID.fromString(cart.getCartId()),
                        cart
                    ).join();
                    return payment;
                }
            }

            // All reservations can be committed. Apply updates: decrement product stock and mark reservations COMMITTED
            for (Reservation r : reservations) {
                // load product
                SearchConditionRequest pcond = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", r.getSku())
                );
                ArrayNode pArr = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    pcond,
                    true
                ).join();
                ObjectNode pNode = (ObjectNode) pArr.get(0);
                Product product = objectMapper.treeToValue(pNode, Product.class);

                int newQty = product.getQuantityAvailable() - r.getQty();
                product.setQuantityAvailable(newQty);
                // update product
                entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(product.getProductId()),
                    product
                ).join();

                // mark reservation COMMITTED
                r.setStatus("COMMITTED");
                entityService.updateItem(
                    Reservation.ENTITY_NAME,
                    String.valueOf(Reservation.ENTITY_VERSION),
                    UUID.fromString(r.getReservationId()),
                    r
                ).join();
            }

            // Snapshot user address
            CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                com.java_template.application.entity.user.version_1.User.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.user.version_1.User.ENTITY_VERSION),
                UUID.fromString(cart.getUserId())
            );
            ObjectNode userNode = userFuture.join();
            Address shippingAddress = new Address();
            if (userNode != null) {
                com.java_template.application.entity.user.version_1.User user = objectMapper.treeToValue(userNode, com.java_template.application.entity.user.version_1.User.class);
                if (user.getAddress() != null) {
                    shippingAddress.setLine1(user.getAddress().getLine1());
                    shippingAddress.setCity(user.getAddress().getCity());
                    shippingAddress.setPostcode(user.getAddress().getPostcode());
                    shippingAddress.setCountry(user.getAddress().getCountry());
                }
            }

            // Build Order from cart
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            // short ULID-like orderNumber
            String orderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            order.setOrderNumber(orderNumber);
            order.setUserId(cart.getUserId());
            order.setShippingAddress(shippingAddress);

            List<Order.Line> oLines = new ArrayList<>();
            double itemsTotal = 0.0;
            for (Cart.Line cl : cart.getLines()) {
                Order.Line ol = new Order.Line();
                ol.setSku(cl.getSku());
                ol.setName(cl.getName());
                ol.setQty(cl.getQty());
                ol.setUnitPrice(cl.getPrice());
                double lineTotal = cl.getPrice() * cl.getQty();
                ol.setLineTotal(lineTotal);
                itemsTotal += lineTotal;
                oLines.add(ol);
            }
            order.setLines(oLines);

            double tax = itemsTotal * 0.10;
            double shipping = 5.0;
            double grand = itemsTotal + tax + shipping;

            Totals totals = new Totals();
            totals.setItems(itemsTotal);
            totals.setTax(tax);
            totals.setShipping(shipping);
            totals.setGrand(grand);
            order.setTotals(totals);

            order.setCreatedAt(Instant.now().toString());
            order.setUpdatedAt(order.getCreatedAt());

            // create shipments by splitting lines (max 3 lines per shipment)
            List<Shipment> createdShipments = new ArrayList<>();
            int maxLinesPerShipment = 3;
            List<List<Order.Line>> partitions = new ArrayList<>();
            for (int i = 0; i < oLines.size(); i += maxLinesPerShipment) {
                int end = Math.min(i + maxLinesPerShipment, oLines.size());
                partitions.add(new ArrayList<>(oLines.subList(i, end)));
            }

            // Set order status to PICKING as shipments will be created
            order.setStatus("PICKING");

            // Persist Order
            CompletableFuture<UUID> orderAdd = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            );
            UUID orderTechnicalId = orderAdd.join();

            // Create Shipments
            for (List<Order.Line> chunk : partitions) {
                Shipment sh = new Shipment();
                sh.setShipmentId(UUID.randomUUID().toString());
                sh.setOrderId(order.getOrderId());
                sh.setStatus("PICKING");
                sh.setCreatedAt(Instant.now().toString());
                sh.setUpdatedAt(sh.getCreatedAt());

                List<Shipment.Line> sLines = new ArrayList<>();
                for (Order.Line ol : chunk) {
                    Shipment.Line sl = new Shipment.Line();
                    sl.setSku(ol.getSku());
                    sl.setQtyOrdered(ol.getQty());
                    sl.setQtyPicked(0);
                    sl.setQtyShipped(0);
                    sLines.add(sl);
                }
                sh.setLines(sLines);

                entityService.addItem(
                    Shipment.ENTITY_NAME,
                    String.valueOf(Shipment.ENTITY_VERSION),
                    sh
                ).join();
                createdShipments.add(sh);
            }

            // Mark cart as CONVERTED
            cart.setStatus("CONVERTED");
            entityService.updateItem(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                UUID.fromString(cart.getCartId()),
                cart
            ).join();

            logger.info("Order {} created for cartId={} paymentId={}", order.getOrderNumber(), cart.getCartId(), payment.getPaymentId());

        } catch (Exception e) {
            logger.error("Error creating order from paymentId={}: {}", payment.getPaymentId(), e.getMessage(), e);
            // On error, best-effort revert cart to ACTIVE if possible
            try {
                if (payment.getCartId() != null && !payment.getCartId().isBlank()) {
                    CompletableFuture<ObjectNode> cartFuture = entityService.getItem(
                        Cart.ENTITY_NAME,
                        String.valueOf(Cart.ENTITY_VERSION),
                        UUID.fromString(payment.getCartId())
                    );
                    ObjectNode cartNode = cartFuture.join();
                    if (cartNode != null) {
                        Cart cart = objectMapper.treeToValue(cartNode, Cart.class);
                        cart.setStatus("ACTIVE");
                        entityService.updateItem(
                            Cart.ENTITY_NAME,
                            String.valueOf(Cart.ENTITY_VERSION),
                            UUID.fromString(cart.getCartId()),
                            cart
                        ).join();
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to revert cart status after error for paymentId={}", payment.getPaymentId(), ex);
            }
        }

        return payment;
    }
}