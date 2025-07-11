package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.Pet;
import com.java_template.application.serializer.ProcessorSerializer;
import com.java_template.application.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

/**
 * Modern Pet Controller demonstrating advanced serializer usage.
 * Features a single endpoint for adding Pet entities with comprehensive
 * validation, business logic processing, and error handling using the
 * new Jackson serializer architecture.
 */
@RestController
@RequestMapping("/api/v1/pets")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        logger.info("Pet Controller initialized with modern serializer architecture");
    }

    private static final String ENTITY_PET = "pet";

    // ========================================
    // REQUEST/RESPONSE DTOs
    // ========================================

    /**
     * Request DTO for adding a new Pet entity.
     * Demonstrates comprehensive validation with business rules.
     */
    @Data
    public static class AddPetRequest {
        @NotNull(message = "Pet ID is required")
        @Positive(message = "Pet ID must be positive")
        private Long id;

        @NotBlank(message = "Pet name is required")
        @Size(min = 1, max = 100, message = "Pet name must be between 1 and 100 characters")
        private String name;

        @Pattern(regexp = "^(dog|cat|bird|fish|reptile|other)$",
                message = "Category must be one of: dog, cat, bird, fish, reptile, other")
        private String category;

        @Pattern(regexp = "^(available|pending|sold)$",
                message = "Status must be one of: available, pending, sold")
        private String status = "available"; // Default status

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<@NotBlank(message = "Tag cannot be blank") String> tags = new ArrayList<>();

        @Size(max = 20, message = "Maximum 20 photo URLs allowed")
        private List<@Pattern(regexp = "^https?://.+", message = "Must be a valid URL") String> photoUrls = new ArrayList<>();
    }

    /**
     * Response DTO for Pet operations.
     * Includes processing metadata and validation status.
     */
    @Data
    public static class PetResponse {
        private boolean success;
        private String message;
        private Pet pet;
        private ProcessingMetadata metadata;
        private ValidationStatus validation;
        private String error;
        private String errorDetails;

        @Data
        public static class ProcessingMetadata {
            private String processedAt;
            private String processedBy;
            private String requestId;
            private String entityId;
            private String processingVersion;
        }

        @Data
        public static class ValidationStatus {
            private boolean isValid;
            private boolean hasStatus;
            private int tagCount;
            private int photoUrlCount;
            private List<String> validationMessages;
        }
    }


    // ========================================
    // MODERN PET ENDPOINT
    // ========================================

    /**
     * Adds a new Pet entity with comprehensive validation and processing.
     * Demonstrates advanced usage of the Jackson serializer architecture including:
     * - Type-safe entity processing
     * - Business logic validation
     * - Enhanced response with metadata
     * - Comprehensive error handling
     * - Functional composition patterns
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<PetResponse>> addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Adding new Pet entity: id={}, name={}, category={}, status={}",
            request.getId(), request.getName(), request.getCategory(), request.getStatus());

        try {
            // Convert request to Pet entity
            Pet pet = convertRequestToPet(request);

            // Use serializer for advanced processing and validation
            return serializer.executeFunction(null, ser -> {
                try {
                    // Apply business logic and validation
                    Pet processedPet = applyPetBusinessLogic(pet);

                    // Add timestamp
                    processedPet.addLastModifiedTimestamp();

                    // Convert to JSON for storage
                    JsonNode petJson = objectMapper.valueToTree(processedPet);

                    // Store in entity service
                    return entityService.addItem(ENTITY_PET, ENTITY_VERSION, petJson)
                        .thenApply(entityId -> {
                            logger.info("Pet entity created successfully with ID: {}", entityId);

                            // Create enhanced response using serializer patterns
                            PetResponse response = createSuccessResponse(processedPet, entityId.toString());
                            return ResponseEntity.status(HttpStatus.CREATED).body(response);
                        })
                        .exceptionally(ex -> {
                            logger.error("Failed to store Pet entity", ex);
                            PetResponse errorResponse = createErrorResponse("STORAGE_ERROR",
                                "Failed to store Pet entity: " + ex.getMessage());
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                        });

                } catch (Exception e) {
                    logger.error("Error processing Pet entity", e);
                    PetResponse errorResponse = createErrorResponse("PROCESSING_ERROR",
                        "Failed to process Pet entity: " + e.getMessage());
                    return CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
                }
            });

        } catch (Exception e) {
            logger.error("Error in Pet endpoint", e);
            PetResponse errorResponse = createErrorResponse("REQUEST_ERROR",
                "Invalid Pet request: " + e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }
    }

    // ========================================
    // BUSINESS LOGIC & UTILITIES
    // ========================================

    /**
     * Converts AddPetRequest to Pet entity with validation.
     */
    private Pet convertRequestToPet(AddPetRequest request) {
        Pet pet = new Pet();
        pet.setId(request.getId());
        pet.setName(request.getName().trim());
        pet.setCategory(request.getCategory() != null ? request.getCategory().toLowerCase() : null);
        pet.setStatus(request.getStatus() != null ? request.getStatus().toLowerCase() : "available");

        // Clean and validate tags
        if (request.getTags() != null) {
            List<String> cleanedTags = request.getTags().stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .limit(10)
                .toList();
            pet.setTags(cleanedTags);
        }

        // Validate photo URLs
        if (request.getPhotoUrls() != null) {
            List<String> validUrls = request.getPhotoUrls().stream()
                .filter(this::isValidUrl)
                .limit(20)
                .toList();
            pet.setPhotoUrls(validUrls);
        }

        return pet;
    }

    /**
     * Applies comprehensive business logic to Pet entity.
     */
    private Pet applyPetBusinessLogic(Pet pet) {
        logger.debug("Applying business logic to Pet: {}", pet.getId());

        // Validate required fields
        if (pet.getId() == null || pet.getId() <= 0) {
            throw new IllegalArgumentException("Pet ID must be positive");
        }

        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Pet name is required");
        }

        // Normalize status
        if (pet.getStatus() != null) {
            pet.setStatus(pet.getStatus().toLowerCase());
            if (!Set.of("available", "pending", "sold").contains(pet.getStatus())) {
                pet.setStatus("available"); // Default to available
                logger.debug("Normalized invalid status to 'available' for Pet: {}", pet.getId());
            }
        }

        // Validate name length
        if (pet.getName().length() > 100) {
            pet.setName(pet.getName().substring(0, 100));
            logger.debug("Truncated Pet name to 100 characters for Pet: {}", pet.getId());
        }

        logger.debug("Completed business logic processing for Pet: {}", pet.getId());
        return pet;
    }

    /**
     * Creates a success response with enhanced metadata.
     */
    private PetResponse createSuccessResponse(Pet pet, String entityId) {
        PetResponse response = new PetResponse();
        response.setSuccess(true);
        response.setMessage("Pet entity created successfully");
        response.setPet(pet);

        // Add processing metadata
        PetResponse.ProcessingMetadata metadata = new PetResponse.ProcessingMetadata();
        metadata.setProcessedAt(Instant.now().toString());
        metadata.setProcessedBy("PetController");
        metadata.setEntityId(entityId);
        metadata.setProcessingVersion("2.0");
        response.setMetadata(metadata);

        // Add validation status
        PetResponse.ValidationStatus validation = new PetResponse.ValidationStatus();
        validation.setValid(pet.isValid());
        validation.setHasStatus(pet.hasStatus());
        validation.setTagCount(pet.getTags() != null ? pet.getTags().size() : 0);
        validation.setPhotoUrlCount(pet.getPhotoUrls() != null ? pet.getPhotoUrls().size() : 0);
        validation.setValidationMessages(List.of("All validations passed"));
        response.setValidation(validation);

        return response;
    }

    /**
     * Creates an error response with detailed context.
     */
    private PetResponse createErrorResponse(String errorCode, String errorMessage) {
        PetResponse response = new PetResponse();
        response.setSuccess(false);
        response.setMessage("Pet operation failed");
        response.setError(errorCode);
        response.setErrorDetails(errorMessage);

        // Add processing metadata for error tracking
        PetResponse.ProcessingMetadata metadata = new PetResponse.ProcessingMetadata();
        metadata.setProcessedAt(Instant.now().toString());
        metadata.setProcessedBy("PetController");
        metadata.setProcessingVersion("2.0");
        response.setMetadata(metadata);

        return response;
    }

    /**
     * Validates if a string is a valid URL format.
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://");
    }


    // ========================================
    // EXCEPTION HANDLERS
    // ========================================

    /**
     * Handles validation exceptions with detailed error responses.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<PetResponse> handleValidationException(jakarta.validation.ConstraintViolationException ex) {
        logger.error("Validation error: {}", ex.getMessage());

        List<String> validationMessages = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();

        PetResponse errorResponse = createErrorResponse("VALIDATION_ERROR",
            "Request validation failed: " + String.join(", ", validationMessages));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles illegal argument exceptions from business logic.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PetResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.error("Business logic error: {}", ex.getMessage());

        PetResponse errorResponse = createErrorResponse("BUSINESS_LOGIC_ERROR", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles all other exceptions with generic error response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PetResponse> handleGenericException(Exception ex) {
        logger.error("Unhandled exception in Pet controller", ex);

        PetResponse errorResponse = createErrorResponse("INTERNAL_ERROR",
            "An unexpected error occurred while processing your request");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}