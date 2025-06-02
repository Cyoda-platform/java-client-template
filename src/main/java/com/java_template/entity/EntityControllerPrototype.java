```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/orders")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // In-memory store for orders: orderId -> Order
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    // In-memory store for workflows status: orderId -> WorkflowStatus
    private final Map<String, String> workflowStatuses = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * Create a new order and trigger workflow asynchronously.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        log.info("Received create order request for customerId={}", request.getCustomerId());

        // Basic validation (could be extended)
        if (request.getCustomerId() == null || request.getCustomerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order items cannot be empty");
        }
        String orderId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();

        Order order = new Order(orderId, request.getCustomerId(), request.getItems(), request.getOrderDate(), "CREATED");
        orders.put(orderId, order);
        workflowStatuses.put(orderId, "processing");

        // Fire and forget workflow trigger
        triggerWorkflowAsync(order);

        log.info("Order created with orderId={}, status=CREATED", orderId);

        return new CreateOrUpdateOrderResponse(orderId, order.getStatus(), "processing");
    }

    /**
     * Update existing order and trigger workflow asynchronously.
     */
    @PostMapping(path = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse updateOrder(@PathVariable String orderId, @RequestBody UpdateOrderRequest request) {
        log.info("Received update order request for orderId={}", orderId);

        Order existingOrder = orders.get(orderId);
        if (existingOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        // Update fields if present
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            existingOrder.setItems(request.getItems());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existingOrder.setStatus(request.getStatus());
        }

        orders.put(orderId, existingOrder);
        workflowStatuses.put(orderId, "processing");

        // Fire and forget workflow trigger
        triggerWorkflowAsync(existingOrder);

        log.info("Order updated with orderId={}, status={}", orderId, existingOrder.getStatus());

        return new CreateOrUpdateOrderResponse(orderId, existingOrder.getStatus(), "processing");
    }

    /**
     * Retrieve order details (read-only, no external calls).
     */
    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Order getOrder(@PathVariable String orderId) {
        log.info("Received get order request for orderId={}", orderId);

        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        String wfStatus = workflowStatuses.getOrDefault(orderId, "unknown");
        order.setWorkflowStatus(wfStatus);
        return order;
    }

    /**
     * List orders by customer (read-only).
     */
    @GetMapping(path = "/customer/{customerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderSummary> listOrdersByCustomer(@PathVariable String customerId) {
        log.info("Received list orders request for customerId={}", customerId);

        List<OrderSummary> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getCustomerId().equals(customerId)) {
                result.add(new OrderSummary(order.getOrderId(), order.getOrderDate(), order.getStatus()));
            }
        }
        return result;
    }

    /**
     * Asynchronous workflow simulation: validates, calls external API, updates workflow status.
     */
    @Async
    void triggerWorkflowAsync(Order order) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Workflow started for orderId={}", order.getOrderId());

                // 1. Simulate validation (could add real logic)
                Thread.sleep(200); // simulate delay

                // 2. External API call example: fetch product info for all items
                for (OrderItem item : order.getItems()) {
                    JsonNode productData = fetchProductData(item.getProductId());
                    // TODO: Use productData for validation/enrichment/calculation as needed
                    log.info("Fetched product data from external API for productId={}: {}", item.getProductId(), productData.toString());
                }

                // 3. Simulate calculation or enrichment
                Thread.sleep(300); // simulate delay

                // 4. Mark workflow complete
                workflowStatuses.put(order.getOrderId(), "completed");
                log.info("Workflow completed for orderId={}", order.getOrderId());

            } catch (Exception e) {
                workflowStatuses.put(order.getOrderId(), "failed");
                log.error("Workflow failed for orderId=" + order.getOrderId(), e);
            }
        });
        // TODO: Consider proper exception handling and retry logic in real implementation
    }

    /**
     * Mock external call to fetch product info.
     * Replace URI and logic with real external API.
     */
    private JsonNode fetchProductData(String productId) {
        try {
            // TODO: Replace with real external product info API endpoint
            String url = "https://fakestoreapi.com/products/" + productId;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.warn("External API returned non-200 status: {}", response.statusCode());
                return objectMapper.createObjectNode().put("error", "Failed to fetch product data");
            }
        } catch (Exception e) {
            log.error("Error fetching product data for productId=" + productId, e);
            return objectMapper.createObjectNode().put("error", "Exception during fetch");
        }
    }

    /**
     * Minimal error handler for ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "error", ex.getReason(),
                "status", String.valueOf(ex.getStatusCode().value())
        );
    }

    // --- DTO and Entity classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private String orderId;
        private String customerId;
        private List<OrderItem> items;
        private String orderDate; // ISO8601 date-time string
        private String status;

        // Transient field for workflow status
        private String workflowStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        private String customerId;
        private List<OrderItem> items;
        private String orderDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateOrderRequest {
        private List<OrderItem> items;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrUpdateOrderResponse {
        private String orderId;
        private String status;
        private String workflowStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private String orderId;
        private String orderDate;
        private String status;
    }
}
```
