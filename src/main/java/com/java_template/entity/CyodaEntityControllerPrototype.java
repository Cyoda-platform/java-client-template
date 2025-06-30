package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/weather", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    private static final String ENTITY_NAME = "WeatherRequest";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch")
    public ResponseEntity<FetchResponse> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        logger.info("Received fetch request: latitude={}, longitude={}, parameters={}, startDate={}, endDate={}",
                request.getLatitude(), request.getLongitude(), request.getParameters(),
                request.getStartDate(), request.getEndDate());

        WeatherRequestData data = new WeatherRequestData(
                "processing",
                Instant.now(),
                request.getLatitude(),
                request.getLongitude(),
                request.getParameters(),
                null,
                null
        );

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                data
        );

        UUID requestId = idFuture.join();

        CompletableFuture.runAsync(() -> processFetchRequest(requestId, request));

        FetchResponse response = new FetchResponse(requestId.toString(), "success", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<WeatherRequestData> getWeatherResult(@PathVariable @NotBlank String requestId) {
        logger.info("Received GET result request for requestId={}", requestId);

        UUID technicalId = UUID.fromString(requestId);
        CompletableFuture<JsonNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );

        JsonNode itemNode = itemFuture.join();
        if (itemNode == null || itemNode.isNull()) {
            logger.error("No data found for requestId={}", requestId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data found for requestId");
        }

        WeatherRequestData stored = null;
        try {
            stored = objectMapper.treeToValue(itemNode, WeatherRequestData.class);
        } catch (Exception e) {
            logger.error("Failed to parse stored weather data for requestId={}", requestId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored data parse error");
        }

        if ("processing".equalsIgnoreCase(stored.getStatus())) {
            logger.info("Data is still processing for requestId={}", requestId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(stored);
        }

        return ResponseEntity.ok(stored);
    }

    private void processFetchRequest(UUID technicalId, FetchRequest request) {
        logger.info("Processing fetch request asynchronously for technicalId={}", technicalId);
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

            Map<String, JsonNode> paramData = new java.util.concurrent.ConcurrentHashMap<>();
            for (String param : request.getParameters()) {
                JsonNode dataNode = hourlyNode.path(param);
                if (dataNode.isMissingNode()) {
                    logger.warn("Parameter '{}' not found in API response", param);
                }
                paramData.put(param, dataNode.isMissingNode() ? null : dataNode);
            }

            WeatherRequestData updatedData = new WeatherRequestData(
                    "completed",
                    Instant.now(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getParameters(),
                    paramData,
                    technicalId // keep technicalId in object for reference if needed
            );

            entityService.updateItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId,
                    updatedData
            ).join();

            logger.info("Successfully stored weather data for technicalId={}", technicalId);

        } catch (Exception e) {
            logger.error("Failed to fetch weather data for technicalId=" + technicalId, e);
            WeatherRequestData failedData = new WeatherRequestData(
                    "failed",
                    Instant.now(),
                    null,
                    null,
                    null,
                    null,
                    technicalId
            );
            entityService.updateItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId,
                    failedData
            ).join();
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
    public static class WeatherRequestData {
        private String status;
        private Instant fetchedAt;
        private Double latitude;
        private Double longitude;
        private List<String> parameters;
        private Map<String, JsonNode> data;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}