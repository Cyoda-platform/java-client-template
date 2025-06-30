package com.java_template.entity.WeatherRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class WeatherRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(WeatherRequestWorkflow.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> validateFields(ObjectNode entity) {
        try {
            if (!entity.hasNonNull("latitude") || !entity.hasNonNull("longitude"))
                throw new IllegalArgumentException("Entity missing latitude or longitude");
            if (!entity.hasNonNull("startDate") || !entity.hasNonNull("endDate"))
                throw new IllegalArgumentException("Entity missing startDate or endDate");
            if (!entity.hasNonNull("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0)
                throw new IllegalArgumentException("Entity missing parameters or parameters array is empty");
            entity.put("validationPassed", true);
        } catch (Exception e) {
            logger.error("Validation failed", e);
            entity.put("validationPassed", false);
            entity.put("validationError", e.getMessage());
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchAndSetWeatherData(ObjectNode entity) {
        // Original fetch logic or placeholder; to be implemented or called elsewhere
        entity.put("fetchSuccess", true); // set success flag for demo
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setFailedStatus(ObjectNode entity) {
        entity.put("status", "failed");
        entity.put("fetchedAt", Instant.now().toString());
        entity.putNull("data");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> checkWeatherAlerts(ObjectNode entity) {
        // Template: Fetch active WeatherAlert entities and evaluate conditions
        // For demonstration, pretend we checked and found alerts triggered
        boolean alertsTriggered = false;

        // TODO: Implement actual logic to fetch alerts and evaluate conditions against entity data

        entity.put("alertsTriggered", alertsTriggered);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notifyUsers(ObjectNode entity) {
        // Template: Send notifications for triggered alerts
        // TODO: Implement notification dispatch logic based on alert config and user preferences

        entity.put("notificationsSent", true);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setCompletedStatus(ObjectNode entity) {
        entity.put("status", "completed");
        entity.put("fetchedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }
}