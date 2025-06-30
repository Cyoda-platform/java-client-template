package com.java_template.entity.WeatherRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.client.RestTemplate;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class WeatherRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(WeatherRequestWorkflow.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Main workflow orchestration method - no business logic here, only method calls
    public CompletableFuture<ObjectNode> processWeatherRequest(ObjectNode entity) {
        logger.info("Workflow started: processWeatherRequest for entity id={}", entity.path("id").asText(null));
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateFields(entity);
                fetchAndSetWeatherData(entity);
                setCompletedStatus(entity);
                logger.info("Workflow completed successfully for entity");
            } catch (Exception e) {
                logger.error("Workflow failed during processing weather data", e);
                setFailedStatus(entity);
            }
            return entity;
        });
    }

    // Validation logic separated
    private void validateFields(ObjectNode entity) {
        if (!entity.hasNonNull("latitude") || !entity.hasNonNull("longitude"))
            throw new IllegalArgumentException("Entity missing latitude or longitude");
        if (!entity.hasNonNull("startDate") || !entity.hasNonNull("endDate"))
            throw new IllegalArgumentException("Entity missing startDate or endDate");
        if (!entity.hasNonNull("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0)
            throw new IllegalArgumentException("Entity missing parameters or parameters array is empty");
    }

    // Business logic for fetching data and updating entity.data
    private void fetchAndSetWeatherData(ObjectNode entity) throws Exception {
        double latitude = entity.get("latitude").asDouble();
        double longitude = entity.get("longitude").asDouble();
        String startDate = entity.get("startDate").asText();
        String endDate = entity.get("endDate").asText();

        List<String> parameters = objectMapper.convertValue(
                entity.get("parameters"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        String baseUrl = "https://api.open-meteo.com/v1/forecast";
        String paramsCsv = String.join(",", parameters);

        String url = String.format("%s?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=%s",
                baseUrl,
                URLEncoder.encode(String.valueOf(latitude), StandardCharsets.UTF_8),
                URLEncoder.encode(String.valueOf(longitude), StandardCharsets.UTF_8),
                URLEncoder.encode(startDate, StandardCharsets.UTF_8),
                URLEncoder.encode(endDate, StandardCharsets.UTF_8),
                URLEncoder.encode(paramsCsv, StandardCharsets.UTF_8)
        );

        logger.info("Calling external Open-Meteo API: {}", url);

        String responseStr = restTemplate.getForObject(URI.create(url), String.class);
        if (responseStr == null) {
            throw new IllegalStateException("Empty response from Open-Meteo API");
        }

        JsonNode rootNode = objectMapper.readTree(responseStr);
        JsonNode hourlyNode = rootNode.path("hourly");

        ObjectNode dataNode = objectMapper.createObjectNode();

        for (String param : parameters) {
            JsonNode paramNode = hourlyNode.path(param);
            if (paramNode.isMissingNode()) {
                logger.warn("Parameter '{}' not found in API response", param);
                dataNode.putNull(param);
            } else {
                dataNode.set(param, paramNode);
            }
        }

        entity.set("data", dataNode);
    }

    private void setCompletedStatus(ObjectNode entity) {
        entity.put("status", "completed");
        entity.put("fetchedAt", Instant.now().toString());
    }

    private void setFailedStatus(ObjectNode entity) {
        entity.put("status", "failed");
        entity.put("fetchedAt", Instant.now().toString());
        entity.putNull("data");
    }
}