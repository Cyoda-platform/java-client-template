package com.java_template.application.controller.searchfilter.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/searchfilters")
@Tag(name = "SearchFilter", description = "SearchFilter entity operations (version 1)")
public class SearchFilterController {

    private static final Logger logger = LoggerFactory.getLogger(SearchFilterController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchFilterController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create SearchFilter", description = "Create a new SearchFilter. This controller only proxies to the entity service. Business logic is implemented in workflows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateSearchFilterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSearchFilter(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchFilter create request", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSearchFilterRequest.class)))
            @RequestBody CreateSearchFilterRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // convert request DTO to ObjectNode for the EntityService (controller must be a proxy only)
            ObjectNode entityNode = objectMapper.convertValue(request, ObjectNode.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    SearchFilter.ENTITY_NAME,
                    String.valueOf(SearchFilter.ENTITY_VERSION),
                    entityNode
            );

            UUID technicalId = idFuture.get();

            CreateSearchFilterResponse resp = new CreateSearchFilterResponse();
            resp.setTechnicalId(technicalId.toString());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSearchFilter: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during createSearchFilter: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during createSearchFilter: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during createSearchFilter", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createSearchFilter", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error during createSearchFilter", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get SearchFilter by technicalId", description = "Retrieve a SearchFilter by technicalId. This controller only proxies to the entity service.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SearchFilterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSearchFilterById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalIdStr) {
        try {
            if (technicalIdStr == null || technicalIdStr.isBlank()) {
                throw new IllegalArgumentException("technicalId path parameter is required");
            }

            UUID technicalId = UUID.fromString(technicalIdStr);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    SearchFilter.ENTITY_NAME,
                    String.valueOf(SearchFilter.ENTITY_VERSION),
                    technicalId
            );

            ObjectNode item = itemFuture.get();
            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("SearchFilter not found");
            }

            // Ensure technicalId is present in the response payload
            item.put("technicalId", technicalId.toString());

            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSearchFilterById: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("SearchFilter not found: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument during getSearchFilterById: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException during getSearchFilterById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getSearchFilterById", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error during getSearchFilterById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateSearchFilterRequest", description = "Payload to create a SearchFilter")
    public static class CreateSearchFilterRequest {
        @Schema(description = "Business id of the filter (optional)", example = "sf-1")
        private String id;

        @Schema(description = "Owner user id", example = "user-123", required = true)
        private String user_id;

        @Schema(description = "Filter name", example = "Nearby Puppies")
        private String name;

        @Schema(description = "Species", example = "dog")
        private String species;

        @Schema(description = "Breeds", example = "[\"beagle\"]")
        private java.util.List<String> breeds;

        @Schema(description = "Minimum age", example = "0")
        private Integer age_min;

        @Schema(description = "Maximum age", example = "24")
        private Integer age_max;

        @Schema(description = "Age unit preference", example = "months")
        private String age_unit_preference;

        @Schema(description = "Sizes", example = "[\"small\"]")
        private java.util.List<String> size;

        @Schema(description = "Sex", example = "M")
        private String sex;

        @Schema(description = "Location center for radius searches")
        private LocationCenter location_center;

        @Schema(description = "Search radius in kilometers", example = "30")
        private Double radius_km;

        @Schema(description = "Vaccination required", example = "true")
        private Boolean vaccination_required;

        @Schema(description = "Temperament tags", example = "[\"playful\"]")
        private java.util.List<String> temperament_tags;

        @Schema(description = "Sort by field", example = "distance")
        private String sort_by;

        @Schema(description = "Page size", example = "20")
        private Integer page_size;
    }

    @Data
    @Schema(name = "CreateSearchFilterResponse", description = "Response after creating a SearchFilter")
    public static class CreateSearchFilterResponse {
        @Schema(description = "Technical id of the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "SearchFilterResponse", description = "SearchFilter response payload")
    public static class SearchFilterResponse {
        @Schema(description = "Technical id of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Business id", example = "sf-1")
        private String id;

        @Schema(description = "Owner user id", example = "user-123")
        private String user_id;

        @Schema(description = "Filter name", example = "Nearby Puppies")
        private String name;

        @Schema(description = "Species", example = "dog")
        private String species;

        @Schema(description = "Breeds")
        private java.util.List<String> breeds;

        @Schema(description = "Minimum age")
        private Integer age_min;

        @Schema(description = "Maximum age")
        private Integer age_max;

        @Schema(description = "Age unit preference")
        private String age_unit_preference;

        @Schema(description = "Sizes")
        private java.util.List<String> size;

        @Schema(description = "Sex")
        private String sex;

        @Schema(description = "Location center")
        private LocationCenter location_center;

        @Schema(description = "Search radius in km")
        private Double radius_km;

        @Schema(description = "Vaccination required")
        private Boolean vaccination_required;

        @Schema(description = "Temperament tags")
        private java.util.List<String> temperament_tags;

        @Schema(description = "Sort by")
        private String sort_by;

        @Schema(description = "Page size")
        private Integer page_size;

        @Schema(description = "Creation timestamp (ISO)", example = "2023-01-01T12:00:00Z")
        private String created_at;

        @Schema(description = "Is active")
        private Boolean is_active;
    }

    @Data
    @Schema(name = "LocationCenter", description = "Location center payload")
    public static class LocationCenter {
        @Schema(description = "Latitude", example = "52.1")
        private Double lat;

        @Schema(description = "Longitude", example = "5.1")
        private Double lon;

        @Schema(description = "City", example = "City")
        private String city;
    }

    // Placeholder import reference to the SearchFilter entity to ensure compile-time linkage.
    // The entity must exist under the specified package with ENTITY_NAME and ENTITY_VERSION fields.
    //noinspection unused
    private static class SearchFilter {
        public static final String ENTITY_NAME = "SearchFilter";
        public static final int ENTITY_VERSION = 1;
    }
}