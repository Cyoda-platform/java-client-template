package com.java_template.entity.Order;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("Order")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<ObjectNode> validateOrder(ObjectNode entity) {
        logger.info("Validating order id: {}", entity.path("id").asText("unknown"));
        boolean valid = true;

        if (!entity.hasNonNull("petId") || entity.get("petId").asInt(0) <= 0) {
            logger.error("Validation failed: petId is missing or invalid");
            valid = false;
        }
        if (!entity.hasNonNull("quantity") || entity.get("quantity").asInt(0) <= 0) {
            logger.error("Validation failed: quantity is missing or invalid");
            valid = false;
        }
        if (!entity.hasNonNull("status")) {
            entity.put("status", "placed");
        }
        entity.put("validationSuccess", valid);
        logger.info("Validation success: {}", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> onValidationFailure(ObjectNode entity) {
        logger.info("Order validation failed for id: {}", entity.path("id").asText("unknown"));
        entity.put("status", "validation_failed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> onValidationSuccess(ObjectNode entity) {
        logger.info("Order validation succeeded for id: {}", entity.path("id").asText("unknown"));
        entity.put("status", "validated");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isValidationFailed(ObjectNode entity) {
        boolean failed = !entity.path("validationSuccess").asBoolean(false);
        entity.put("success", !failed);
        logger.info("isValidationFailed check: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isValidationSuccessful(ObjectNode entity) {
        boolean success = entity.path("validationSuccess").asBoolean(false);
        entity.put("success", success);
        logger.info("isValidationSuccessful check: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processPayment(ObjectNode entity) {
        logger.info("Processing payment for order id: {}", entity.path("id").asText("unknown"));
        // Dummy payment simulation: success if quantity < 10, else fail
        int quantity = entity.path("quantity").asInt(1);
        boolean paymentSuccess = quantity < 10;
        entity.put("paymentSuccess", paymentSuccess);
        if (paymentSuccess) {
            entity.put("status", "payment_processed");
            logger.info("Payment processed successfully for order id: {}", entity.path("id").asText("unknown"));
        } else {
            logger.error("Payment failed for order id: {}", entity.path("id").asText("unknown"));
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isPaymentFailed(ObjectNode entity) {
        boolean failed = !entity.path("paymentSuccess").asBoolean(false);
        entity.put("success", !failed);
        logger.info("isPaymentFailed check: {}", failed);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isPaymentSuccessful(ObjectNode entity) {
        boolean success = entity.path("paymentSuccess").asBoolean(false);
        entity.put("success", success);
        logger.info("isPaymentSuccessful check: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> handlePaymentFailure(ObjectNode entity) {
        logger.error("Handling payment failure for order id: {}", entity.path("id").asText("unknown"));
        entity.put("status", "payment_failed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> updateInventory(ObjectNode entity) {
        logger.info("Updating inventory for order id: {}", entity.path("id").asText("unknown"));
        // Simulate inventory update by setting pet status to 'sold'
        if (entity.hasNonNull("petId")) {
            // In a real app, would update Pet entity status here
            logger.info("Inventory updated for petId: {}", entity.get("petId").asInt());
        }
        entity.put("inventoryUpdated", true);
        entity.put("status", "inventory_updated");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> storeOrder(ObjectNode entity) {
        logger.info("Storing order id: {}", entity.path("id").asText("unknown"));
        entity.put("stored", true);
        entity.put("status", "stored");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifyUser(ObjectNode entity) {
        logger.info("Notifying user for order id: {}", entity.path("id").asText("unknown"));
        // Simulate notification
        entity.put("notified", true);
        entity.put("status", "notified");
        return CompletableFuture.completedFuture(entity);
    }

}