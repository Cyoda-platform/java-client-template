package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/weather")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST endpoint to create WeatherRequest - orchestration entity
    @PostMapping("/weather-requests")
    public ResponseEntity<Map<String, String>> createWeatherRequest(@Valid @RequestBody WeatherRequest request) throws JsonProcessingException {
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
                // processWeatherRequest method extracted to workflow prototype
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
        } catch (ExecutionException ee) {
            log.error("ExecutionException in createWeatherRequest: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("InterruptedException in createWeatherRequest: {}", ie.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in createWeatherRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET endpoint to retrieve WeatherRequest by technicalId
    @GetMapping("/weather-requests/{id}")
    public ResponseEntity<WeatherRequest> getWeatherRequest(@PathVariable("id") @NotBlank String id) throws JsonProcessingException {
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
            WeatherRequest request = objectMapper.treeToValue(node, WeatherRequest.class);
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
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("InterruptedException in getWeatherRequest: {}", ie.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET endpoint to retrieve WeatherData by technicalId
    @GetMapping("/weather-data/{id}")
    public ResponseEntity<WeatherData> getWeatherData(@PathVariable("id") @NotBlank String id) throws JsonProcessingException {
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
            WeatherData data = objectMapper.treeToValue(node, WeatherData.class);
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
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("InterruptedException in getWeatherData: {}", ie.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Optional GET endpoint to retrieve weather data by weatherRequestId filter
    @GetMapping("/weather-data")
    public ResponseEntity<List<WeatherData>> getWeatherDataByRequestId(@RequestParam(value = "weatherRequestId", required = false) String weatherRequestId) throws JsonProcessingException {
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
                WeatherData wd = objectMapper.treeToValue(node, WeatherData.class);
                results.add(wd);
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getWeatherDataByRequestId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            log.error("ExecutionException in getWeatherDataByRequestId: {}", ee.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("InterruptedException in getWeatherDataByRequestId: {}", ie.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getWeatherDataByRequestId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}