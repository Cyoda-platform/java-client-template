package com.java_template.application.controller.weatherobservation.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.weatherobservation.version_1.WeatherObservation;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/weather-observations")
@Tag(name = "WeatherObservation", description = "Controller for WeatherObservation entity (proxy to EntityService)")
public class WeatherObservationController {

    private static final Logger logger = LoggerFactory.getLogger(WeatherObservationController.class);

    private final EntityService entityService;

    public WeatherObservationController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create WeatherObservation", description = "Create a single WeatherObservation entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "WeatherObservation to create", required = true,
                    content = @Content(schema = @Schema(implementation = CreateWeatherObservationRequest.class)))
            @RequestBody CreateWeatherObservationRequest request
    ) {
        try {
            WeatherObservation entity = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    entity
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new CreateResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for create: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on create", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating WeatherObservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Bulk create WeatherObservations", description = "Create multiple WeatherObservation entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkCreate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of WeatherObservations to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateWeatherObservationRequest.class))))
            @RequestBody List<CreateWeatherObservationRequest> requests
    ) {
        try {
            List<WeatherObservation> entities = new ArrayList<>();
            for (CreateWeatherObservationRequest r : requests) {
                entities.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<CreateResponse> responses = new ArrayList<>();
            for (UUID id : ids) {
                responses.add(new CreateResponse(id.toString()));
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk create request: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on bulk create", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating WeatherObservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on bulk create", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get WeatherObservation by technicalId", description = "Retrieve a WeatherObservation by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = WeatherObservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Item not found");
            }
            WeatherObservationResponse resp = fromObjectNode(node, technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getById: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on getById", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting WeatherObservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on getById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all WeatherObservations", description = "Retrieve all WeatherObservations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeatherObservationResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            List<WeatherObservationResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    String technicalId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                    responses.add(fromObjectNode(node, technicalId));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("ExecutionException on getAll", e);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all WeatherObservations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on getAll", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search WeatherObservations by condition", description = "Retrieve WeatherObservations filtered by a simple condition group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = WeatherObservationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchConditionRequest with grouping and conditions", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = filteredItemsFuture.get();
            List<WeatherObservationResponse> responses = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    ObjectNode node = (ObjectNode) arr.get(i);
                    String technicalId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                    responses.add(fromObjectNode(node, technicalId));
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on searchByCondition", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching WeatherObservations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on searchByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update WeatherObservation", description = "Update an existing WeatherObservation by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> update(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated WeatherObservation payload", required = true,
                    content = @Content(schema = @Schema(implementation = UpdateWeatherObservationRequest.class)))
            @RequestBody UpdateWeatherObservationRequest request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            WeatherObservation entity = toEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    id,
                    entity
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for update: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on update", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating WeatherObservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on update", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete WeatherObservation", description = "Delete a WeatherObservation by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> delete(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    WeatherObservation.ENTITY_NAME,
                    String.valueOf(WeatherObservation.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for delete: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException on delete", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause == null ? e.getMessage() : cause.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting WeatherObservation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error on delete", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper: convert request DTO to entity
    private WeatherObservation toEntity(CreateWeatherObservationRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is null");
        WeatherObservation e = new WeatherObservation();
        e.setObservationId(req.getObservationId());
        e.setLocationId(req.getLocationId());
        e.setRawSourceId(req.getRawSourceId());
        e.setHumidity(req.getHumidity());
        e.setPrecipitation(req.getPrecipitation());
        e.setTemperature(req.getTemperature());
        e.setWindSpeed(req.getWindSpeed());
        e.setTimestamp(req.getTimestamp());
        e.setProcessed(req.getProcessed());
        return e;
    }

    private WeatherObservation toEntity(UpdateWeatherObservationRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is null");
        WeatherObservation e = new WeatherObservation();
        e.setObservationId(req.getObservationId());
        e.setLocationId(req.getLocationId());
        e.setRawSourceId(req.getRawSourceId());
        e.setHumidity(req.getHumidity());
        e.setPrecipitation(req.getPrecipitation());
        e.setTemperature(req.getTemperature());
        e.setWindSpeed(req.getWindSpeed());
        e.setTimestamp(req.getTimestamp());
        e.setProcessed(req.getProcessed());
        return e;
    }

    // Helper: map ObjectNode to response DTO
    private WeatherObservationResponse fromObjectNode(ObjectNode node, String technicalId) {
        WeatherObservationResponse r = new WeatherObservationResponse();
        r.setTechnicalId(technicalId);
        if (node == null) return r;
        if (node.has("observationId") && !node.get("observationId").isNull()) r.setObservationId(node.get("observationId").asText(null));
        if (node.has("locationId") && !node.get("locationId").isNull()) r.setLocationId(node.get("locationId").asText(null));
        if (node.has("rawSourceId") && !node.get("rawSourceId").isNull()) r.setRawSourceId(node.get("rawSourceId").asText(null));
        if (node.has("humidity") && !node.get("humidity").isNull()) r.setHumidity(node.get("humidity").isInt() ? node.get("humidity").asInt() : (node.get("humidity").isNumber() ? node.get("humidity").asInt() : null));
        if (node.has("precipitation") && !node.get("precipitation").isNull()) r.setPrecipitation(node.get("precipitation").isNumber() ? node.get("precipitation").asDouble() : null);
        if (node.has("temperature") && !node.get("temperature").isNull()) r.setTemperature(node.get("temperature").isNumber() ? node.get("temperature").asDouble() : null);
        if (node.has("windSpeed") && !node.get("windSpeed").isNull()) r.setWindSpeed(node.get("windSpeed").isNumber() ? node.get("windSpeed").asDouble() : null);
        if (node.has("timestamp") && !node.get("timestamp").isNull()) r.setTimestamp(node.get("timestamp").asText(null));
        if (node.has("processed") && !node.get("processed").isNull()) r.setProcessed(node.get("processed").asBoolean());
        // also, if node contains technicalId field, prefer it
        if (node.has("technicalId") && !node.get("technicalId").isNull()) r.setTechnicalId(node.get("technicalId").asText(null));
        return r;
    }

    // Static DTOs

    @Data
    @Schema(name = "CreateWeatherObservationRequest", description = "Payload to create a WeatherObservation")
    public static class CreateWeatherObservationRequest {
        @Schema(description = "Domain observation id", example = "OBS-20250826-1")
        private String observationId;

        @Schema(description = "Location domain id", example = "LOC123")
        private String locationId;

        @Schema(description = "ISO-8601 timestamp of observation", example = "2025-08-26T09:45:00Z")
        private String timestamp;

        @Schema(description = "Temperature in Celsius", example = "12.3")
        private Double temperature;

        @Schema(description = "Humidity percentage", example = "78")
        private Integer humidity;

        @Schema(description = "Wind speed in m/s", example = "3.2")
        private Double windSpeed;

        @Schema(description = "Precipitation in mm", example = "0.0")
        private Double precipitation;

        @Schema(description = "Source record id", example = "raw-0001")
        private String rawSourceId;

        @Schema(description = "Processed flag", example = "false")
        private Boolean processed;
    }

    @Data
    @Schema(name = "UpdateWeatherObservationRequest", description = "Payload to update a WeatherObservation")
    public static class UpdateWeatherObservationRequest {
        @Schema(description = "Domain observation id", example = "OBS-20250826-1")
        private String observationId;

        @Schema(description = "Location domain id", example = "LOC123")
        private String locationId;

        @Schema(description = "ISO-8601 timestamp of observation", example = "2025-08-26T09:45:00Z")
        private String timestamp;

        @Schema(description = "Temperature in Celsius", example = "12.3")
        private Double temperature;

        @Schema(description = "Humidity percentage", example = "78")
        private Integer humidity;

        @Schema(description = "Wind speed in m/s", example = "3.2")
        private Double windSpeed;

        @Schema(description = "Precipitation in mm", example = "0.0")
        private Double precipitation;

        @Schema(description = "Source record id", example = "raw-0001")
        private String rawSourceId;

        @Schema(description = "Processed flag", example = "true")
        private Boolean processed;
    }

    @Data
    @Schema(name = "WeatherObservationResponse", description = "WeatherObservation returned by the API")
    public static class WeatherObservationResponse {
        @Schema(description = "Technical ID of the entity (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;

        @Schema(description = "Domain observation id", example = "OBS-20250826-1")
        private String observationId;

        @Schema(description = "Location domain id", example = "LOC123")
        private String locationId;

        @Schema(description = "ISO-8601 timestamp of observation", example = "2025-08-26T09:45:00Z")
        private String timestamp;

        @Schema(description = "Temperature in Celsius", example = "12.3")
        private Double temperature;

        @Schema(description = "Humidity percentage", example = "78")
        private Integer humidity;

        @Schema(description = "Wind speed in m/s", example = "3.2")
        private Double windSpeed;

        @Schema(description = "Precipitation in mm", example = "0.0")
        private Double precipitation;

        @Schema(description = "Source record id", example = "raw-0001")
        private String rawSourceId;

        @Schema(description = "Processed flag", example = "true")
        private Boolean processed;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Generic response returning technicalId")
    public static class CreateResponse {
        @Schema(description = "Technical ID of the created/updated/deleted entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;

        public CreateResponse() {}

        public CreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}