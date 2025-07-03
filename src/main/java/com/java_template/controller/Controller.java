package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("cyoda/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/forecast", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<WeatherForecastResponse>> fetchForecast(@RequestBody @Valid WeatherForecastRequest request) {
        logger.info("Received forecast request lat={} lon={} params={} start={} end={}",
                request.getLatitude(), request.getLongitude(), String.join(",", request.getParameters()),
                request.getStartDate(), request.getEndDate());

        ObjectNode initialEntity = objectMapper.createObjectNode();
        initialEntity.put("status", "processing");
        initialEntity.put("timestamp", Instant.now().toString());

        initialEntity.put("latitude", request.getLatitude());
        initialEntity.put("longitude", request.getLongitude());
        initialEntity.put("startDate", request.getStartDate());
        initialEntity.put("endDate", request.getEndDate());
        initialEntity.putArray("parameters").addAll(objectMapper.valueToTree(request.getParameters()));

        return entityService.addItem(
                "WeatherForecastResult",
                ENTITY_VERSION,
                initialEntity
        ).thenApply(technicalId -> {
            UUID jobId = technicalId;
            return ResponseEntity.accepted().body(new WeatherForecastResponse("success", jobId.toString()));
        });
    }

    @GetMapping(path = "/forecast/{locationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<WeatherForecastResult>> getForecast(@PathVariable("locationId") @NotBlank String locationId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(locationId);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid UUID format for locationId {}", locationId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid locationId format");
        }
        logger.info("Retrieving forecast for locationId {}", locationId);
        return entityService.getItem("WeatherForecastResult", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null) {
                        logger.error("No forecast found for locationId {}", locationId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Forecast not found");
                    }
                    try {
                        WeatherForecastResult result = objectMapper.treeToValue(objectNode, WeatherForecastResult.class);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        logger.error("Failed to parse forecast result for locationId {}: {}", locationId, e.getMessage(), e);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Data parsing error");
                    }
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: status={} message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    // DTOs

    public static class WeatherForecastRequest {
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

        public WeatherForecastRequest() {
        }

        public WeatherForecastRequest(double latitude, double longitude, String[] parameters, String startDate, String endDate) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.parameters = parameters;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public String[] getParameters() {
            return parameters;
        }

        public void setParameters(String[] parameters) {
            this.parameters = parameters;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }
    }

    public static class WeatherForecastResponse {
        private String status;
        private String locationId;

        public WeatherForecastResponse() {
        }

        public WeatherForecastResponse(String status, String locationId) {
            this.status = status;
            this.locationId = locationId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getLocationId() {
            return locationId;
        }

        public void setLocationId(String locationId) {
            this.locationId = locationId;
        }
    }

    public static class WeatherForecastResult {
        private String status;
        private Instant timestamp;
        private JsonNode forecast;

        public WeatherForecastResult() {
        }

        public WeatherForecastResult(String status, Instant timestamp, JsonNode forecast) {
            this.status = status;
            this.timestamp = timestamp;
            this.forecast = forecast;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public JsonNode getForecast() {
            return forecast;
        }

        public void setForecast(JsonNode forecast) {
            this.forecast = forecast;
        }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse() {
        }

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}