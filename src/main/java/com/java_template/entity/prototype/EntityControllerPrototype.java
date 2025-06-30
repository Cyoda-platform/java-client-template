```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/weather")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory job storage: requestId -> JobStatus + result
    private final Map<String, JobInfo> entityJobs = new ConcurrentHashMap<>();

    /**
     * POST /prototype/weather/fetch
     * Trigger weather data fetching from Open-Meteo external API.
     */
    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeather(@Valid @RequestBody FetchRequest request) {
        log.info("Received weather fetch request: {}", request);

        // Validate coordinates range roughly (optional, but good practice)
        if (request.latitude < -90 || request.latitude > 90 ||
                request.longitude < -180 || request.longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid latitude or longitude values");
        }
        if (request.parameters == null || request.parameters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameters list must not be empty");
        }

        String requestId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Mark job as processing
        entityJobs.put(requestId, new JobInfo("processing", requestedAt, null));

        // Fire-and-forget the background fetch and processing
        CompletableFuture.runAsync(() -> fetchAndProcessWeather(requestId, request))
                .exceptionally(ex -> {
                    log.error("Error processing weather fetch for requestId={}: {}", requestId, ex.getMessage(), ex);
                    entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
                    return null;
                });

        FetchResponse response = new FetchResponse("success", "Weather data fetching triggered", requestId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /prototype/weather/result/{requestId}
     * Retrieve stored weather data results for given requestId.
     */
    @GetMapping("/result/{requestId}")
    public ResponseEntity<ResultResponse> getWeatherResult(@PathVariable String requestId) {
        log.info("Retrieving weather result for requestId={}", requestId);

        JobInfo jobInfo = entityJobs.get(requestId);
        if (jobInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        if ("processing".equalsIgnoreCase(jobInfo.status)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new ResultResponse(requestId, null, null, "processing", null));
        }
        if ("failed".equalsIgnoreCase(jobInfo.status)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResultResponse(requestId, null, null, "failed", null));
        }

        // Success case
        return ResponseEntity.ok(jobInfo.result);
    }

    /**
     * Background method to call Open-Meteo API, parse and store results.
     */
    private void fetchAndProcessWeather(String requestId, FetchRequest request) {
        log.info("Starting background fetch for requestId={}", requestId);

        try {
            String url = buildOpenMeteoUrl(request);
            log.info("Calling external API: {}", url);

            String rawResponse = restTemplate.getForObject(new URI(url), String.class);
            if (rawResponse == null) {
                throw new RuntimeException("Empty response from Open-Meteo API");
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);

            // Extract relevant data from the JSON response based on requested parameters
            // Note: Open-Meteo returns forecasts in "hourly" or "daily" nodes usually.
            // For this prototype, we will try "daily" data if available.

            JsonNode dailyNode = rootNode.path("daily");
            Map<String, List<Object>> dataMap = new HashMap<>();
            List<String> dates = new ArrayList<>();

            if (dailyNode.isMissingNode() || !dailyNode.isObject()) {
                log.warn("No 'daily' data found in Open-Meteo response");
            } else {
                // Extract dates
                JsonNode dateNode = dailyNode.path("time");
                if (dateNode.isArray()) {
                    for (JsonNode d : dateNode) {
                        dates.add(d.asText());
                    }
                }

                // For each requested parameter, extract its array values
                for (String param : request.parameters) {
                    JsonNode arrNode = dailyNode.path(param);
                    List<Object> values = new ArrayList<>();
                    if (arrNode.isArray()) {
                        arrNode.forEach(v -> {
                            if (v.isNumber()) {
                                values.add(v.numberValue());
                            } else if (v.isTextual()) {
                                values.add(v.asText());
                            } else {
                                values.add(v.toString());
                            }
                        });
                    } else {
                        log.warn("Parameter '{}' not found or not an array in daily data", param);
                    }
                    dataMap.put(param, values);
                }
            }

            ResultResponse result = new ResultResponse(
                    requestId,
                    request.latitude,
                    request.longitude,
                    "success",
                    new WeatherData(dates, dataMap)
            );

            entityJobs.put(requestId, new JobInfo("success", Instant.now(), result));
            log.info("Weather data processed and stored for requestId={}", requestId);

        } catch (Exception e) {
            log.error("Failed to fetch/process weather data for requestId={}: {}", requestId, e.getMessage(), e);
            entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
        }
    }

    /**
     * Build Open-Meteo API URL with query parameters based on request.
     */
    private String buildOpenMeteoUrl(FetchRequest request) {
        StringBuilder urlBuilder = new StringBuilder("https://api.open-meteo.com/v1/forecast?");
        urlBuilder.append("latitude=").append(request.latitude);
        urlBuilder.append("&longitude=").append(request.longitude);

        // We use daily parameters for the prototype
        urlBuilder.append("&daily=");
        urlBuilder.append(String.join(",", request.parameters));

        // Optional dates
        if (StringUtils.hasText(request.startDate)) {
            urlBuilder.append("&start_date=").append(request.startDate);
        }
        if (StringUtils.hasText(request.endDate)) {
            urlBuilder.append("&end_date=").append(request.endDate);
        }

        urlBuilder.append("&timezone=auto");

        return urlBuilder.toString();
    }

    // --- Exception handler for minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed with status {}: {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // --- DTOs and holder classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        @NotNull
        public Double latitude;

        @NotNull
        public Double longitude;

        // Optional dates
        public String startDate;
        public String endDate;

        @NotEmpty
        public List<String> parameters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private String status;
        private String message;
        private String requestId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultResponse {
        private String requestId;
        private Double latitude;
        private Double longitude;
        private String status;
        private WeatherData data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherData {
        private List<String> dates;
        private Map<String, List<Object>> parameters;
    }

    @Data
    @AllArgsConstructor
    public static class JobInfo {
        private String status; // "processing", "success", "failed"
        private Instant lastUpdated;
        private ResultResponse result;
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