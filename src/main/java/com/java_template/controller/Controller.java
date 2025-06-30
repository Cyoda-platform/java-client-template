package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@RequestMapping("/cyoda/weather")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    // In-memory cache of job status and results, keyed by requestId
    private final Map<String, JobInfo> entityJobs = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST endpoint - validate and create minimal entity then call addItem without workflow
    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeather(@RequestBody @Valid FetchRequest request) {
        logger.info("Received weather fetch request: {}", request);
        if (request.getLatitude() < -90 || request.getLatitude() > 90 ||
            request.getLongitude() < -180 || request.getLongitude() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid latitude or longitude values");
        }
        if (request.getParameters() == null || request.getParameters().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameters list cannot be empty");
        }

        String requestId = UUID.randomUUID().toString();

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("requestId", requestId);
        entity.put("latitude", request.getLatitude());
        entity.put("longitude", request.getLongitude());
        if (request.getStartDate() != null) entity.put("startDate", request.getStartDate());
        if (request.getEndDate() != null) entity.put("endDate", request.getEndDate());
        entity.putPOJO("parameters", request.getParameters());
        entity.put("status", "processing");
        entity.put("createdTimestamp", Instant.now().toString());

        entityJobs.put(requestId, new JobInfo("processing", Instant.now(), null));

        CompletableFuture<UUID> idFuture = entityService.addItem(
            "WeatherData",
            ENTITY_VERSION,
            entity
        );

        idFuture.whenComplete((id, ex) -> {
            if (ex != null) {
                logger.error("Failed to persist weather data entity for requestId={}: {}", requestId, ex.getMessage(), ex);
                entityJobs.put(requestId, new JobInfo("failed", Instant.now(), null));
            } else {
                logger.info("Weather data entity persisted with id {} for requestId={}", id, requestId);
                // Entity updated elsewhere; job status updated accordingly
            }
        });

        return ResponseEntity.accepted().body(new FetchResponse("success", "Weather data fetching triggered", requestId));
    }

    // GET endpoint - return job status and result for requestId
    @GetMapping("/result/{requestId}")
    public ResponseEntity<ResultResponse> getWeatherResult(@PathVariable @NotBlank String requestId) {
        logger.info("Retrieving weather result for requestId={}", requestId);
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
        if (jobInfo.getResult() != null) {
            return ResponseEntity.ok(jobInfo.getResult());
        }
        return ResponseEntity.ok(new ResultResponse(requestId, null, null, "success", null));
    }

    private String buildOpenMeteoUrl(double latitude, double longitude, String startDate, String endDate, List<String> parameters) {
        StringBuilder url = new StringBuilder("https://api.open-meteo.com/v1/forecast?");
        url.append("latitude=").append(latitude);
        url.append("&longitude=").append(longitude);
        url.append("&daily=").append(String.join(",", parameters));
        if (startDate != null) url.append("&start_date=").append(startDate);
        if (endDate != null) url.append("&end_date=").append(endDate);
        url.append("&timezone=auto");
        return url.toString();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Request failed with status {}: {}", ex.getStatusCode(), ex.getReason());
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