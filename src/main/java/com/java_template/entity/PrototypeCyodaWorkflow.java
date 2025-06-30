package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

        // Create initial entity ObjectNode with minimal information and "processing" status
        ObjectNode initialEntity = objectMapper.createObjectNode();
        initialEntity.put("status", "processing");
        initialEntity.put("fetchedAt", Instant.now().toString());
        initialEntity.put("latitude", request.getLatitude());
        initialEntity.put("longitude", request.getLongitude());
        initialEntity.put("startDate", request.getStartDate());
        initialEntity.put("endDate", request.getEndDate());
        initialEntity.putPOJO("parameters", request.getParameters());
        initialEntity.putNull("data");

        // Workflow function to process the entity asynchronously before persistence
        Function<ObjectNode, CompletableFuture<ObjectNode>> workflow = this::processWeatherRequest;

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                initialEntity,
                workflow
        );

        UUID requestId = idFuture.join();

        FetchResponse response = new FetchResponse(requestId.toString(), "success", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Workflow function to asynchronously fetch weather data and update entity before persistence.
     * This function is called asynchronously by entityService.addItem.
     *
     * @param entity ObjectNode representing the WeatherRequest entity
     * @return CompletableFuture of the modified entity (ObjectNode)
     */
    private CompletableFuture<ObjectNode> processWeatherRequest(ObjectNode entity) {
        logger.info("Workflow started: processWeatherRequest for entity id={}", entity.path("id").asText(null));

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate mandatory fields are present and correctly typed
                if (!entity.hasNonNull("latitude") || !entity.hasNonNull("longitude")) {
                    logger.error("Entity missing latitude or longitude");
                    entity.put("status", "failed");
                    entity.put("fetchedAt", Instant.now().toString());
                    entity.putNull("data");
                    return entity;
                }
                if (!entity.hasNonNull("startDate") || !entity.hasNonNull("endDate")) {
                    logger.error("Entity missing startDate or endDate");
                    entity.put("status", "failed");
                    entity.put("fetchedAt", Instant.now().toString());
                    entity.putNull("data");
                    return entity;
                }
                if (!entity.hasNonNull("parameters") || !entity.get("parameters").isArray() || entity.get("parameters").size() == 0) {
                    logger.error("Entity missing parameters or parameters array is empty");
                    entity.put("status", "failed");
                    entity.put("fetchedAt", Instant.now().toString());
                    entity.putNull("data");
                    return entity;
                }

                double latitude = entity.get("latitude").asDouble();
                double longitude = entity.get("longitude").asDouble();
                String startDate = entity.get("startDate").asText();
                String endDate = entity.get("endDate").asText();

                List<String> parameters = objectMapper.convertValue(
                        entity.get("parameters"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );

                // Build URL for external API call
                String baseUrl = "https://api.open-meteo.com/v1/forecast";
                String paramsCsv = String.join(",", parameters);

                String url = String.format("%s?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=%s",
                        baseUrl,
                        URLEncoder.encode(String.valueOf(latitude), StandardCharsets.UTF_8),
                        URLEncoder.encode(String.valueOf(longitude), StandardCharsets.UTF_8),
                        URLEncoder.encode(startDate, StandardCharsets.UTF_8),
                        URLEncoder.encode(endDate, StandardCharsets.UTF_8),
                        URLEncoder.encode(paramsCsv, StandardCharsets.UTF_8)
                );

                logger.info("Calling external Open-Meteo API: {}", url);

                String responseStr = restTemplate.getForObject(URI.create(url), String.class);
                if (responseStr == null) {
                    logger.error("Empty response from Open-Meteo API");
                    entity.put("status", "failed");
                    entity.put("fetchedAt", Instant.now().toString());
                    entity.putNull("data");
                    return entity;
                }

                JsonNode rootNode = objectMapper.readTree(responseStr);
                JsonNode hourlyNode = rootNode.path("hourly");

                // Prepare data map to put into entity.data
                ObjectNode dataNode = objectMapper.createObjectNode();

                for (String param : parameters) {
                    JsonNode paramNode = hourlyNode.path(param);
                    if (paramNode.isMissingNode()) {
                        logger.warn("Parameter '{}' not found in API response", param);
                        dataNode.putNull(param);
                    } else {
                        dataNode.set(param, paramNode);
                    }
                }

                // Update entity fields directly
                entity.put("status", "completed");
                entity.put("fetchedAt", Instant.now().toString());
                entity.set("data", dataNode);

                logger.info("Workflow completed successfully for entity");

            } catch (Exception e) {
                logger.error("Workflow failed during processing weather data", e);
                entity.put("status", "failed");
                entity.put("fetchedAt", Instant.now().toString());
                entity.putNull("data");
            }

            // Return the modified entity for persistence
            return entity;
        });
    }

    @GetMapping("/result/{requestId}")
    public ResponseEntity<ObjectNode> getWeatherResult(@PathVariable @NotBlank String requestId) {
        logger.info("Received GET result request for requestId={}", requestId);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(requestId);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid UUID format for requestId={}", requestId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid requestId format");
        }

        CompletableFuture<JsonNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );

        JsonNode itemNode = itemFuture.join();
        if (itemNode == null || itemNode.isNull()) {
            logger.error("No data found for requestId={}", requestId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No data found for requestId");
        }

        if (!(itemNode instanceof ObjectNode)) {
            logger.error("Stored entity is not an ObjectNode as expected");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected stored entity format");
        }

        ObjectNode stored = (ObjectNode) itemNode;
        String status = stored.has("status") ? stored.get("status").asText() : "unknown";

        if ("processing".equalsIgnoreCase(status)) {
            // Return 202 Accepted with current entity state
            return ResponseEntity.status(202).body(stored);
        }

        return ResponseEntity.ok(stored);
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
}