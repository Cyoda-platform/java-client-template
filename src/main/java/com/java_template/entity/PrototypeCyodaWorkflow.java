Certainly!  
The goal is to move all asynchronous logic and complex entity state modifications from the controller endpoints into the workflow function `processOrder`, which is invoked asynchronously before persistence. This will clean up the controller and centralize the business logic related to entity processing.

---

### Approach & key points:

- The workflow function `processOrder` takes an `ObjectNode` representing the entity data, modifies it directly, and can asynchronously invoke other entityService methods for *different* entityModels.

- We **cannot** do `addItem/updateItem/deleteItem` on the same entityModel inside the workflow function (to avoid infinite recursion).

- Any asynchronous "fire and forget" post-persistence tasks currently done with `CompletableFuture.runAsync(...)` should be moved into this workflow function as part of the pre-persist asynchronous processing.

- The controller endpoints become simple: validate request, convert to ObjectNode, call `entityService.addItem` with workflow, and return the result.

---

### What to move into `processOrder`:

- **Order "rejected" status handling** (currently done in controller)

- **Order "pending" status setting**

- **Order "executed" status update and updating executedAmountsByPair**

- The current `executeOrderWorkflow` async method (which updates status to "executed" after some delay) should be moved into `processOrder`.

---

### Implementation details:

- Since `processOrder` gets the entity as an `ObjectNode`, we will work directly with it:

  - e.g. `entity.put("status", "pending");`

- To do asynchronous delays / logic, just chain CompletableFutures inside `processOrder`.

- To update other entities, call `entityService.addItem` or `entityService.getItem` for *different* entityModels if needed.

- To update the `executedAmountsByPair`, since it is a local map, and `processOrder` happens before persistence, we can update it inside the workflow function.

---

### Updated complete code:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final String ENTITY_NAME = "order";

    private final EntityService entityService;

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = entityService.getObjectMapper();
    }

    // To track executed amounts by pair (thread-safe with synchronization)
    private final Map<String, Double> executedAmountsByPair = Collections.synchronizedMap(new HashMap<>());

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    public static class OrderRequest {
        @NotNull
        private Double price;

        @NotNull
        private String side;

        @NotNull
        private Double amount;

        @NotNull
        private String pair;
    }

    @Data
    public static class OrderResponse {
        private UUID orderId;
        private String status;
        private String message;

        public OrderResponse(UUID orderId, String status, String message) {
            this.orderId = orderId;
            this.status = status;
            this.message = message;
        }
    }

    @Data
    public static class OrderStatusResponse {
        private UUID orderId;
        private Double price;
        private String side;
        private Double amount;
        private String pair;
        private String status;
        private Instant createdAt;

        public OrderStatusResponse(UUID orderId, Double price, String side, Double amount, String pair, String status, Instant createdAt) {
            this.orderId = orderId;
            this.price = price;
            this.side = side;
            this.amount = amount;
            this.pair = pair;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    @Data
    public static class ExecutionSummary {
        private String pair;
        private Double totalAmount;

        public ExecutionSummary(String pair, Double totalAmount) {
            this.pair = pair;
            this.totalAmount = totalAmount;
        }
    }

    @Data
    public static class ExecutionReportResponse {
        private Instant reportGeneratedAt;
        private List<ExecutionSummary> executions;

        public ExecutionReportResponse(Instant reportGeneratedAt, List<ExecutionSummary> executions) {
            this.reportGeneratedAt = reportGeneratedAt;
            this.executions = executions;
        }
    }

    /**
     * Workflow function applied asynchronously before persisting the entity.
     * The entity is an ObjectNode, modify it directly.
     * You can get/add entities of different entityModels via entityService.
     * You cannot add/update/delete entity of the same entityModel here.
     */
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processOrder = entity -> {
        logger.info("processOrder workflow started for entity: {}", entity);

        // Set createdAt if missing
        if (!entity.has("createdAt")) {
            entity.put("createdAt", Instant.now().toString());
        }

        // Validate & normalize side field
        if (entity.has("side")) {
            String sideStr = entity.get("side").asText().toLowerCase(Locale.ROOT);
            if (!sideStr.equals("buy") && !sideStr.equals("sell")) {
                // invalid side, reject order by setting status immediately
                entity.put("status", "rejected");
                entity.put("rejectionReason", "Invalid side value");
                logger.warn("Order rejected due to invalid side: {}", sideStr);
                return CompletableFuture.completedFuture(entity);
            }
            entity.put("side", sideStr);
        } else {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Missing side");
            logger.warn("Order rejected due to missing side");
            return CompletableFuture.completedFuture(entity);
        }

        // Set default status if missing
        if (!entity.has("status")) {
            entity.put("status", "pending");
        }

        // Check amount and decide immediate rejection or pending
        double amount = entity.has("amount") ? entity.get("amount").asDouble() : 0.0;

        if (amount >= 10_000) {
            // Immediately reject large orders
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Amount exceeds limit");
            logger.info("Order rejected due to amount >= 10,000: {}", amount);
            return CompletableFuture.completedFuture(entity);
        }

        // For amounts between 5,000 and 10,000, mark as "review"
        if (amount >= 5_000 && amount < 10_000) {
            entity.put("status", "review");
            logger.info("Order marked for review due to amount: {}", amount);
        }

        // Simulate async execution workflow for orders with status "pending" or "review"
        if (entity.get("status").asText().equals("pending") || entity.get("status").asText().equals("review")) {
            // Return a CompletableFuture that completes after simulating execution delay and updating status
            return CompletableFuture.supplyAsync(() -> {
                try {
                    logger.info("Simulating async execution workflow, sleeping 100ms");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted during execution simulation", e);
                }

                // Update status to "executed"
                entity.put("status", "executed");

                // Update executedAmountsByPair map
                if (entity.has("pair")) {
                    String pair = entity.get("pair").asText();
                    synchronized (executedAmountsByPair) {
                        double oldVal = executedAmountsByPair.getOrDefault(pair, 0.0);
                        executedAmountsByPair.put(pair, oldVal + amount);
                    }
                }

                logger.info("Order status updated to executed and executedAmountsByPair updated");

                // Return the modified entity to be persisted
                return entity;
            });
        }

        // For all other cases, just return the entity immediately
        return CompletableFuture.completedFuture(entity);
    };

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest request) {
        logger.info("Received new order request: {}", request);

        // Convert OrderRequest to ObjectNode
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("price", request.getPrice());
        entityNode.put("side", request.getSide());
        entityNode.put("amount", request.getAmount());
        entityNode.put("pair", request.getPair());

        // createdAt will be set in workflow function if missing

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode,
                processOrder
        );

        UUID techId = idFuture.join();

        String status = entityNode.has("status") ? entityNode.get("status").asText() : "unknown";
        String message;
        switch (status) {
            case "rejected":
                message = entityNode.has("rejectionReason") ? entityNode.get("rejectionReason").asText() : "Rejected";
                break;
            case "review":
                message = "Order under review";
                break;
            case "executed":
                message = "Order executed";
                break;
            case "pending":
                message = "Order pending";
                break;
            default:
                message = "Status unknown";
        }

        return ResponseEntity.ok(new OrderResponse(techId, status, message));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable @NotNull UUID orderId) {
        logger.info("Fetching order status for technicalId={}", orderId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, orderId);
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
        }

        try {
            // Map ObjectNode to response fields
            OrderStatusResponse response = new OrderStatusResponse(
                    orderId,
                    node.has("price") ? node.get("price").asDouble() : null,
                    node.has("side") ? node.get("side").asText() : null,
                    node.has("amount") ? node.get("amount").asDouble() : null,
                    node.has("pair") ? node.get("pair").asText() : null,
                    node.has("status") ? node.get("status").asText() : null,
                    node.has("createdAt") ? Instant.parse(node.get("createdAt").asText()) : null
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse order data");
        }
    }

    @GetMapping("/reports/executions")
    public ResponseEntity<ExecutionReportResponse> getExecutionReport() {
        logger.info("Generating execution report");
        List<ExecutionSummary> summaries = new ArrayList<>();
        synchronized (executedAmountsByPair) {
            executedAmountsByPair.forEach((pair, total) -> summaries.add(new ExecutionSummary(pair, total)));
        }
        ExecutionReportResponse report = new ExecutionReportResponse(Instant.now(), summaries);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```

---

### Summary of what changed:

- The controller `createOrder` simply converts the request to `ObjectNode` and calls `entityService.addItem` with `processOrder` workflow.

- The `processOrder` workflow function:

  - Sets/validates `createdAt`, `side`, and `status`.

  - Immediately rejects orders with invalid side or amount >= 10,000.

  - Marks orders with amount between 5,000 and 10,000 as "review".

  - For "pending" or "review" orders, simulates async execution by sleeping 100ms, then marks status to "executed" and updates the executedAmountsByPair map.

- Removed all async `CompletableFuture.runAsync` or manual post-persist workflows from the controller.

- The map `executedAmountsByPair` updates happen inside the workflow function, ensuring state is consistent and logic is centralized.

---

### Benefits:

- Controller is slim, only responsible for validation and calling service.

- All business logic, validation, async workflows, state updates happen inside `processOrder` function.

- The workflow function runs asynchronously before persistence, integrating validation, processing, and side-effects in one place.

- No infinite recursion risk because we do not update the same entityModel inside the workflow, only modify the entity state directly.

---

If you want, I can also help to move other logic or entities into workflow functions similarly.  
Let me know if you want me to include additional comments or tests!