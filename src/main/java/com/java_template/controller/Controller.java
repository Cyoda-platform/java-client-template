package com.java_template.entity;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

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

    // Helper method to fetch product data from external API
    private JsonNode fetchProductData(String productId) throws Exception {
        String url = "https://fakestoreapi.com/products/" + productId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return objectMapper.readTree(response.body());
        } else {
            logger.warn("External API returned status {} for productId={}", response.statusCode(), productId);
            return objectMapper.createObjectNode().put("error", "Failed to fetch product data");
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) throws Exception {
        logger.info("Received create order request for customerId={}", request.getCustomerId());

        // Build initial Order ObjectNode
        ObjectNode orderNode = objectMapper.createObjectNode();
        orderNode.putNull("technicalId"); // will be assigned by backend
        orderNode.put("customerId", request.getCustomerId());
        orderNode.set("items", objectMapper.valueToTree(request.getItems()));
        orderNode.put("orderDate", request.getOrderDate());
        orderNode.put("status", "CREATED");
        // workflowStatus will be set by workflow function

        // Add entity without workflow argument
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "order",
                ENTITY_VERSION,
                orderNode
        );
        UUID technicalId = idFuture.get();

        // Return response with assigned ID and initial status/workflowStatus
        return new CreateOrUpdateOrderResponse(technicalId.toString(), "CREATED", "processing");
    }

    @PostMapping(path = "/{orderId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateOrUpdateOrderResponse updateOrder(@PathVariable @NotBlank String orderId,
                                                   @Valid @RequestBody UpdateOrderRequest request) throws Exception {
        logger.info("Received update order request for orderId={}", orderId);

        UUID id = UUID.fromString(orderId);

        // Fetch existing order as ObjectNode
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("order", ENTITY_VERSION, id);
        ObjectNode existingOrder = itemFuture.get();

        if (existingOrder == null || existingOrder.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
        }

        // Update fields if present in the request
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            existingOrder.set("items", objectMapper.valueToTree(request.getItems()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            existingOrder.put("status", request.getStatus());
        }
        // workflowStatus will be set inside workflow function

        // Update entity in storage without workflow argument
        CompletableFuture<UUID> updateFuture = entityService.updateItem(
                "order",
                ENTITY_VERSION,
                id,
                existingOrder
        );
        updateFuture.get();

        return new CreateOrUpdateOrderResponse(orderId,
                existingOrder.has("status") ? existingOrder.get("status").asText() : "UNKNOWN",
                existingOrder.has("workflowStatus") ? existingOrder.get("workflowStatus").asText() : "processing");
    }

    @GetMapping(path = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode getOrder(@PathVariable @NotBlank String orderId) throws Exception {
        logger.info("Received get order request for orderId={}", orderId);

        UUID id = UUID.fromString(orderId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("order", ENTITY_VERSION, id);
        ObjectNode order = itemFuture.get();

        if (order == null || order.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
        }

        if (!order.has("workflowStatus")) {
            order.put("workflowStatus", "unknown");
        }
        return order;
    }

    @GetMapping(path = "/customer/{customerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArrayNode listOrdersByCustomer(@PathVariable @NotBlank String customerId) throws Exception {
        logger.info("Received list orders request for customerId={}", customerId);

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                "order",
                ENTITY_VERSION,
                objectMapper.createObjectNode().put("customerId", customerId)
        );
        ArrayNode itemsNode = itemsFuture.get();

        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode node : itemsNode) {
            if (node.has("customerId") && customerId.equals(node.get("customerId").asText()) && node.isObject()) {
                ObjectNode summary = objectMapper.createObjectNode();
                if (node.has("technicalId")) summary.put("orderId", node.get("technicalId").asText());
                if (node.has("orderDate")) summary.put("orderDate", node.get("orderDate").asText());
                if (node.has("status")) summary.put("status", node.get("status").asText());
                result.add(summary);
            }
        }
        return result;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason());
        return Map.of("error", ex.getReason(), "status", String.valueOf(ex.getStatusCode().value()));
    }

    // DTO classes

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
        private List<@Valid OrderItem> items; // optional, may be null
        private String status; // optional, may be null
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
    public static class OrderItem {
        @NotBlank
        private String productId;
        @NotNull
        @Min(1)
        private Integer quantity;
    }
}