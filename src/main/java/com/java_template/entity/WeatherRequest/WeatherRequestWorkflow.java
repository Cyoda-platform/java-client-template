package com.java_template.entity.WeatherRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class WeatherRequestWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(WeatherRequestWorkflow.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> validateFields(ObjectNode entity) {
        boolean valid = true;
        if (!entity.hasNonNull("latitude") || !entity.hasNonNull("longitude")) {
            valid = false;
        }
        if (!entity.hasNonNull("startDate") || !entity.hasNonNull("endDate")) {
            valid = false;
        }
        if (!entity.hasNonNull("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0) {
            valid = false;
        }
        entity.put("success", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setFailedStatus(ObjectNode entity) {
        entity.put("status", "failed");
        entity.put("fetchedAt", Instant.now().toString());
        entity.putNull("data");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchAndSetWeatherData(ObjectNode entity) {
        try {
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
                entity.put("success", false);
                return CompletableFuture.completedFuture(entity);
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
            entity.put("success", true);
        } catch (Exception e) {
            logger.error("Error fetching weather data", e);
            entity.put("success", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> setCompletedStatus(ObjectNode entity) {
        entity.put("status", "completed");
        entity.put("fetchedAt", Instant.now().toString());
        return CompletableFuture.completedFuture(entity);
    }
}