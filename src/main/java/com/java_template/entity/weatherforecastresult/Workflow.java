package com.java_template.entity.weatherforecastresult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("weatherforecastresult")
@RequiredArgsConstructor
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Condition: Check if input is valid
    public CompletableFuture<ObjectNode> isValidInput(ObjectNode entity) {
        double latitude = entity.path("latitude").asDouble(Double.NaN);
        double longitude = entity.path("longitude").asDouble(Double.NaN);
        JsonNode paramsNode = entity.path("parameters");
        String startDate = entity.path("startDate").asText(null);
        String endDate = entity.path("endDate").asText(null);

        boolean valid = !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && paramsNode != null && paramsNode.isArray()
                && startDate != null && endDate != null;

        entity.put("success", valid);
        if (!valid) {
            logger.error("Invalid input detected in isValidInput: {}", entity);
        } else {
            logger.info("Input validated successfully in isValidInput.");
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Check if input is NOT valid (negation of isValidInput)
    public CompletableFuture<ObjectNode> isNotValidInput(ObjectNode entity) {
        return isValidInput(entity).thenApply(e -> {
            boolean valid = e.path("success").asBoolean(false);
            e.put("success", !valid);
            if (!valid) {
                logger.info("Input was invalid as expected in isNotValidInput.");
            } else {
                logger.error("Input unexpectedly valid in isNotValidInput.");
            }
            return e;
        });
    }

    // Action: No-op validation state, just pass-through
    public CompletableFuture<ObjectNode> validation(ObjectNode entity) {
        logger.info("Entered validation state");
        return CompletableFuture.completedFuture(entity);
    }

    // Action: Start processing the forecast - calls the external API asynchronously
    public CompletableFuture<ObjectNode> processing(ObjectNode entity) {
        logger.info("Processing started for entity: {}", entity);

        URI uri;
        try {
            uri = buildForecastUri(entity);
        } catch (URISyntaxException e) {
            logger.error("Invalid URI in processing: {}", e.getMessage(), e);
            entity.put("status", "failed");
            entity.put("timestamp", Instant.now().toString());
            return CompletableFuture.completedFuture(entity);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Calling external API from processing: {}", uri);
                String jsonResponse = restTemplate.getForObject(uri, String.class);
                if (jsonResponse == null || jsonResponse.isEmpty()) {
                    throw new IllegalStateException("Empty response from OpenMeteo");
                }
                JsonNode forecastJson = objectMapper.readTree(jsonResponse);

                entity.put("status", "completed");
                entity.put("timestamp", Instant.now().toString());
                entity.set("forecast", forecastJson);

                logger.info("Processing completed successfully for entity");
                return entity;
            } catch (Exception e) {
                logger.error("Error during async API call in processing", e);
                entity.put("status", "failed");
                entity.put("timestamp", Instant.now().toString());
                entity.remove("forecast");
                return entity;
            }
        });
    }

    // Condition: Check if fetch was successful (status == "completed")
    public CompletableFuture<ObjectNode> isFetchSuccessful(ObjectNode entity) {
        boolean success = "completed".equals(entity.path("status").asText(null));
        entity.put("success", success);
        logger.info("isFetchSuccessful returns {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: Check if fetch was NOT successful (status != "completed")
    public CompletableFuture<ObjectNode> isNotFetchSuccessful(ObjectNode entity) {
        return isFetchSuccessful(entity).thenApply(e -> {
            boolean success = e.path("success").asBoolean(false);
            e.put("success", !success);
            logger.info("isNotFetchSuccessful returns {}", !success);
            return e;
        });
    }

    // Helper: Build the forecast URI string from entity parameters
    private URI buildForecastUri(ObjectNode entity) throws URISyntaxException {
        double latitude = entity.get("latitude").asDouble();
        double longitude = entity.get("longitude").asDouble();
        JsonNode paramsNode = entity.get("parameters");
        String[] parameters = new String[paramsNode.size()];
        for (int i = 0; i < paramsNode.size(); i++) {
            parameters[i] = paramsNode.get(i).asText();
        }
        String parametersCsv = String.join(",", parameters);
        String startDate = entity.get("startDate").asText();
        String endDate = entity.get("endDate").asText();

        String uriStr = String.format("https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&start_date=%s&end_date=%s&timezone=auto",
                latitude, longitude, parametersCsv, startDate, endDate);

        return new URI(uriStr);
    }

    // Terminal states need no functions (failed, completed)
}