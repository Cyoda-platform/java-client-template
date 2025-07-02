package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchResponse>> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        log.info("Received weather fetch request: {}", request);
        WeatherJob job = new WeatherJob();
        job.setStatus("processing");
        job.setRequestedAt(Instant.now());
        job.setLatitude(request.getLatitude());
        job.setLongitude(request.getLongitude());
        job.setParameters(request.getParameters());
        job.setForecastDays(request.getForecastDays());

        return entityService.addItem("WeatherJob", ENTITY_VERSION, job)
                .thenApply(technicalId -> {
                    log.info("Created WeatherJob with technicalId={}", technicalId);
                    return ResponseEntity.accepted().body(new FetchResponse(technicalId.toString(), "processing"));
                });
    }

    @GetMapping("/result/{requestId}")
    public CompletableFuture<ResponseEntity<WeatherResult>> getWeatherResult(@PathVariable @NotBlank String requestId) {
        log.info("Fetching weather result for requestId={}", requestId);
        UUID technicalId = UUID.fromString(requestId);
        return entityService.getItem("WeatherResult", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Weather data not found");
                    }
                    WeatherResult result = objectMapper.convertValue(objectNode, WeatherResult.class);
                    return ResponseEntity.ok(result);
                });
    }

    @Async
    protected CompletableFuture<Void> fetchAndStoreWeatherDataAsync(UUID requestId, FetchRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting async fetch for requestId={}", requestId);
                String url = buildOpenMeteoUrl(request);
                log.info("Calling Open-Meteo API: {}", url);
                String response = restTemplate.getForObject(URI.create(url), String.class);
                if (!StringUtils.hasText(response)) {
                    throw new IllegalStateException("Empty response from Open-Meteo API");
                }
                JsonNode rootNode = objectMapper.readTree(response);
                WeatherResult weatherResult = new WeatherResult();
                weatherResult.setTechnicalId(requestId);
                weatherResult.setRequestId(requestId.toString());
                weatherResult.setLatitude(request.getLatitude());
                weatherResult.setLongitude(request.getLongitude());
                weatherResult.setParameters(rootNode);
                weatherResult.setForecastDays(request.getForecastDays());
                weatherResult.setTimestamp(Instant.now());

                entityService.updateItem("WeatherResult", ENTITY_VERSION, requestId, weatherResult).join();

                WeatherJob job = new WeatherJob();
                job.setStatus("completed");
                job.setRequestedAt(Instant.now());
                entityService.updateItem("WeatherJob", ENTITY_VERSION, requestId, job).join();

                log.info("Completed async fetch for requestId={}", requestId);
            } catch (Exception e) {
                log.error("Error fetching weather data for requestId={}", requestId, e);
                WeatherJob job = new WeatherJob();
                job.setStatus("failed");
                job.setRequestedAt(Instant.now());
                entityService.updateItem("WeatherJob", ENTITY_VERSION, requestId, job).join();
            }
        });
    }

    private String buildOpenMeteoUrl(FetchRequest request) {
        StringBuilder dailyParams = new StringBuilder();
        for (String param : request.getParameters()) {
            switch (param.toLowerCase()) {
                case "temperature":
                    appendParam(dailyParams, "temperature_2m_max");
                    break;
                case "precipitation":
                    appendParam(dailyParams, "precipitation_sum");
                    break;
                default:
            }
        }
        if (dailyParams.isEmpty()) {
            dailyParams.append("temperature_2m_max");
        }
        int days = request.getForecastDays();
        return String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&timezone=auto&forecast_days=%d",
                request.getLatitude(),
                request.getLongitude(),
                dailyParams,
                days
        );
    }

    private void appendParam(StringBuilder sb, String param) {
        if (sb.length() > 0) sb.append(",");
        sb.append(param);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()),
                ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return new ResponseEntity<>(new ErrorResponse(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Internal server error"),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        @NotNull
        @Min(-90)
        @Max(90)
        private Double latitude;

        @NotNull
        @Min(-180)
        @Max(180)
        private Double longitude;

        @NotNull
        @Size(min = 1)
        private String[] parameters;

        @NotNull
        @Min(1)
        @Max(16)
        private Integer forecastDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private String requestId;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherJob {
        private String status;
        private Instant requestedAt;
        private Double latitude;
        private Double longitude;
        private String[] parameters;
        private Integer forecastDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherResult {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String requestId;
        private Double latitude;
        private Double longitude;
        private JsonNode parameters;
        private Integer forecastDays;
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