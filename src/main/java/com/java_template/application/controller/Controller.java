package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.WeatherData;
import com.java_template.application.entity.WeatherRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/weather")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    // POST endpoint to create WeatherRequest - orchestration entity
    @PostMapping("/weather-requests")
    public ResponseEntity<Map<String, String>> createWeatherRequest(@RequestBody WeatherRequest request) {
        try {
            if (!request.isValid()) {
                log.error("Invalid WeatherRequest received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            request.setRequestTimestamp(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeatherRequest.ENTITY_NAME,
                    ENTITY_VERSION,
                    request
            );

            UUID technicalId = idFuture.get();
            String technicalIdStr = technicalId.toString();

            log.info("WeatherRequest created with technicalId: {}", technicalIdStr);

            try {
                processWeatherRequest(technicalIdStr, request);
            } catch (Exception e) {
                log.error("Error processing WeatherRequest {}: {}", technicalIdStr, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createWeatherRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Exception in createWeatherRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET endpoint to retrieve WeatherRequest by technicalId
    @GetMapping("/weather-requests/{id}")
    public ResponseEntity<WeatherRequest> getWeatherRequest(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeatherRequest.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("WeatherRequest not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            WeatherRequest request = node.traverse().readValueAs(WeatherRequest.class);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for WeatherRequest id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                log.error("IllegalArgumentException in getWeatherRequest: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.error("ExecutionException in getWeatherRequest: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET endpoint to retrieve WeatherData by technicalId
    @GetMapping("/weather-data/{id}")
    public ResponseEntity<WeatherData> getWeatherData(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeatherData.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("WeatherData not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            WeatherData data = node.traverse().readValueAs(WeatherData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for WeatherData id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                log.error("IllegalArgumentException in getWeatherData: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            log.error("ExecutionException in getWeatherData: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Optional GET endpoint to retrieve weather data by weatherRequestId filter
    @GetMapping("/weather-data")
    public ResponseEntity<List<WeatherData>> getWeatherDataByRequestId(@RequestParam(value = "weatherRequestId", required = false) String weatherRequestId) {
        try {
            CompletableFuture<ArrayNode> itemsFuture;
            if (weatherRequestId == null) {
                itemsFuture = entityService.getItems(
                        WeatherData.ENTITY_NAME,
                        ENTITY_VERSION
                );
            } else {
                // Build condition for weatherRequestId equality
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.weatherRequestId", "EQUALS", weatherRequestId)
                );
                itemsFuture = entityService.getItemsByCondition(
                        WeatherData.ENTITY_NAME,
                        ENTITY_VERSION,
                        condition,
                        true
                );
            }
            ArrayNode arrayNode = itemsFuture.get();
            if (arrayNode == null || arrayNode.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<WeatherData> results = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                ObjectNode node = (ObjectNode) arrayNode.get(i);
                WeatherData wd = node.traverse().readValueAs(WeatherData.class);
                results.add(wd);
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getWeatherDataByRequestId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherDataByRequestId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Business logic for processing WeatherRequest entity
    private void processWeatherRequest(String technicalId, WeatherRequest request) {
        log.info("Starting processWeatherRequest for technicalId: {}", technicalId);

        // Basic validation to ensure either cityName or lat/long present
        if ((request.getCityName() == null || request.getCityName().isBlank()) &&
                (request.getLatitude() == null || request.getLongitude() == null)) {
            log.error("WeatherRequest {} missing location information", technicalId);
            return;
        }

        // Call MSC GeoMet API to fetch weather data
        // For demonstration, using dummy data instead of real API call

        // Simulated weather data points for current or forecast
        List<WeatherData> fetchedData = new ArrayList<>();

        if ("CURRENT".equalsIgnoreCase(request.getRequestType()) || "FORECAST".equalsIgnoreCase(request.getRequestType())) {
            WeatherData data = new WeatherData();
            data.setWeatherRequestId(technicalId);
            data.setDataType(request.getRequestType().toUpperCase());
            data.setTemperature(20.0 + Math.random() * 10); // random temp between 20-30
            data.setHumidity(50.0 + Math.random() * 50); // random humidity 50-100%
            data.setWindSpeed(1.0 + Math.random() * 10); // random wind speed
            data.setPrecipitation(Math.random() * 5); // random precip
            data.setObservationTime(Instant.now());
            if (!data.isValid()) {
                log.error("Generated WeatherData invalid for request {}", technicalId);
                return;
            }
            fetchedData.add(data);
        } else {
            log.error("Unsupported requestType {} for WeatherRequest {}", request.getRequestType(), technicalId);
            return;
        }

        // Save fetched WeatherData entities via entityService
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    WeatherData.ENTITY_NAME,
                    ENTITY_VERSION,
                    fetchedData
            );
            List<UUID> ids = idsFuture.get();
            for (UUID id : ids) {
                log.info("Stored WeatherData with technicalId: {}", id.toString());
            }
        } catch (Exception e) {
            log.error("Error storing WeatherData for WeatherRequest {}: {}", technicalId, e.getMessage());
        }

        log.info("Completed processWeatherRequest for technicalId: {}", technicalId);
    }
}