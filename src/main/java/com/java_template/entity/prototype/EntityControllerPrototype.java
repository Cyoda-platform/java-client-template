```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for fetched results keyed by requestId
    private final Map<String, StoredWeatherData> entityJobs = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeatherData(@Valid @RequestBody FetchRequest request) {
        logger.info("Received fetch request: latitude={}, longitude={}, parameters={}, startDate={}, endDate={}",
                request.getLatitude(), request.getLongitude(), request.getParameters(),
                request.getStartDate(), request.getEndDate());

        validateRequest(request);

        String requestId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        entityJobs.put(requestId, new StoredWeatherData("processing", requestedAt, null, null, null, null));

        // Fire-and-forget background processing
        CompletableFuture.runAsync(() -> processFetchRequest(requestId, request));

        FetchResponse response = new FetchResponse(requestId, "success", requestedAt.toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<StoredWeatherData> getWeatherResult(@PathVariable String requestId) {
        logger.info("Received GET result request for requestId={}", requestId);

        StoredWeatherData stored = entityJobs.get(requestId);
        if (stored == null) {
            logger.error("No data found for requestId={}", requestId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for requestId");
        }
        if ("processing".equalsIgnoreCase(stored.getStatus())) {
            logger.info("Data is still processing for requestId={}", requestId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(stored);
        }

        return ResponseEntity.ok(stored);
    }

    private void validateRequest(FetchRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            logger.error("Latitude and Longitude are required");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and Longitude are required");
        }
        if (request.getParameters() == null || request.getParameters().isEmpty()) {
            logger.error("At least one parameter must be specified");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one parameter must be specified");
        }
        if (!StringUtils.hasText(request.getStartDate()) || !StringUtils.hasText(request.getEndDate())) {
            logger.error("Start date and end date are required");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        // Additional validation on dates could be added here (TODO)
    }

    private void processFetchRequest(String requestId, FetchRequest request) {
        logger.info("Processing fetch request asynchronously for requestId={}", requestId);

        try {
            String baseUrl = "https://api.open-meteo.com/v1/forecast";

            String paramsCsv = String.join(",", request.getParameters());
            String url = String.format("%s?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=%s",
                    baseUrl,
                    URLEncoder.encode(request.getLatitude().toString(), StandardCharsets.UTF_8),
                    URLEncoder.encode(request.getLongitude().toString(), StandardCharsets.UTF_8),
                    URLEncoder.encode(request.getStartDate(), StandardCharsets.UTF_8),
                    URLEncoder.encode(request.getEndDate(), StandardCharsets.UTF_8),
                    URLEncoder.encode(paramsCsv, StandardCharsets.UTF_8)
            );

            logger.info("Calling external Open-Meteo API: {}", url);

            String responseStr = restTemplate.getForObject(URI.create(url), String.class);
            if (responseStr == null) {
                throw new IllegalStateException("Empty response from Open-Meteo API");
            }

            JsonNode rootNode = objectMapper.readTree(responseStr);

            JsonNode hourlyNode = rootNode.path("hourly");

            // Extract requested parameters data
            Map<String, JsonNode> paramData = new ConcurrentHashMap<>();
            for (String param : request.getParameters()) {
                JsonNode dataNode = hourlyNode.path(param);
                if (dataNode.isMissingNode()) {
                    logger.warn("Parameter '{}' not found in API response", param);
                }
                paramData.put(param, dataNode.isMissingNode() ? null : dataNode);
            }

            StoredWeatherData storedData = new StoredWeatherData(
                    "completed",
                    Instant.now(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getParameters(),
                    paramData
            );

            entityJobs.put(requestId, storedData);

            logger.info("Successfully stored weather data for requestId={}", requestId);

        } catch (Exception e) {
            logger.error("Failed to fetch weather data for requestId=" + requestId, e);
            entityJobs.put(requestId, new StoredWeatherData(
                    "failed",
                    Instant.now(),
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason(), ex);
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        private Double latitude;
        private Double longitude;
        private String startDate;
        private String endDate;
        private java.util.List<String> parameters;
    }

    @Data
    @AllArgsConstructor
    public static class FetchResponse {
        private String requestId;
        private String status;
        private String fetchedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredWeatherData {
        private String status; // processing, completed, failed
        private Instant fetchedAt;
        private Double latitude;
        private Double longitude;
        private java.util.List<String> parameters;
        private Map<String, JsonNode> data; // parameter -> data array or null
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```