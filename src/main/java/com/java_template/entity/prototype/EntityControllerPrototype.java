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
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory job status and data storage (mock persistence)
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private final Map<String, WeatherResult> weatherResults = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeatherData(@RequestBody FetchRequest request) {
        log.info("Received weather fetch request: {}", request);

        validateRequest(request);

        String requestId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Mark job as processing
        entityJobs.put(requestId, new JobStatus("processing", requestedAt));
        // Fire-and-forget async fetch and store
        fetchAndStoreWeatherDataAsync(requestId, request);

        FetchResponse response = new FetchResponse(requestId, "processing");
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<WeatherResult> getWeatherResult(@PathVariable String requestId) {
        log.info("Fetching weather result for requestId={}", requestId);

        if (!entityJobs.containsKey(requestId)) {
            log.error("RequestId {} not found", requestId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }

        JobStatus status = entityJobs.get(requestId);
        if ("processing".equalsIgnoreCase(status.getStatus())) {
            log.info("RequestId {} still processing", requestId);
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "Data is still processing");
        }

        WeatherResult result = weatherResults.get(requestId);
        if (result == null) {
            log.error("No weather data found for requestId {}", requestId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Weather data not found");
        }

        return ResponseEntity.ok(result);
    }

    @Async
    protected CompletableFuture<Void> fetchAndStoreWeatherDataAsync(String requestId, FetchRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting async fetch for requestId={}", requestId);

                // Build external API URL
                String url = buildOpenMeteoUrl(request);

                log.info("Calling Open-Meteo API: {}", url);
                String response = restTemplate.getForObject(URI.create(url), String.class);

                if (!StringUtils.hasText(response)) {
                    throw new IllegalStateException("Empty response from Open-Meteo API");
                }

                JsonNode rootNode = objectMapper.readTree(response);

                // TODO: Here we can parse and filter required parameters from the rootNode if needed.
                // For prototype, store entire JSON under parameters field.

                WeatherResult weatherResult = new WeatherResult(
                        requestId,
                        request.getLatitude(),
                        request.getLongitude(),
                        rootNode,
                        request.getForecast_days(),
                        Instant.now()
                );

                weatherResults.put(requestId, weatherResult);
                entityJobs.put(requestId, new JobStatus("completed", Instant.now()));

                log.info("Completed async fetch for requestId={}", requestId);

            } catch (Exception e) {
                log.error("Error fetching weather data for requestId={}: {}", requestId, e.getMessage(), e);
                entityJobs.put(requestId, new JobStatus("failed", Instant.now()));
            }
        });
    }

    private String buildOpenMeteoUrl(FetchRequest request) {
        // Open-Meteo API example:
        // https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.405&daily=temperature_2m_max,precipitation_sum&timezone=auto&forecast_days=7
        // We'll map requested parameters to Open-Meteo daily parameters by a simple prefix + join.
        // For prototype: map supported parameters only (temperature -> temperature_2m_max, precipitation -> precipitation_sum)
        // TODO: Expand mapping as needed.

        StringBuilder dailyParams = new StringBuilder();

        if (request.getParameters() != null) {
            for (String param : request.getParameters()) {
                switch (param.toLowerCase()) {
                    case "temperature":
                        if (dailyParams.length() > 0) dailyParams.append(",");
                        dailyParams.append("temperature_2m_max");
                        break;
                    case "precipitation":
                        if (dailyParams.length() > 0) dailyParams.append(",");
                        dailyParams.append("precipitation_sum");
                        break;
                    // Add more mappings if needed
                    default:
                        // Ignore unsupported params in prototype
                }
            }
        }

        if (dailyParams.length() == 0) {
            dailyParams.append("temperature_2m_max"); // default fallback
        }

        int days = request.getForecast_days() != null && request.getForecast_days() > 0 ? request.getForecast_days() : 7;

        return String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&timezone=auto&forecast_days=%d",
                request.getLatitude(),
                request.getLongitude(),
                dailyParams,
                days
        );
    }

    private void validateRequest(FetchRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is missing");
        }
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude and longitude are required");
        }
        if (request.getLatitude() < -90 || request.getLatitude() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude must be between -90 and 90");
        }
        if (request.getLongitude() < -180 || request.getLongitude() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Longitude must be between -180 and 180");
        }
        if (request.getForecast_days() != null && (request.getForecast_days() < 1 || request.getForecast_days() > 16)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast days must be between 1 and 16");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Internal server error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        private Double latitude;
        private Double longitude;
        private String[] parameters;
        private Integer forecast_days;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private String requestId;
        private String status; // e.g. "processing"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status; // processing, completed, failed
        private Instant updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherResult {
        private String requestId;
        private Double latitude;
        private Double longitude;
        private JsonNode parameters; // raw JSON from Open-Meteo API
        private Integer forecast_days;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}
```