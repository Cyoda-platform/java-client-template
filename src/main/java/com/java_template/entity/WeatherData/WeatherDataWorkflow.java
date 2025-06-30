package com.java_template.entity.WeatherData;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class WeatherDataWorkflow {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final EntityService entityService;

    // In-memory cache of job status and results, keyed by requestId
    private final Map<String, JobInfo> entityJobs = new ConcurrentHashMap<>();

    public WeatherDataWorkflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow orchestration only, no business logic here
    public CompletableFuture<ObjectNode> processWeatherData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String requestId = entity.has("requestId") ? entity.get("requestId").asText() : "unknown";
            try {
                double latitude = entity.path("latitude").asDouble(Double.NaN);
                double longitude = entity.path("longitude").asDouble(Double.NaN);
                if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    throw new IllegalArgumentException("Latitude or longitude missing or invalid");
                }
                String startDate = entity.hasNonNull("startDate") ? entity.get("startDate").asText() : null;
                String endDate = entity.hasNonNull("endDate") ? entity.get("endDate").asText() : null;
                List<String> parameters = objectMapper.convertValue(entity.get("parameters"), List.class);
                if (parameters == null || parameters.isEmpty()) {
                    throw new IllegalArgumentException("Parameters list missing or empty");
                }

                // Business logic delegated to separate private method
                return fetchAndProcess(entity, requestId, latitude, longitude, startDate, endDate, parameters);

            } catch (Exception e) {
                log.error("Workflow failed for requestId={}: {}", requestId, e.getMessage(), e);
                entity.put("status", "failed");
                entity.put("processedTimestamp", Instant.now().toString());
                entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
                return entity;
            }
        });
    }

    public CompletableFuture<ObjectNode> validateInputReturnsFalse(ObjectNode entity) {
        boolean invalid = false;
        if (!entity.has("latitude") || entity.path("latitude").isNull() || entity.path("latitude").asText().isBlank()) {
            invalid = true;
        }
        if (!entity.has("longitude") || entity.path("longitude").isNull() || entity.path("longitude").asText().isBlank()) {
            invalid = true;
        }
        if (!entity.has("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0) {
            invalid = true;
        }
        entity.put("success", !invalid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> validateInputReturnsTrue(ObjectNode entity) {
        boolean valid = true;
        if (!entity.has("latitude") || entity.path("latitude").isNull() || entity.path("latitude").asText().isBlank()) {
            valid = false;
        }
        if (!entity.has("longitude") || entity.path("longitude").isNull() || entity.path("longitude").asText().isBlank()) {
            valid = false;
        }
        if (!entity.has("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0) {
            valid = false;
        }
        entity.put("success", valid);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> handleFailure(ObjectNode entity) {
        entity.put("status", "failed");
        entity.put("processedTimestamp", Instant.now().toString());
        String requestId = entity.has("requestId") ? entity.get("requestId").asText() : "unknown";
        entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchAndProcess(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestId = entity.has("requestId") ? entity.get("requestId").asText() : "unknown";
                double latitude = entity.path("latitude").asDouble(Double.NaN);
                double longitude = entity.path("longitude").asDouble(Double.NaN);
                String startDate = entity.hasNonNull("startDate") ? entity.get("startDate").asText() : null;
                String endDate = entity.hasNonNull("endDate") ? entity.get("endDate").asText() : null;
                List<String> parameters = objectMapper.convertValue(entity.get("parameters"), List.class);

                ObjectNode result = fetchAndProcess(entity, requestId, latitude, longitude, startDate, endDate, parameters);
                return result;
            } catch (Exception e) {
                log.error("Fetch and process failed: {}", e.getMessage(), e);
                entity.put("status", "failed");
                entity.put("processedTimestamp", Instant.now().toString());
                return entity;
            }
        });
    }

    public CompletableFuture<ObjectNode> persistRawData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestId = entity.has("requestId") ? entity.get("requestId").asText() : "unknown";
                String rawResponse = null;
                if (entity.has("data")) {
                    rawResponse = entity.get("data").toString();
                }
                ObjectNode rawDataEntity = objectMapper.createObjectNode();
                rawDataEntity.put("requestId", requestId);
                rawDataEntity.put("createdTimestamp", Instant.now().toString());
                rawDataEntity.put("rawResponse", rawResponse != null ? rawResponse : "");
                entityService.addItem("WeatherRawData", ENTITY_VERSION, rawDataEntity, null);
            } catch (Exception e) {
                log.warn("Failed to persist raw data: {}", e.getMessage());
            }
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> updateEntitySuccessState(ObjectNode entity) {
        entity.put("status", "success");
        entity.put("processedTimestamp", Instant.now().toString());
        String requestId = entity.has("requestId") ? entity.get("requestId").asText() : "unknown";
        entityJobs.put(requestId, new JobInfo("success", Instant.now(), null));
        return CompletableFuture.completedFuture(entity);
    }

    private ObjectNode fetchAndProcess(ObjectNode entity, String requestId, double latitude, double longitude, String startDate, String endDate, List<String> parameters) throws Exception {
        log.info("Workflow started for requestId={}", requestId);

        String url = buildOpenMeteoUrl(latitude, longitude, startDate, endDate, parameters);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("External API responded with status code: " + response.statusCode());
        }

        String rawResponse = response.body();
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new RuntimeException("Empty response from Open-Meteo API");
        }

        JsonNode rootNode = objectMapper.readTree(rawResponse);
        JsonNode dailyNode = rootNode.path("daily");

        List<String> dates = new ArrayList<>();
        if (dailyNode.has("time") && dailyNode.get("time").isArray()) {
            dailyNode.get("time").forEach(d -> dates.add(d.asText()));
        }

        Map<String, List<Object>> dataMap = new HashMap<>();
        for (String param : parameters) {
            List<Object> values = new ArrayList<>();
            JsonNode paramNode = dailyNode.get(param);
            if (paramNode != null && paramNode.isArray()) {
                paramNode.forEach(v -> {
                    if (v.isNumber()) values.add(v.numberValue());
                    else if (v.isTextual()) values.add(v.asText());
                    else values.add(v.toString());
                });
            }
            dataMap.put(param, values);
        }

        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.putPOJO("dates", dates);
        dataNode.putPOJO("parameters", dataMap);
        entity.set("data", dataNode);

        entity.put("status", "success");
        entity.put("processedTimestamp", Instant.now().toString());

        // Persist supplementary raw data entity - must not modify current entity
        try {
            ObjectNode rawDataEntity = objectMapper.createObjectNode();
            rawDataEntity.put("requestId", requestId);
            rawDataEntity.put("createdTimestamp", Instant.now().toString());
            rawDataEntity.put("rawResponse", rawResponse);
            entityService.addItem("WeatherRawData", ENTITY_VERSION, rawDataEntity, null);
        } catch (Exception e) {
            log.warn("Failed to persist supplementary raw data entity for requestId={}: {}", requestId, e.getMessage());
        }

        ResultResponse result = new ResultResponse(
                requestId,
                latitude,
                longitude,
                "success",
                new WeatherData(dates, dataMap)
        );
        entityJobs.put(requestId, new JobInfo("success", Instant.now(), result));

        log.info("Workflow completed successfully for requestId={}", requestId);

        return entity;
    }

    private String buildOpenMeteoUrl(double latitude, double longitude, String startDate, String endDate, List<String> parameters) {
        StringBuilder urlBuilder = new StringBuilder("https://api.open-meteo.com/v1/forecast?");
        urlBuilder.append("latitude=").append(latitude);
        urlBuilder.append("&longitude=").append(longitude);
        urlBuilder.append("&daily=").append(String.join(",", parameters));
        if (startDate != null) urlBuilder.append("&start_date=").append(startDate);
        if (endDate != null) urlBuilder.append("&end_date=").append(endDate);
        urlBuilder.append("&timezone=auto");
        return urlBuilder.toString();
    }

    // --- DTOs ---

    public record ResultResponse(String requestId, double latitude, double longitude, String status, WeatherData data) {}

    public record WeatherData(List<String> dates, Map<String, List<Object>> parameters) {}

    public record JobInfo(String status, Instant lastUpdated, ResultResponse result) {}

}