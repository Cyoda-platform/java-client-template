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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/weather")
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, JobInfo> entityJobs = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeather(@RequestBody @Valid FetchRequest request) {
        log.info("Received weather fetch request: {}", request);
        if (request.getLatitude() < -90 || request.getLatitude() > 90 ||
            request.getLongitude() < -180 || request.getLongitude() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid latitude or longitude values");
        }
        String requestId = UUID.randomUUID().toString();
        entityJobs.put(requestId, new JobInfo("processing", Instant.now(), null));
        CompletableFuture.runAsync(() -> fetchAndProcessWeather(requestId, request))
            .exceptionally(ex -> {
                log.error("Error processing weather fetch for requestId={}: {}", requestId, ex.getMessage(), ex);
                entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
                return null;
            });
        return ResponseEntity.accepted().body(new FetchResponse("success", "Weather data fetching triggered", requestId));
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<ResultResponse> getWeatherResult(@PathVariable @NotBlank String requestId) {
        log.info("Retrieving weather result for requestId={}", requestId);
        JobInfo jobInfo = entityJobs.get(requestId);
        if (jobInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request ID not found");
        }
        if ("processing".equalsIgnoreCase(jobInfo.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResultResponse(requestId, null, null, "processing", null));
        }
        if ("failed".equalsIgnoreCase(jobInfo.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ResultResponse(requestId, null, null, "failed", null));
        }
        return ResponseEntity.ok(jobInfo.getResult());
    }

    private void fetchAndProcessWeather(String requestId, FetchRequest request) {
        log.info("Starting background fetch for requestId={}", requestId);
        try {
            String url = buildOpenMeteoUrl(request);
            log.info("Calling external API: {}", url);
            String rawResponse = restTemplate.getForObject(new URI(url), String.class);
            if (rawResponse == null) throw new RuntimeException("Empty response from Open-Meteo API");
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode dailyNode = rootNode.path("daily");
            Map<String, List<Object>> dataMap = new HashMap<>();
            List<String> dates = new ArrayList<>();
            if (dailyNode.isObject()) {
                JsonNode dateNode = dailyNode.path("time");
                if (dateNode.isArray()) dateNode.forEach(d -> dates.add(d.asText()));
                for (String param : request.getParameters()) {
                    JsonNode arrNode = dailyNode.path(param);
                    List<Object> values = new ArrayList<>();
                    if (arrNode.isArray()) arrNode.forEach(v -> {
                        if (v.isNumber()) values.add(v.numberValue());
                        else if (v.isTextual()) values.add(v.asText());
                        else values.add(v.toString());
                    });
                    dataMap.put(param, values);
                }
            }
            ResultResponse result = new ResultResponse(
                requestId,
                request.getLatitude(),
                request.getLongitude(),
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

    private String buildOpenMeteoUrl(FetchRequest request) {
        StringBuilder url = new StringBuilder("https://api.open-meteo.com/v1/forecast?");
        url.append("latitude=").append(request.getLatitude());
        url.append("&longitude=").append(request.getLongitude());
        url.append("&daily=").append(String.join(",", request.getParameters()));
        if (request.getStartDate() != null) url.append("&start_date=").append(request.getStartDate());
        if (request.getEndDate() != null) url.append("&end_date=").append(request.getEndDate());
        url.append("&timezone=auto");
        return url.toString();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed with status {}: {}", ex.getStatusCode(), ex.getReason());
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
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String startDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String endDate;
        @NotEmpty
        private List<@NotBlank String> parameters;
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
        private String status;
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