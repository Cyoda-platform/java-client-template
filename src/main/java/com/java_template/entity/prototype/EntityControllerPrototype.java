```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/weather")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock storage for fetched forecasts keyed by locationId
    private final Map<String, WeatherForecastResult> entityJobs = new ConcurrentHashMap<>();

    /**
     * POST /prototype/weather/forecast
     * Fetch weather forecast data from external API and store internally.
     */
    @PostMapping(path = "/forecast", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeatherForecastResponse> fetchForecast(@Valid @RequestBody WeatherForecastRequest request) {
        logger.info("Received forecast request for lat={}, lon={}, params={}, start_date={}, end_date={}",
                request.getLatitude(), request.getLongitude(), request.getParameters(), request.getStartDate(), request.getEndDate());

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Mark job as processing (fire-and-forget)
        entityJobs.put(jobId, new WeatherForecastResult("processing", requestedAt, null));

        // Fire-and-forget async fetch and process
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode forecastJson = callOpenMeteoApi(request);
                WeatherForecastResult result = new WeatherForecastResult("completed", Instant.now(), forecastJson);
                entityJobs.put(jobId, result);
                logger.info("Successfully fetched and stored forecast data for jobId {}", jobId);
            } catch (Exception e) {
                logger.error("Error fetching forecast data for jobId {}: {}", jobId, e.getMessage(), e);
                entityJobs.put(jobId, new WeatherForecastResult("failed", Instant.now(), null));
            }
        });
        // TODO: Consider persistence or event publishing for real workflow

        WeatherForecastResponse response = new WeatherForecastResponse("success", jobId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /prototype/weather/forecast/{locationId}
     * Retrieve cached/stored forecast result by locationId (jobId).
     */
    @GetMapping(path = "/forecast/{locationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeatherForecastResult> getForecast(@PathVariable("locationId") String locationId) {
        logger.info("Fetching stored forecast for locationId {}", locationId);
        WeatherForecastResult result = entityJobs.get(locationId);
        if (result == null) {
            logger.error("Forecast not found for locationId {}", locationId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Forecast data not found");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Calls the Open-Meteo API using RestTemplate and returns the raw JSON response as JsonNode.
     */
    private JsonNode callOpenMeteoApi(WeatherForecastRequest request) throws URISyntaxException {
        String baseUrl = "https://api.open-meteo.com/v1/forecast";

        String parametersCsv = String.join(",", request.getParameters());

        // Build URI with query parameters
        String uriStr = String.format("%s?latitude=%s&longitude=%s&daily=%s&start_date=%s&end_date=%s&timezone=auto",
                baseUrl,
                request.getLatitude(),
                request.getLongitude(),
                parametersCsv,
                request.getStartDate(),
                request.getEndDate());

        URI uri = new URI(uriStr);

        logger.info("Calling external Open-Meteo API: {}", uri);

        String jsonResponse = restTemplate.getForObject(uri, String.class);
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external API");
        }

        try {
            return objectMapper.readTree(jsonResponse);
        } catch (Exception e) {
            logger.error("Failed to parse JSON response from external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid JSON response from external API");
        }
    }

    /**
     * Minimal error handling for ResponseStatusException.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    /**
     * Generic error response model.
     */
    @Data
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }

    /**
     * Request DTO for POST /forecast
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastRequest {
        private Double latitude;
        private Double longitude;
        private String[] parameters;
        private String startDate; // ISO Date yyyy-MM-dd
        private String endDate;   // ISO Date yyyy-MM-dd
    }

    /**
     * Response DTO for POST /forecast to return jobId for async retrieval.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastResponse {
        private String status;
        private String locationId; // jobId to use for GET retrieval
    }

    /**
     * Stored forecast result with status and raw JSON data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastResult {
        private String status; // e.g. processing, completed, failed
        private Instant timestamp;
        private JsonNode forecast; // raw JSON data from external API
    }
}
```