package com.java_template.entity;

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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
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

    // Workflow function for WeatherJob entity
    // Performs async weather data fetch and persists WeatherResult entity
    // Updates WeatherJob entity status accordingly
    protected CompletableFuture<ObjectNode> processWeatherJob(ObjectNode entityObjNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Workflow: processWeatherJob started for entity: {}", entityObjNode);

                // Extract fields with validation, fallback or throw exception if missing
                Double latitude = entityObjNode.hasNonNull("latitude") ? entityObjNode.get("latitude").asDouble() : null;
                Double longitude = entityObjNode.hasNonNull("longitude") ? entityObjNode.get("longitude").asDouble() : null;
                int forecastDays = entityObjNode.hasNonNull("forecastDays") ? entityObjNode.get("forecastDays").asInt() : 1;
                JsonNode parametersNode = entityObjNode.get("parameters");

                if (latitude == null || longitude == null) {
                    throw new IllegalArgumentException("Latitude and longitude must be provided in WeatherJob entity");
                }

                String[] parameters;
                if (parametersNode != null && parametersNode.isArray()) {
                    parameters = new String[parametersNode.size()];
                    for (int i = 0; i < parametersNode.size(); i++) {
                        parameters[i] = parametersNode.get(i).asText();
                    }
                } else {
                    parameters = new String[0];
                }

                String url = buildOpenMeteoUrl(latitude, longitude, parameters, forecastDays);
                log.info("Workflow: Calling Open-Meteo API: {}", url);

                String response = restTemplate.getForObject(URI.create(url), String.class);

                if (!StringUtils.hasText(response)) {
                    throw new IllegalStateException("Empty response from Open-Meteo API");
                }

                JsonNode weatherData = objectMapper.readTree(response);

                // Prepare WeatherResult entity node
                ObjectNode weatherResultNode = objectMapper.createObjectNode();
                UUID technicalId = UUID.randomUUID();

                weatherResultNode.put("technicalId", technicalId.toString());
                String requestId = entityObjNode.hasNonNull("requestId") ? entityObjNode.get("requestId").asText() : technicalId.toString();
                weatherResultNode.put("requestId", requestId);
                weatherResultNode.put("latitude", latitude);
                weatherResultNode.put("longitude", longitude);
                weatherResultNode.set("parameters", weatherData);
                weatherResultNode.put("forecastDays", forecastDays);
                weatherResultNode.put("timestamp", Instant.now().toString());

                // Persist WeatherResult entity (different entityModel)
                entityService.addItem("WeatherResult", ENTITY_VERSION, weatherResultNode).join();

                // Update WeatherJob entity status to completed
                entityObjNode.put("status", "completed");
                entityObjNode.put("updatedAt", Instant.now().toString());

                log.info("Workflow: processWeatherJob completed successfully");
            } catch (Exception ex) {
                log.error("Workflow: processWeatherJob failed", ex);
                // Update WeatherJob entity status to failed
                entityObjNode.put("status", "failed");
                entityObjNode.put("updatedAt", Instant.now().toString());
            }
            return entityObjNode;
        });
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchResponse>> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        log.info("Received weather fetch request: {}", request);

        // Prepare WeatherJob as ObjectNode
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

        // Add item with workflow function processWeatherJob
        return entityService.addItem(
                        "WeatherJob",
                        ENTITY_VERSION,
                        jobNode,
                        this::processWeatherJob
                )
                .thenApply(technicalId -> {
                    log.info("Created WeatherJob with technicalId={}", technicalId);
                    return ResponseEntity.accepted().body(new FetchResponse(technicalId.toString(), "processing"));
                });
    }

    @GetMapping("/result/{requestId}")
    public CompletableFuture<ResponseEntity<WeatherResult>> getWeatherResult(@PathVariable @NotBlank String requestId) {
        log.info("Fetching weather result for requestId={}", requestId);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }
        return entityService.getItem("WeatherResult", ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
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
                    // Ignore unknown params
            }
        }
        if (dailyParams.length() == 0) {
            dailyParams.append("temperature_2m_max");
        }

        // Clamp forecastDays to allowed range (1-16) to prevent invalid requests
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
    public static class WeatherResult {
        private String technicalId;
        private String requestId;
        private Double latitude;
        private Double longitude;
        private JsonNode parameters;
        private Integer forecastDays;
        private String timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}