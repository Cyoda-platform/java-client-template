package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping("/orders")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, String> workflowStatuses = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("Received create order request for customerId={}", request.getCustomerId());
        String orderId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Order order = new Order(orderId, request.getCustomerId(), request.getItems(), request.getOrderDate(), "CREATED");
        orders.put(orderId, order);
        workflowStatuses.put(orderId, "processing");
        triggerWorkflowAsync(order);
        log.info("Order created with orderId={}, status=CREATED", orderId);
        return new CreateOrUpdateOrderResponse(orderId, order.getStatus(), "processing");
    }

    @PostMapping(path = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse updateOrder(@PathVariable @NotBlank String orderId,
                                                   @Valid @RequestBody UpdateOrderRequest request) {
        log.info("Received update order request for orderId={}", orderId);
        Order existingOrder = orders.get(orderId);
        if (existingOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            existingOrder.setItems(request.getItems());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existingOrder.setStatus(request.getStatus());
        }
        orders.put(orderId, existingOrder);
        workflowStatuses.put(orderId, "processing");
        triggerWorkflowAsync(existingOrder);
        log.info("Order updated with orderId={}, status={}", orderId, existingOrder.getStatus());
        return new CreateOrUpdateOrderResponse(orderId, existingOrder.getStatus(), "processing");
    }

    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Order getOrder(@PathVariable @NotBlank String orderId) {
        log.info("Received get order request for orderId={}", orderId);
        Order order = orders.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        order.setWorkflowStatus(workflowStatuses.getOrDefault(orderId, "unknown"));
        return order;
    }

    @GetMapping(path = "/customer/{customerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderSummary> listOrdersByCustomer(@PathVariable @NotBlank String customerId) {
        log.info("Received list orders request for customerId={}", customerId);
        List<OrderSummary> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getCustomerId().equals(customerId)) {
                result.add(new OrderSummary(order.getOrderId(), order.getOrderDate(), order.getStatus()));
            }
        }
        return result;
    }

    @Async
    void triggerWorkflowAsync(Order order) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Workflow started for orderId={}", order.getOrderId());
                Thread.sleep(200);
                for (OrderItem item : order.getItems()) {
                    JsonNode productData = fetchProductData(item.getProductId());
                    log.info("Fetched product data for productId={}: {}", item.getProductId(), productData);
                }
                Thread.sleep(300);
                workflowStatuses.put(order.getOrderId(), "completed");
                log.info("Workflow completed for orderId={}", order.getOrderId());
            } catch (Exception e) {
                workflowStatuses.put(order.getOrderId(), "failed");
                log.error("Workflow failed for orderId=" + order.getOrderId(), e);
            }
        });
    }

    private JsonNode fetchProductData(String productId) {
        try {
            String url = "https://fakestoreapi.com/products/" + productId;
            var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
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

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of("error", ex.getReason(), "status", String.valueOf(ex.getStatusCode().value()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        private String orderId;
        private String customerId;
        private List<OrderItem> items;
        private String orderDate;
        private String status;
        private String workflowStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        @NotBlank
        private String productId;
        @NotNull
        @Min(1)
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        @NotBlank
        private String customerId;
        @NotNull
        @NotEmpty
        private List<@Valid OrderItem> items;
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z$", message = "must be ISO8601 date-time")
        private String orderDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateOrderRequest {
        @NotNull
        @NotEmpty
        private List<@Valid OrderItem> items;
        @NotBlank
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