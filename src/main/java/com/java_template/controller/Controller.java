package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping(path = "/cyoda/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchResponse>> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        logger.info("Received weather fetch request: {}", request);

        ObjectNode jobNode = objectMapper.createObjectNode();
        jobNode.put("status", "processing");
        jobNode.put("requestedAt", Instant.now().toString());
        jobNode.put("latitude", request.getLatitude());
        jobNode.put("longitude", request.getLongitude());
        if (request.getParameters() != null) {
            jobNode.putArray("parameters").addAll(objectMapper.valueToTree(request.getParameters()));
        } else {
            jobNode.putArray("parameters");
        }
        jobNode.put("forecastDays", request.getForecastDays());

        return entityService.addItem(
                        "WeatherJob",
                        ENTITY_VERSION,
                        jobNode
                )
                .thenApply(technicalId -> {
                    logger.info("Created WeatherJob with technicalId={}", technicalId);
                    return ResponseEntity.accepted().body(new FetchResponse(technicalId.toString(), "processing"));
                });
    }

    @GetMapping("/result/{requestId}")
    public CompletableFuture<ResponseEntity<WeatherResult>> getWeatherResult(@PathVariable @NotBlank String requestId) {
        logger.info("Fetching weather result for requestId={}", requestId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid requestId format: {}", requestId, e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }
        return entityService.getItem("WeatherResult", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        logger.error("Weather data not found for requestId={}", requestId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Weather data not found");
                    }
                    WeatherResult result = objectMapper.convertValue(objectNode, WeatherResult.class);
                    return ResponseEntity.ok(result);
                });
    }

    private String buildOpenMeteoUrl(Double latitude, Double longitude, String[] parameters, int forecastDays) {
        StringBuilder dailyParams = new StringBuilder();
        for (String param : parameters) {
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
        if (dailyParams.length() == 0) {
            dailyParams.append("temperature_2m_max");
        }

        int days = Math.min(Math.max(forecastDays, 1), 16);

        return String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&timezone=auto&forecast_days=%d",
                latitude,
                longitude,
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
        logger.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()),
                ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        return new ResponseEntity<>(new ErrorResponse(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Internal server error"),
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // DTO classes

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

        public FetchRequest() {}

        public FetchRequest(Double latitude, Double longitude, String[] parameters, Integer forecastDays) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.parameters = parameters;
            this.forecastDays = forecastDays;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public String[] getParameters() {
            return parameters;
        }

        public void setParameters(String[] parameters) {
            this.parameters = parameters;
        }

        public Integer getForecastDays() {
            return forecastDays;
        }

        public void setForecastDays(Integer forecastDays) {
            this.forecastDays = forecastDays;
        }

        @Override
        public String toString() {
            return "FetchRequest{" +
                    "latitude=" + latitude +
                    ", longitude=" + longitude +
                    ", parameters=" + java.util.Arrays.toString(parameters) +
                    ", forecastDays=" + forecastDays +
                    '}';
        }
    }

    public static class FetchResponse {
        private String requestId;
        private String status;

        public FetchResponse() {}

        public FetchResponse(String requestId, String status) {
            this.requestId = requestId;
            this.status = status;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class WeatherResult {
        private String technicalId;
        private String requestId;
        private Double latitude;
        private Double longitude;
        private JsonNode parameters;
        private Integer forecastDays;
        private String timestamp;

        public WeatherResult() {}

        public WeatherResult(String technicalId, String requestId, Double latitude, Double longitude, JsonNode parameters, Integer forecastDays, String timestamp) {
            this.technicalId = technicalId;
            this.requestId = requestId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.parameters = parameters;
            this.forecastDays = forecastDays;
            this.timestamp = timestamp;
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public JsonNode getParameters() {
            return parameters;
        }

        public void setParameters(JsonNode parameters) {
            this.parameters = parameters;
        }

        public Integer getForecastDays() {
            return forecastDays;
        }

        public void setForecastDays(Integer forecastDays) {
            this.forecastDays = forecastDays;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse() {}

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