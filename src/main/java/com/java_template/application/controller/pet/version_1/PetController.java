package com.java_template.application.controller.pet.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.version_1.Pet;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/pets")
@Tag(name = "Pet", description = "Operations for Pet entity (read-only)")
public class PetController {
    private static final Logger logger = LoggerFactory.getLogger(PetController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PetController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get Pet by technicalId", description = "Retrieve a Pet entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPetById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();

            PetResponse response = objectMapper.treeToValue(node, PetResponse.class);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getPetById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.info("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in getPetById: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPetById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting pet", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getPetById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Pets", description = "Retrieve all Pet entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PetResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPets() {
        try {
            ArrayNode arr = entityService.getItems(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION)
            ).get();

            List<PetResponse> list = objectMapper.convertValue(arr, new TypeReference<List<PetResponse>>() {});
            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in listPets: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Illegal argument from service in listPets: {}", cause.getMessage());
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listPets", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing pets", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listPets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads
    @Data
    @Schema(name = "PetResponse", description = "Pet response payload")
    public static class PetResponse {
        @Schema(description = "Technical id", example = "pet_123")
        private String id;

        @Schema(description = "Source id from external system", example = "ps_987")
        private String sourceId;

        @Schema(description = "Pet name", example = "Mittens")
        private String name;

        @Schema(description = "Species", example = "cat")
        private String species;

        @Schema(description = "Breed description", example = "Tabby")
        private String breed;

        @Schema(description = "Age in years or months", example = "2")
        private Integer age;

        @Schema(description = "Status (available, adopted, foster)", example = "available")
        private String status;

        @Schema(description = "Tags", example = "[\"kitten\",\"friendly\"]")
        private List<String> tags;

        @Schema(description = "Image URLs", example = "[\"https://...\"]")
        private List<String> images;

        @Schema(description = "Description", example = "Friendly tabby kitten")
        private String description;

        @Schema(description = "Timestamp from source", example = "2025-08-25T12:00:00Z")
        private String sourceUpdatedAt;

        @Schema(description = "Record created timestamp", example = "2025-08-25T12:01:00Z")
        private String createdAt;

        @Schema(description = "Record updated timestamp", example = "2025-08-25T12:01:00Z")
        private String updatedAt;
    }
}