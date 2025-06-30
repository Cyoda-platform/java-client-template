package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StoredWeatherData> entityJobs = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        logger.info("Received fetch request: latitude={}, longitude={}, parameters={}, startDate={}, endDate={}",
                request.getLatitude(), request.getLongitude(), request.getParameters(),
                request.getStartDate(), request.getEndDate());

        String requestId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        entityJobs.put(requestId, new StoredWeatherData("processing", requestedAt, null, null, null, null));

        CompletableFuture.runAsync(() -> processFetchRequest(requestId, request));

        FetchResponse response = new FetchResponse(requestId, "success", requestedAt.toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<StoredWeatherData> getWeatherResult(@PathVariable @NotBlank String requestId) {
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
        @NotNull
        private Double latitude;
        @NotNull
        private Double longitude;
        @NotBlank
        private String startDate;
        @NotBlank
        private String endDate;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> parameters;
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
        private String status;
        private Instant fetchedAt;
        private Double latitude;
        private Double longitude;
        private List<String> parameters;
        private Map<String, JsonNode> data;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}