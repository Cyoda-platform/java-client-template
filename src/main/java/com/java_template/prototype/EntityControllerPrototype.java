package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.WeatherRequest;
import com.java_template.application.entity.WeatherData;
import java.time.Instant;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, WeatherRequest> weatherRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong weatherRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, WeatherData> weatherDataCache = new ConcurrentHashMap<>();
    private final AtomicLong weatherDataIdCounter = new AtomicLong(1);

    // POST endpoint to create WeatherRequest - orchestration entity
    @PostMapping("/weather-requests")
    public ResponseEntity<Map<String, String>> createWeatherRequest(@RequestBody WeatherRequest request) {
        if (!request.isValid()) {
            log.error("Invalid WeatherRequest received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "WR-" + weatherRequestIdCounter.getAndIncrement();
        request.setRequestTimestamp(Instant.now());
        weatherRequestCache.put(technicalId, request);
        log.info("WeatherRequest created with technicalId: {}", technicalId);

        try {
            processWeatherRequest(technicalId, request);
        } catch (Exception e) {
            log.error("Error processing WeatherRequest {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET endpoint to retrieve WeatherRequest by technicalId
    @GetMapping("/weather-requests/{id}")
    public ResponseEntity<WeatherRequest> getWeatherRequest(@PathVariable("id") String id) {
        WeatherRequest request = weatherRequestCache.get(id);
        if (request == null) {
            log.error("WeatherRequest not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(request);
    }

    // GET endpoint to retrieve WeatherData by technicalId
    @GetMapping("/weather-data/{id}")
    public ResponseEntity<WeatherData> getWeatherData(@PathVariable("id") String id) {
        WeatherData data = weatherDataCache.get(id);
        if (data == null) {
            log.error("WeatherData not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(data);
    }

    // Optional GET endpoint to retrieve weather data by weatherRequestId filter
    @GetMapping("/weather-data")
    public ResponseEntity<List<WeatherData>> getWeatherDataByRequestId(@RequestParam(value = "weatherRequestId", required = false) String weatherRequestId) {
        List<WeatherData> results = new ArrayList<>();
        for (WeatherData data : weatherDataCache.values()) {
            if (weatherRequestId == null || data.getWeatherRequestId().equals(weatherRequestId)) {
                results.add(data);
            }
        }
        return ResponseEntity.ok(results);
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

        // Save fetched WeatherData entities to cache with generated technicalIds
        for (WeatherData wd : fetchedData) {
            String dataId = "WD-" + weatherDataIdCounter.getAndIncrement();
            weatherDataCache.put(dataId, wd);
            log.info("Stored WeatherData with technicalId: {}", dataId);
        }

        log.info("Completed processWeatherRequest for technicalId: {}", technicalId);
    }
}