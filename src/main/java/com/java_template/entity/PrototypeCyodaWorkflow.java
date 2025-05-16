package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
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

    // Thread-safe map to track executed amounts by pair
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

    // Workflow function to process the Order entity asynchronously before persistence
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processOrder = entity -> {
        logger.info("processOrder workflow started for entity: {}", entity);

        // Set createdAt if missing
        if (!entity.has("createdAt")) {
            entity.put("createdAt", Instant.now().toString());
        }

        // Validate and normalize side field
        if (entity.has("side")) {
            String sideStr = entity.get("side").asText().toLowerCase(Locale.ROOT);
            if (!sideStr.equals("buy") && !sideStr.equals("sell")) {
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

        // Validate amount
        double amount = entity.has("amount") ? entity.get("amount").asDouble() : 0.0;

        if (amount < 0) {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Negative amount not allowed");
            logger.warn("Order rejected due to negative amount: {}", amount);
            return CompletableFuture.completedFuture(entity);
        }

        // Immediately reject orders with amount >= 10,000
        if (amount >= 10_000) {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Amount exceeds limit");
            logger.info("Order rejected due to amount >= 10,000: {}", amount);
            return CompletableFuture.completedFuture(entity);
        }

        // For amounts between 5,000 and 10,000 mark as review
        if (amount >= 5_000 && amount < 10_000) {
            entity.put("status", "review");
            logger.info("Order marked for review due to amount: {}", amount);
        }

        // For pending or review status, simulate async execution workflow
        String status = entity.get("status").asText();
        if ("pending".equals(status) || "review".equals(status)) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    logger.info("Simulating async execution workflow, sleeping 100ms");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted during execution simulation", e);
                }

                // Update status to executed
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

                return entity;
            });
        }

        // For all other cases, return entity immediately
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

        // Add item with workflow function
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode,
                processOrder
        );

        UUID techId = idFuture.join();

        // Determine status and message for response
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