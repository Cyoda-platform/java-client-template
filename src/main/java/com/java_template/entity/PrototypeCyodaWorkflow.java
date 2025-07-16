Certainly! Moving asynchronous and business logic into the workflow function (`processOrder`) is a great design improvement. It keeps controllers thin, isolates domain logic, and leverages the lifecycle hooks provided by your framework.

Since the workflow function now receives an `ObjectNode` (the JSON representation of the entity), all manipulations will be done via this `ObjectNode`. We must convert our previous `Order` POJO usage to use `ObjectNode` inside `processOrder`.

---

### What to move into `processOrder` workflow function?

- **Payment validation** (previously synchronous in controller) — can be done async inside workflow.
- **Setting default statuses**
- **Setting estimated delivery time (ETA)**
- Any **fire-and-forget async tasks** (e.g. sending notifications, logging analytics, etc.) — you can add CompletableFuture calls here.
- Any other state enrichment or calls to other entities.

---

### What remains in controller?

- Input validation (basic)
- Transforming request into basic ObjectNode or POJO before sending to `addItem`
- Converting response (id) to HTTP response

---

### Updated complete Java code with all async logic moved into `processOrder`

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/api")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;

    private static final List<String> AVAILABLE_SIZES = List.of("small", "medium", "large");
    private static final List<String> AVAILABLE_TOPPINGS = List.of("pepperoni", "mushrooms", "onions", "sausage", "bacon");
    private static final List<String> PAYMENT_METHODS = List.of("credit_card", "paypal", "cash");
    private static final String ENTITY_NAME = "order";

    @PostMapping("/orders")
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        logger.info("Received create order request for customerId={}", request.getCustomerId());

        // Basic validations remain here
        if (!AVAILABLE_SIZES.contains(request.getPizzaSize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pizza size");
        }
        if (request.getToppings().isEmpty() || !AVAILABLE_TOPPINGS.containsAll(request.getToppings())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid toppings");
        }
        if (!PAYMENT_METHODS.contains(request.getPaymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
        }

        // Prepare entity as ObjectNode
        ObjectNode entity = entityService.getObjectMapper().createObjectNode();
        entity.put("customerId", request.getCustomerId());
        entity.put("pizzaSize", request.getPizzaSize());
        entity.putArray("toppings").addAll(request.getToppings().stream()
                .map(entityService.getObjectMapper()::convertValue).toList());
        entity.put("deliveryAddress", request.getDeliveryAddress());
        entity.put("scheduledTime", request.getScheduledTime());
        entity.put("paymentMethod", request.getPaymentMethod());
        entity.put("paymentDetails", request.getPaymentDetails());

        // Pass workflow function processOrder that handles all async logic and state changes
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entity, this::processOrder)
                .thenApply(id -> {
                    CreateOrderResponse resp = new CreateOrderResponse();
                    resp.setOrderId(id.toString());

                    // The 'status' and 'estimatedDeliveryTime' were set inside processOrder workflow,
                    // so we read them back from entity now:
                    resp.setStatus(entity.path("status").asText("created").toLowerCase());
                    resp.setEstimatedDeliveryTime(entity.path("estimatedDeliveryTime").asText(null));

                    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
                });
    }

    /**
     * Workflow function to process the order entity asynchronously before persistence.
     *
     * @param entity The JSON ObjectNode representing the order entity.
     * @return CompletableFuture<ObjectNode> with the processed entity.
     */
    private CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        logger.info("Processing order entity in workflow function processOrder for customerId={}", entity.path("customerId").asText());

        // 1. Validate payment asynchronously (simulate async call)
        return validatePaymentAsync(entity.path("paymentMethod").asText(), entity.path("paymentDetails").asText())
                .thenApply(paymentOk -> {
                    if (!paymentOk) {
                        throw new RuntimeException("Payment validation failed");
                    }
                    return entity;
                })
                .thenApply(enrichedEntity -> {
                    // 2. Set default status if missing
                    if (enrichedEntity.path("status").isMissingNode() || enrichedEntity.path("status").asText().isEmpty()) {
                        enrichedEntity.put("status", "CREATED");
                    }

                    // 3. Set estimated delivery time if missing - 30 minutes from now
                    if (enrichedEntity.path("estimatedDeliveryTime").isMissingNode()) {
                        Instant eta = Instant.now().plusSeconds(30 * 60);
                        enrichedEntity.put("estimatedDeliveryTime", eta.toString());
                    }

                    return enrichedEntity;
                })
                .thenApply(enrichedEntity -> {
                    // 4. Example fire-and-forget async task: send notification (simulated)
                    CompletableFuture.runAsync(() -> sendOrderNotification(enrichedEntity));
                    return enrichedEntity;
                });
    }

    /**
     * Simulate asynchronous payment validation.
     */
    private CompletableFuture<Boolean> validatePaymentAsync(String method, String details) {
        logger.info("Validating payment asynchronously method={}", method);
        // Replace with real async payment gateway call
        return CompletableFuture.supplyAsync(() -> true);
    }

    /**
     * Simulate fire-and-forget notification sending.
     */
    private void sendOrderNotification(ObjectNode orderEntity) {
        logger.info("Sending notification for order customerId={}", orderEntity.path("customerId").asText());
        // Simulate notification logic here
    }

    @GetMapping("/orders/{orderId}")
    public CompletableFuture<ResponseEntity<OrderStatusResponse>> getOrderStatus(@PathVariable @NotBlank String orderId) {
        logger.info("Retrieving status for orderId={}", orderId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(item -> {
                    if (item == null || item.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                    }
                    OrderStatusResponse resp = new OrderStatusResponse();
                    resp.setOrderId(orderId);
                    resp.setStatus(item.path("status").asText("unknown").toLowerCase());
                    resp.setPizzaSize(item.path("pizzaSize").asText(null));
                    resp.setToppings(item.path("toppings").findValuesAsText("toppings"));
                    resp.setDeliveryAddress(item.path("deliveryAddress").asText(null));
                    resp.setEstimatedDeliveryTime(item.path("estimatedDeliveryTime").asText(null));
                    return ResponseEntity.ok(resp);
                });
    }

    @GetMapping("/options")
    public ResponseEntity<AvailableOptionsResponse> getAvailableOptions() {
        logger.info("Listing available options");
        AvailableOptionsResponse resp = new AvailableOptionsResponse();
        resp.setSizes(AVAILABLE_SIZES);
        resp.setToppings(AVAILABLE_TOPPINGS);
        resp.setPaymentMethods(PAYMENT_METHODS);
        return ResponseEntity.ok(resp);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse err = new ErrorResponse();
        err.setError(ex.getStatusCode().toString());
        err.setMessage(ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @Data
    public static class CreateOrderRequest {
        @NotBlank
        private String customerId;
        @NotBlank
        private String pizzaSize;
        @Size(min = 1)
        private List<@NotBlank String> toppings;
        @NotBlank
        private String deliveryAddress;
        @Pattern(regexp = "^\\d{4}-[01]\\d-[0-3]\\d[T ](?:[0-2]\\d:[0-5]\\d:[0-5]\\d)Z?$",
                 message = "scheduledTime must be ISO8601")
        private String scheduledTime;
        @NotBlank
        private String paymentMethod;
        @NotBlank
        private String paymentDetails;
    }

    @Data
    public static class CreateOrderResponse {
        private String orderId;
        private String status;
        private String estimatedDeliveryTime;
    }

    @Data
    public static class OrderStatusResponse {
        private String orderId;
        private String status;
        private String pizzaSize;
        private List<String> toppings;
        private String deliveryAddress;
        private String estimatedDeliveryTime;
    }

    @Data
    public static class AvailableOptionsResponse {
        private List<String> sizes;
        private List<String> toppings;
        private List<String> paymentMethods;
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```

---

### Summary of changes:
- **All async logic (payment validation, setting ETA, firing notifications) moved into `processOrder` workflow function.**
- `processOrder` now takes and returns `CompletableFuture<ObjectNode>`.
- `processOrder` modifies the entity directly by calling `entity.put(...)`.
- Controller only prepares the bare minimal entity JSON and validates input.
- After persistence, controller reads back entity fields set by workflow function to form the response.
- Used `CompletableFuture.runAsync()` inside workflow for fire-and-forget async tasks.
- Removed all business logic and async calls from controller.

This approach makes your controllers thin, puts domain logic and async tasks into workflow functions which run atomically before persistence, and respects the rules (no `addItem` on same entity inside workflow).

---

If you want me to help further with extracting other logic or adding additional workflow functions, just ask!