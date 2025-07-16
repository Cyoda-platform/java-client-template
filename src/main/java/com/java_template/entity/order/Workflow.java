package com.java_template.entity.order;

import static com.java_template.common.config.Config.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component("order")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> setDefaultStatusIfMissing(ObjectNode entity) {
        logger.info("Executing setDefaultStatusIfMissing for customerId={}", entity.path("customerId").asText());
        String status = entity.path("status").asText(null);
        if (status == null || status.isEmpty()) {
            entity.put("status", "CREATED");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isPaymentValid(ObjectNode entity) {
        logger.info("Validating payment for customerId={}", entity.path("customerId").asText());
        String paymentMethod = entity.path("paymentMethod").asText();
        String paymentDetails = entity.path("paymentDetails").asText();
        return validatePaymentAsync(paymentMethod, paymentDetails)
                .thenApply(paymentOk -> {
                    if (!paymentOk) {
                        entity.put("paymentValid", false);
                        logger.error("Payment validation failed for customerId={}", entity.path("customerId").asText());
                    } else {
                        entity.put("paymentValid", true);
                        logger.info("Payment validated successfully for customerId={}", entity.path("customerId").asText());
                    }
                    return entity;
                });
    }

    public CompletableFuture<ObjectNode> isPaymentInvalid(ObjectNode entity) {
        // Inverse of isPaymentValid condition
        logger.info("Checking payment invalid status for customerId={}", entity.path("customerId").asText());
        return isPaymentValid(entity)
                .thenApply(enrichedEntity -> {
                    boolean valid = enrichedEntity.path("paymentValid").asBoolean(false);
                    return valid ? null : enrichedEntity; // return null to indicate condition false if valid=true
                });
    }

    public CompletableFuture<ObjectNode> setPaymentValidatedStatus(ObjectNode entity) {
        logger.info("Setting status to PAYMENT_VALIDATED for customerId={}", entity.path("customerId").asText());
        entity.put("status", "PAYMENT_VALIDATED");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> sendNotification(ObjectNode entity) {
        logger.info("Sending notification for customerId={}", entity.path("customerId").asText());
        CompletableFuture.runAsync(() -> sendOrderNotification(entity))
                .exceptionally(ex -> {
                    logger.error("Failed to send order notification for customerId={}", entity.path("customerId").asText(), ex);
                    return null;
                });
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setEstimatedDeliveryTime(ObjectNode entity) {
        logger.info("Setting estimated delivery time for customerId={}", entity.path("customerId").asText());
        if (entity.path("estimatedDeliveryTime").isMissingNode()) {
            Instant eta = Instant.now().plusSeconds(30 * 60);
            entity.put("estimatedDeliveryTime", eta.toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isReadyForDelivery(ObjectNode entity) {
        logger.info("Checking if ready for delivery for customerId={}", entity.path("customerId").asText());
        // Example condition: status must be PAYMENT_VALIDATED or NOTIFIED and current time past estimatedDeliveryTime
        String status = entity.path("status").asText("");
        if (!"NOTIFIED".equals(status) && !"READY_FOR_DELIVERY".equals(status)) {
            return CompletableFuture.completedFuture(null);
        }
        String etaString = entity.path("estimatedDeliveryTime").asText(null);
        if (etaString == null) {
            return CompletableFuture.completedFuture(null);
        }
        Instant eta = Instant.parse(etaString);
        boolean ready = Instant.now().isAfter(eta);
        if (ready) {
            entity.put("readyForDelivery", true);
            return CompletableFuture.completedFuture(entity);
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<ObjectNode> markAsDelivered(ObjectNode entity) {
        logger.info("Marking order as DELIVERED for customerId={}", entity.path("customerId").asText());
        entity.put("status", "DELIVERED");
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<Boolean> validatePaymentAsync(String method, String details) {
        logger.info("Mock payment validation: method={}, details={}", method, details);
        // TODO: Replace with real payment gateway integration
        return CompletableFuture.completedFuture(true);
    }

    private void sendOrderNotification(ObjectNode entity) {
        logger.info("Mock sending order notification for customerId={}", entity.path("customerId").asText());
        // TODO: Replace with real notification logic
    }
}