package com.java_template.application.controller;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ui/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping
    public CompletableFuture<ResponseEntity<List<Map<String, Object>>>> getOrders(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {

        logger.info("Getting orders with page={}, pageSize={}", page, pageSize);

        try {
            return entityService.getItems(
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                pageSize,
                page,
                null
            ).thenApply(dataPayloads -> {
                List<Map<String, Object>> orderSummaries = dataPayloads.stream()
                    .map(payload -> {
                        try {
                            Order order = objectMapper.convertValue(payload.getData(), Order.class);
                            String state = payload.getMetadata().getState();
                            UUID entityId = payload.getMetadata().getId();

                            return createOrderSummary(order, state, entityId);
                        } catch (Exception e) {
                            logger.error("Error converting order data: {}", e.getMessage(), e);
                            return null;
                        }
                    })
                    .filter(summary -> summary != null)
                    .collect(Collectors.toList());

                return ResponseEntity.ok(orderSummaries);
            }).exceptionally(throwable -> {
                logger.error("Error getting orders: {}", throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getOrders: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("/{orderId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getOrder(@PathVariable String orderId) {
        logger.info("Getting order: {}", orderId);

        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);

            return entityService.getFirstItemByCondition(
                Order.ENTITY_NAME,
                Order.ENTITY_VERSION,
                condition,
                true
            ).thenApply(optionalPayload -> {
                if (optionalPayload.isPresent()) {
                    try {
                        Order order = objectMapper.convertValue(optionalPayload.get().getData(), Order.class);
                        String state = optionalPayload.get().getMetadata().getState();
                        UUID entityId = optionalPayload.get().getMetadata().getId();

                        Map<String, Object> response = createOrderResponse(order, state, entityId);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        logger.error("Error converting order data: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError().<Map<String, Object>>build();
                    }
                } else {
                    return ResponseEntity.notFound().<Map<String, Object>>build();
                }
            }).exceptionally(throwable -> {
                logger.error("Error getting order {}: {}", orderId, throwable.getMessage(), throwable);
                return ResponseEntity.internalServerError().build();
            });

        } catch (Exception e) {
            logger.error("Error in getOrder: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PostMapping("/{orderId}/ready-to-send")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> markOrderReadyToSend(
            @PathVariable String orderId) {

        logger.info("Marking order {} as ready to send", orderId);

        try {
            return getOrderEntityId(orderId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                return entityService.applyTransition(entityId, "READY_TO_SEND")
                    .thenApply(result -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("orderId", orderId);
                        response.put("state", "READY_TO_SEND");
                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error marking order ready to send: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in markOrderReadyToSend: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PostMapping("/{orderId}/mark-sent")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> markOrderSent(
            @PathVariable String orderId) {

        logger.info("Marking order {} as sent", orderId);

        try {
            return getOrderEntityId(orderId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                return entityService.applyTransition(entityId, "MARK_SENT")
                    .thenApply(result -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("orderId", orderId);
                        response.put("state", "SENT");
                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error marking order sent: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in markOrderSent: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    @PostMapping("/{orderId}/mark-delivered")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> markOrderDelivered(
            @PathVariable String orderId) {

        logger.info("Marking order {} as delivered", orderId);

        try {
            return getOrderEntityId(orderId).thenCompose(entityId -> {
                if (entityId == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().<Map<String, Object>>build());
                }

                return entityService.applyTransition(entityId, "MARK_DELIVERED")
                    .thenApply(result -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("orderId", orderId);
                        response.put("state", "DELIVERED");
                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error marking order delivered: {}", throwable.getMessage(), throwable);
                        return ResponseEntity.internalServerError().build();
                    });
            });

        } catch (Exception e) {
            logger.error("Error in markOrderDelivered: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    private CompletableFuture<UUID> getOrderEntityId(String orderId) {
        Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);

        return entityService.getFirstItemByCondition(
            Order.ENTITY_NAME,
            Order.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                return optionalPayload.get().getMetadata().getId();
            }
            return null;
        });
    }

    private Map<String, Object> createOrderSummary(Order order, String state, UUID entityId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("orderId", order.getOrderId());
        summary.put("orderNumber", order.getOrderNumber());
        summary.put("state", state);
        summary.put("entityId", entityId.toString());

        if (order.getTotals() != null) {
            summary.put("totalItems", order.getTotals().getItems());
            summary.put("grandTotal", order.getTotals().getGrand());
        }

        if (order.getGuestContact() != null) {
            summary.put("customerName", order.getGuestContact().getName());
        }

        summary.put("createdAt", order.getCreatedAt());
        summary.put("updatedAt", order.getUpdatedAt());

        return summary;
    }

    private Map<String, Object> createOrderResponse(Order order, String state, UUID entityId) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("lines", order.getLines());
        response.put("totals", order.getTotals());
        response.put("guestContact", order.getGuestContact());
        response.put("state", state);
        response.put("entityId", entityId.toString());
        response.put("createdAt", order.getCreatedAt());
        response.put("updatedAt", order.getUpdatedAt());

        return response;
    }
}