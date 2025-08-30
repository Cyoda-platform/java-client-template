package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        return entity != null && entity.isValid() && "PAID".equalsIgnoreCase(entity.getStatus());
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        if (payment == null) {
            logger.warn("Payment entity is null in context");
            return payment;
        }

        // Only process when payment status is PAID (extra safety)
        if (!"PAID".equalsIgnoreCase(payment.getStatus())) {
            logger.info("Payment {} is not PAID (status={}), skipping order creation", payment.getPaymentId(), payment.getStatus());
            return payment;
        }

        try {
            // 1. Load Cart referenced by payment.cartId
            String cartTechnicalId = payment.getCartId();
            if (cartTechnicalId == null || cartTechnicalId.isBlank()) {
                logger.error("Payment {} has no cartId, aborting CreateOrderFromPaidProcessor", payment.getPaymentId());
                return payment;
            }

            CompletableFuture<DataPayload> cartFuture = entityService.getItem(UUID.fromString(cartTechnicalId));
            DataPayload cartPayload = cartFuture.get();
            if (cartPayload == null || cartPayload.getData() == null) {
                logger.error("Cart not found for id {} referenced by payment {}", cartTechnicalId, payment.getPaymentId());
                return payment;
            }

            Cart cart = objectMapper.treeToValue(cartPayload.getData(), Cart.class);
            if (cart == null || cart.getLines() == null || cart.getLines().isEmpty()) {
                logger.error("Cart {} is empty or invalid, cannot create order", cartTechnicalId);
                return payment;
            }

            // 2. Build Order from Cart snapshot
            Order order = new Order();
            order.setOrderId(UUID.randomUUID().toString());
            // Generate a short order number (simple short id)
            String shortOrderNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            order.setOrderNumber(shortOrderNumber);
            order.setStatus("WAITING_TO_FULFILL");
            String now = Instant.now().toString();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // Guest contact snapshot (copy reference) - map fields from Cart.GuestContact to Order.GuestContact
            Cart.GuestContact cartGuest = cart.getGuestContact();
            if (cartGuest != null) {
                Order.GuestContact orderGuest = new Order.GuestContact();
                if (cartGuest.getAddress() != null) {
                    Cart.Address ca = cartGuest.getAddress();
                    Order.Address oa = new Order.Address();
                    oa.setCity(ca.getCity());
                    oa.setCountry(ca.getCountry());
                    oa.setLine1(ca.getLine1());
                    oa.setPostcode(ca.getPostcode());
                    orderGuest.setAddress(oa);
                } else {
                    orderGuest.setAddress(null);
                }
                orderGuest.setEmail(cartGuest.getEmail());
                orderGuest.setName(cartGuest.getName());
                orderGuest.setPhone(cartGuest.getPhone());
                order.setGuestContact(orderGuest);
            } else {
                order.setGuestContact(null);
            }

            // Lines mapping
            List<Order.Line> orderLines = new ArrayList<>();
            double itemsTotal = 0.0;
            for (Cart.Line cartLine : cart.getLines()) {
                Order.Line ol = new Order.Line();
                ol.setSku(cartLine.getSku());
                ol.setName(cartLine.getName());
                ol.setQty(cartLine.getQty());
                ol.setUnitPrice(cartLine.getPrice());
                double lineTotal = 0.0;
                if (cartLine.getPrice() != null && cartLine.getQty() != null) {
                    lineTotal = cartLine.getPrice() * cartLine.getQty();
                }
                ol.setLineTotal(lineTotal);
                itemsTotal += lineTotal;
                orderLines.add(ol);
            }
            order.setLines(orderLines);

            Order.Totals totals = new Order.Totals();
            totals.setItems(itemsTotal);
            totals.setGrand(itemsTotal); // no shipping/taxes for demo
            order.setTotals(totals);

            // 3. Persist Order
            CompletableFuture<java.util.UUID> addOrderFuture = entityService.addItem(Order.ENTITY_NAME, Order.ENTITY_VERSION, order);
            java.util.UUID createdOrderId = addOrderFuture.get();
            logger.info("Created Order entity with technical id {}", createdOrderId);

            // 4. For each order line, decrement Product.quantityAvailable
            for (Order.Line ol : orderLines) {
                if (ol.getSku() == null || ol.getSku().isBlank()) {
                    logger.warn("Skipping product update for empty sku in order {}", order.getOrderId());
                    continue;
                }

                // Build condition to find product by sku
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.sku", "EQUALS", ol.getSku())
                );

                CompletableFuture<List<DataPayload>> productsFuture = entityService.getItemsByCondition(Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);
                List<DataPayload> productPayloads = productsFuture.get();
                if (productPayloads == null || productPayloads.isEmpty()) {
                    logger.warn("Product with sku {} not found; skipping quantity decrement", ol.getSku());
                    continue;
                }

                // Use first match (sku unique)
                DataPayload prodPayload = productPayloads.get(0);
                JsonNode prodNode = prodPayload.getData();
                Product product = objectMapper.treeToValue(prodNode, Product.class);
                if (product == null) {
                    logger.warn("Failed to deserialize product for sku {}, skipping", ol.getSku());
                    continue;
                }

                // Extract technical id of the product from meta
                String productTechnicalId = prodPayload.getMeta() != null && prodPayload.getMeta().get("entityId") != null
                        ? prodPayload.getMeta().get("entityId").asText()
                        : null;
                if (productTechnicalId == null) {
                    logger.warn("No technical id available for product sku {}, skipping update", ol.getSku());
                    continue;
                }

                Integer currentQty = product.getQuantityAvailable();
                int decrement = ol.getQty() != null ? ol.getQty() : 0;
                int updatedQty = (currentQty != null ? currentQty : 0) - decrement;
                if (updatedQty < 0) updatedQty = 0;
                product.setQuantityAvailable(updatedQty);

                // Persist product update
                try {
                    CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(UUID.fromString(productTechnicalId), product);
                    java.util.UUID updatedProductId = updateFuture.get();
                    logger.info("Updated product {} quantity to {} (entityId={})", product.getSku(), updatedQty, updatedProductId);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to update product {} quantity: {}", product.getSku(), e.getMessage(), e);
                }
            }

            // 5. Create Shipment (single shipment per order)
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(now);
            shipment.setUpdatedAt(now);

            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.Line ol : orderLines) {
                Shipment.ShipmentLine sl = new Shipment.ShipmentLine();
                sl.setSku(ol.getSku());
                sl.setQtyOrdered(ol.getQty() != null ? ol.getQty() : 0);
                sl.setQtyPicked(0);
                sl.setQtyShipped(0);
                shipmentLines.add(sl);
            }
            shipment.setLines(shipmentLines);

            CompletableFuture<java.util.UUID> addShipmentFuture = entityService.addItem(Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, shipment);
            java.util.UUID createdShipmentId = addShipmentFuture.get();
            logger.info("Created Shipment {} (entityId={}) for Order {}", shipment.getShipmentId(), createdShipmentId, order.getOrderId());

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating order for payment {}: {}", payment.getPaymentId(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error while creating order for payment {}: {}", payment.getPaymentId(), ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error in CreateOrderFromPaidProcessor for payment {}: {}", payment.getPaymentId(), ex.getMessage(), ex);
        }

        // Do not modify the triggering Payment entity here; return it unchanged
        return payment;
    }
}