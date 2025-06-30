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

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                initialEntity
        );

        UUID requestId = idFuture.join();

        FetchResponse response = new FetchResponse(requestId.toString(), "success", Instant.now().toString());
        return ResponseEntity.ok(response);
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