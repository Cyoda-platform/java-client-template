package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping("prototype/weather")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WeatherForecastResult> entityJobs = new ConcurrentHashMap<>();

    @PostMapping(path = "/forecast", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeatherForecastResponse> fetchForecast(@RequestBody @Valid WeatherForecastRequest request) {
        logger.info("Received forecast request lat={} lon={} params={} start={} end={}",
                request.getLatitude(), request.getLongitude(), String.join(",", request.getParameters()),
                request.getStartDate(), request.getEndDate());

        String jobId = UUID.randomUUID().toString();
        entityJobs.put(jobId, new WeatherForecastResult("processing", Instant.now(), null));

        CompletableFuture.runAsync(() -> {
            try {
                JsonNode forecastJson = callOpenMeteoApi(request);
                entityJobs.put(jobId, new WeatherForecastResult("completed", Instant.now(), forecastJson));
                logger.info("Completed forecast fetch for jobId {}", jobId);
            } catch (Exception e) {
                logger.error("Error in async fetch for jobId {}: {}", jobId, e.getMessage(), e);
                entityJobs.put(jobId, new WeatherForecastResult("failed", Instant.now(), null));
            }
        });

        return ResponseEntity.accepted().body(new WeatherForecastResponse("success", jobId));
    }

    @GetMapping(path = "/forecast/{locationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeatherForecastResult> getForecast(@PathVariable("locationId") @NotBlank String locationId) {
        logger.info("Retrieving forecast for locationId {}", locationId);
        WeatherForecastResult result = entityJobs.get(locationId);
        if (result == null) {
            logger.error("No forecast found for locationId {}", locationId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Forecast not found");
        }
        return ResponseEntity.ok(result);
    }

    private JsonNode callOpenMeteoApi(WeatherForecastRequest request) throws URISyntaxException {
        String baseUrl = "https://api.open-meteo.com/v1/forecast";
        String parametersCsv = String.join(",", request.getParameters());
        String uriStr = String.format("%s?latitude=%s&longitude=%s&daily=%s&start_date=%s&end_date=%s&timezone=auto",
                baseUrl,
                request.getLatitude(),
                request.getLongitude(),
                parametersCsv,
                request.getStartDate(),
                request.getEndDate());
        URI uri = new URI(uriStr);
        logger.info("Calling external API: {}", uri);
        String jsonResponse = restTemplate.getForObject(uri, String.class);
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty response");
        }
        try {
            return objectMapper.readTree(jsonResponse);
        } catch (Exception e) {
            logger.error("Invalid JSON from external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Invalid JSON");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: status={} message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    // DTOs and result models

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastRequest {
        @Min(-90)
        @Max(90)
        private double latitude;

        @Min(-180)
        @Max(180)
        private double longitude;

        @NotNull
        @Size(min = 1)
        private String[] parameters;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String startDate;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String endDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastResponse {
        private String status;
        private String locationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class WeatherForecastResult {
        private String status;
        private Instant timestamp;
        private JsonNode forecast;
    }

    @Data
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }
}