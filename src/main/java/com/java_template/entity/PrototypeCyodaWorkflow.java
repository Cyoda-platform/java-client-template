Certainly! The goal is to move all asynchronous or background logic from the controller endpoint into the workflow function `process{EntityName}` which is invoked asynchronously before persisting the entity. This keeps controllers lean and moves logic closer to the entity lifecycle, making the codebase more robust and maintainable.

---

### What we can move:

- The async task that fetches weather data from the external API and updates the WeatherResult entity.
- Updating the WeatherJob status after the fetch completes (success or failure).
- Any logic that mutates the entity state before persistence.

---

### Constraints:

- The workflow function takes only the entity (an `ObjectNode` representing the entity data).
- It returns a `CompletableFuture<ObjectNode>`.
- Within the workflow function, you **cannot** modify or update the same entityModel (no `entityService.addItem/updateItem/deleteItem` on the same entity).
- You **can** read or write other entities of different entityModel.
- You can modify the passed entity node directly (e.g. `entity.put(...)`), and this will be persisted.
- The workflow is asynchronous, so you can perform async calls inside.

---

### Approach:

- Convert the entity parameter to `ObjectNode` in `processWeatherJob`.
- Move the external API call and creating/updating the `WeatherResult` entity inside `processWeatherJob`.
- Update the `status` field on the `WeatherJob` entity node (passed entity).
- Use `entityService` to add/update other entities (like `WeatherResult`) but **not** `WeatherJob`.
- Remove async fetch and update logic from the controller.
- The controller will just call `addItem` with the workflow function and immediately return accepted.

---

### Here is the fully updated Java code with the workflow function implementing all async logic:

```java
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
import java.util.function.Function;

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

    /**
     * Workflow function for WeatherJob entity.
     * This function asynchronously fetches weather data,
     * creates/updates WeatherResult entity,
     * and updates the status of the WeatherJob entity node.
     *
     * @param entityObjNode entity as ObjectNode, representing WeatherJob entity data
     * @return CompletableFuture<ObjectNode> with possibly updated WeatherJob entity
     */
    protected CompletableFuture<ObjectNode> processWeatherJob(ObjectNode entityObjNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Workflow: processWeatherJob started for entity: {}", entityObjNode);

                // Extract necessary fields from entity node
                Double latitude = entityObjNode.hasNonNull("latitude") ? entityObjNode.get("latitude").asDouble() : null;
                Double longitude = entityObjNode.hasNonNull("longitude") ? entityObjNode.get("longitude").asDouble() : null;
                int forecastDays = entityObjNode.hasNonNull("forecastDays") ? entityObjNode.get("forecastDays").asInt() : 1;
                JsonNode parametersNode = entityObjNode.get("parameters");
                String[] parameters;
                if (parametersNode != null && parametersNode.isArray()) {
                    parameters = new String[parametersNode.size()];
                    for (int i = 0; i < parametersNode.size(); i++) {
                        parameters[i] = parametersNode.get(i).asText();
                    }
                } else {
                    parameters = new String[0];
                }

                // Build Open-Meteo API URL
                String url = buildOpenMeteoUrl(latitude, longitude, parameters, forecastDays);
                log.info("Workflow: Calling Open-Meteo API: {}", url);

                // Call external weather API
                String response = restTemplate.getForObject(URI.create(url), String.class);

                if (!StringUtils.hasText(response)) {
                    throw new IllegalStateException("Empty response from Open-Meteo API");
                }

                JsonNode weatherData = objectMapper.readTree(response);

                // Prepare WeatherResult entity as ObjectNode
                ObjectNode weatherResultNode = objectMapper.createObjectNode();
                UUID technicalId = UUID.randomUUID(); // New ID for WeatherResult

                weatherResultNode.put("technicalId", technicalId.toString());
                weatherResultNode.put("requestId", entityObjNode.hasNonNull("requestId") ? entityObjNode.get("requestId").asText() : technicalId.toString());
                weatherResultNode.put("latitude", latitude);
                weatherResultNode.put("longitude", longitude);
                weatherResultNode.set("parameters", weatherData);
                weatherResultNode.put("forecastDays", forecastDays);
                weatherResultNode.put("timestamp", Instant.now().toString());

                // Persist WeatherResult entity (different entityModel)
                entityService.addItem("WeatherResult", ENTITY_VERSION, weatherResultNode).join();

                // Update status in current WeatherJob entity node to 'completed'
                entityObjNode.put("status", "completed");
                entityObjNode.put("updatedAt", Instant.now().toString());

                log.info("Workflow: processWeatherJob completed successfully");

            } catch (Exception ex) {
                log.error("Workflow: processWeatherJob failed", ex);

                // Update status in current WeatherJob entity node to 'failed'
                entityObjNode.put("status", "failed");
                entityObjNode.put("updatedAt", Instant.now().toString());
            }

            // Return possibly updated WeatherJob entity node to be persisted
            return entityObjNode;
        });
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchResponse>> fetchWeatherData(@RequestBody @Valid FetchRequest request) {
        log.info("Received weather fetch request: {}", request);

        // Prepare initial WeatherJob entity as ObjectNode
        ObjectNode jobNode = objectMapper.createObjectNode();
        jobNode.put("status", "processing");
        jobNode.put("requestedAt", Instant.now().toString());
        jobNode.put("latitude", request.getLatitude());
        jobNode.put("longitude", request.getLongitude());
        jobNode.putArray("parameters").addAll(objectMapper.valueToTree(request.getParameters()));
        jobNode.put("forecastDays", request.getForecastDays());

        // Pass workflow function processWeatherJob as parameter to addItem
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

        return String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&daily=%s&timezone=auto&forecast_days=%d",
                latitude,
                longitude,
                dailyParams,
                forecastDays
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
```

---

### Summary of changes:

- The async weather fetching and result entity creation is moved from controller to `processWeatherJob`.
- `processWeatherJob` is the workflow function passed to `entityService.addItem`.
- `processWeatherJob` accepts the `WeatherJob` entity as `ObjectNode`, modifies it to update status (`processing` -> `completed` or `failed`), and persists a new `WeatherResult` entity.
- Controller now just calls `addItem` with the workflow function and returns immediately.
- Removed all async fetch/update logic from controller.
- Used only `entityService.addItem` on different entityModel (`WeatherResult`) inside workflow function.
- Updated entity state (`status`, `updatedAt`) inside the workflow function directly on the passed entity node.

---

This fully leverages the new workflow mechanism, cleans up the controller, and moves all async tasks and entity state management into the process workflow function as required.