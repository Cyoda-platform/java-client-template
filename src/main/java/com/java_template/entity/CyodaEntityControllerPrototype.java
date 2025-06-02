package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.java_template.common.config.Config.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-orders")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received create order request for customerId={}", request.getCustomerId());

        Order order = new Order(null, request.getCustomerId(), request.getItems(), request.getOrderDate(), "CREATED", "processing");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "order",
                ENTITY_VERSION,
                order
        );
        UUID technicalId = idFuture.get();
        String orderId = technicalId.toString();
        order.setTechnicalId(technicalId);
        order.setWorkflowStatus("processing");

        triggerWorkflowAsync(order);

        logger.info("Order created with technicalId={}, status=CREATED", orderId);

        return new CreateOrUpdateOrderResponse(orderId, order.getStatus(), "processing");
    }

    @PostMapping(path = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse updateOrder(@PathVariable @NotBlank String orderId,
                                                   @Valid @RequestBody UpdateOrderRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received update order request for orderId={}", orderId);

        UUID id = UUID.fromString(orderId);

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("order", ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
        }
        Order existingOrder = objectMapper.convertValue(itemNode, Order.class);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            existingOrder.setItems(request.getItems());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existingOrder.setStatus(request.getStatus());
        }
        existingOrder.setWorkflowStatus("processing");

        CompletableFuture<UUID> updateFuture = entityService.updateItem(
                "order",
                ENTITY_VERSION,
                id,
                existingOrder
        );
        updateFuture.get();

        triggerWorkflowAsync(existingOrder);

        logger.info("Order updated with technicalId={}, status={}", orderId, existingOrder.getStatus());

        return new CreateOrUpdateOrderResponse(orderId, existingOrder.getStatus(), "processing");
    }

    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Order getOrder(@PathVariable @NotBlank String orderId) throws ExecutionException, InterruptedException {
        logger.info("Received get order request for orderId={}", orderId);

        UUID id = UUID.fromString(orderId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("order", ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
        }
        Order order = objectMapper.convertValue(itemNode, Order.class);
        if (order.getWorkflowStatus() == null) {
            order.setWorkflowStatus("unknown");
        }
        return order;
    }

    @GetMapping(path = "/customer/{customerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OrderSummary> listOrdersByCustomer(@PathVariable @NotBlank String customerId) throws ExecutionException, InterruptedException {
        logger.info("Received list orders request for customerId={}", customerId);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                "order",
                ENTITY_VERSION,
                objectMapper.createObjectNode().put("customerId", customerId)
        );
        ArrayNode itemsNode = itemsFuture.get();
        List<OrderSummary> result = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            Order order = objectMapper.convertValue(node, Order.class);
            if (customerId.equals(order.getCustomerId())) {
                result.add(new OrderSummary(order.getTechnicalId().toString(), order.getOrderDate(), order.getStatus()));
            }
        }
        return result;
    }

    @Async
    void triggerWorkflowAsync(Order order) {
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Workflow started for orderId={}", order.getTechnicalId());
                Thread.sleep(200);
                for (OrderItem item : order.getItems()) {
                    JsonNode productData = fetchProductData(item.getProductId());
                    logger.info("Fetched product data for productId={}: {}", item.getProductId(), productData);
                }
                Thread.sleep(300);
                order.setWorkflowStatus("completed");
                logger.info("Workflow completed for orderId={}", order.getTechnicalId());
            } catch (Exception e) {
                order.setWorkflowStatus("failed");
                logger.error("Workflow failed for orderId=" + order.getTechnicalId(), e);
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
                logger.warn("External API returned non-200 status: {}", response.statusCode());
                return objectMapper.createObjectNode().put("error", "Failed to fetch product data");
            }
        } catch (Exception e) {
            logger.error("Error fetching product data for productId=" + productId, e);
            return objectMapper.createObjectNode().put("error", "Exception during fetch");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of("error", ex.getReason(), "status", String.valueOf(ex.getStatusCode().value()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Order {
        @JsonIgnore
        private UUID technicalId;
        private String customerId;
        private List<OrderItem> items;
        private String orderDate;
        private String status;
        @JsonIgnore
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