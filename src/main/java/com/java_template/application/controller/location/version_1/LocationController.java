package com.java_template.application.controller.location.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.location.version_1.Location;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/locations")
@Tag(name = "Location Controller", description = "CRUD proxy endpoints for Location entity (version 1)")
public class LocationController {

    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public LocationController(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Operation(summary = "Create Location", description = "Create a single Location entity. Returns the technicalId only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> createLocation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Location payload", required = true,
                    content = @Content(schema = @Schema(implementation = LocationCreateRequest.class)))
            @RequestBody LocationCreateRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Location data = toLocationEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createLocation: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLocation", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createLocation", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createLocation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Locations", description = "Create multiple Location entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createLocationsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of Location payloads", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LocationCreateRequest.class))))
            @RequestBody List<LocationCreateRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");

            List<Location> data = new ArrayList<>();
            for (LocationCreateRequest r : requests) {
                data.add(toLocationEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    data
            );
            List<UUID> ids = idsFuture.get();
            List<TechnicalIdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                TechnicalIdResponse t = new TechnicalIdResponse();
                t.setTechnicalId(id.toString());
                resp.add(t);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createLocationsBulk: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createLocationsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createLocationsBulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createLocationsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Location by technicalId", description = "Retrieve a Location by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLocationById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Location not found");
            }
            Location entity = objectMapper.treeToValue(node, Location.class);
            LocationResponse resp = toLocationResponse(technicalId, entity);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getLocationById: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getLocationById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getLocationById", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getLocationById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Locations", description = "Retrieve all Location entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LocationResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> getAllLocations() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<LocationResponse> resp = new ArrayList<>();
            if (array != null) {
                for (com.fasterxml.jackson.databind.JsonNode n : array) {
                    Location entity = objectMapper.treeToValue(n, Location.class);
                    // technicalId may not be part of the returned node; set null for those cases
                    LocationResponse lr = toLocationResponse(null, entity);
                    resp.add(lr);
                }
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in getAllLocations", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllLocations", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getAllLocations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Locations by condition", description = "Search Locations using basic field conditions. Use SearchConditionRequest.group(...) and Condition.of(...) to build conditions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = LocationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchLocations(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            List<LocationResponse> resp = new ArrayList<>();
            if (array != null) {
                for (com.fasterxml.jackson.databind.JsonNode n : array) {
                    Location entity = objectMapper.treeToValue(n, Location.class);
                    resp.add(toLocationResponse(null, entity));
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchLocations: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchLocations", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during searchLocations", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchLocations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Location", description = "Update an existing Location entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateLocation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Location payload", required = true,
                    content = @Content(schema = @Schema(implementation = LocationCreateRequest.class)))
            @RequestBody LocationCreateRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Location data = toLocationEntity(request);

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );
            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateLocation: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateLocation", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateLocation", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateLocation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Location", description = "Delete a Location entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteLocation(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteLocation: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteLocation", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteLocation", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteLocation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- Helpers and DTO conversions ---

    private Location toLocationEntity(LocationCreateRequest req) {
        Location loc = new Location();
        loc.setLocationId(req.getLocationId());
        loc.setName(req.getName());
        loc.setRegion(req.getRegion());
        loc.setTimezone(req.getTimezone());
        loc.setActive(req.getActive());
        loc.setLatitude(req.getLatitude());
        loc.setLongitude(req.getLongitude());
        return loc;
    }

    private LocationResponse toLocationResponse(String technicalId, Location loc) {
        LocationResponse r = new LocationResponse();
        r.setTechnicalId(technicalId);
        if (loc != null) {
            r.setLocationId(loc.getLocationId());
            r.setName(loc.getName());
            r.setRegion(loc.getRegion());
            r.setTimezone(loc.getTimezone());
            r.setActive(loc.getActive());
            r.setLatitude(loc.getLatitude());
            r.setLongitude(loc.getLongitude());
        }
        return r;
    }

    // --- DTOs ---

    @Data
    @Schema(name = "LocationCreateRequest", description = "Request payload to create or update a Location")
    public static class LocationCreateRequest {
        @Schema(description = "Domain location id", example = "LOC123")
        private String locationId;

        @Schema(description = "Place name", example = "Station A")
        private String name;

        @Schema(description = "Latitude in decimal degrees", example = "59.1")
        private Double latitude;

        @Schema(description = "Longitude in decimal degrees", example = "18.0")
        private Double longitude;

        @Schema(description = "Region or country", example = "Stockholm")
        private String region;

        @Schema(description = "IANA timezone", example = "Europe/Stockholm")
        private String timezone;

        @Schema(description = "Monitoring enabled", example = "true")
        private Boolean active;
    }

    @Data
    @Schema(name = "LocationResponse", description = "Response payload for a Location")
    public static class LocationResponse {
        @Schema(description = "Technical ID of the entity", example = "loc-tech-001")
        private String technicalId;

        @Schema(description = "Domain location id", example = "LOC123")
        private String locationId;

        @Schema(description = "Place name", example = "Station A")
        private String name;

        @Schema(description = "Latitude in decimal degrees", example = "59.1")
        private Double latitude;

        @Schema(description = "Longitude in decimal degrees", example = "18.0")
        private Double longitude;

        @Schema(description = "Region or country", example = "Stockholm")
        private String region;

        @Schema(description = "IANA timezone", example = "Europe/Stockholm")
        private String timezone;

        @Schema(description = "Monitoring enabled", example = "true")
        private Boolean active;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response with technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "loc-tech-001")
        private String technicalId;
    }
}