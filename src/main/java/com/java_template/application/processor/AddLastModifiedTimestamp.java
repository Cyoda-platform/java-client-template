package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.Pet;
import com.java_template.application.serializer.ProcessorSerializer;
import com.java_template.application.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complex processor that adds last modified timestamp to Pet entities.
 * Demonstrates advanced usage of sealed ResponseBuilder with:
 * - Entity validation and transformation
 * - Business logic processing
 * - Error handling with detailed context
 * - Type-safe response building
 * - Comprehensive validation pipeline
 */
@Component
public class AddLastModifiedTimestamp implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddLastModifiedTimestamp.class);

    // Business constants
    private static final Set<String> VALID_STATUSES = Set.of("available", "pending", "sold");
    private static final Set<String> VALID_CATEGORIES = Set.of("dog", "cat", "bird", "fish", "reptile", "other");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_TAGS = 10;
    private static final int MAX_PHOTO_URLS = 20;

    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public AddLastModifiedTimestamp(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        logger.info("AddLastModifiedTimestamp processor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet entity timestamp update for request: {}", request.getId());

        try {
            // Validate request first
            if (!isRequestValidForProcessing(request)) {
                return handleInvalidRequest(request);
            }

            // Process valid Pet entity
            return processValidPetEntity(request);

        } catch (Exception e) {
            logger.error("Unexpected error processing Pet timestamp for request {}", request.getId(), e);
            return serializer.errorResponse(request)
                .withError("PROCESSING_ERROR", "Unexpected error during Pet processing")
                .withAdditionalErrorDetails("Exception: " + e.getMessage())
                .build();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelKey) {
        if (modelKey instanceof OperationSpecification.Entity entitySpec) {
            ModelSpec modelSpec = entitySpec.modelKey();
            return "pet".equals(modelSpec.getName()) &&
                   Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.getVersion();
        }
        return false;
    }

    // ========================================
    // MAIN PROCESSING METHODS
    // ========================================

    /**
     * Simple example demonstrating the use of withEntity method with entityToJsonNode converter.
     * This is a cleaner approach when you just need to return a processed entity without additional metadata.
     */
    private EntityProcessorCalculationResponse processSimplePetEntity(EntityProcessorCalculationRequest request) {
        logger.debug("Processing Pet entity with simple approach for request: {}", request.getId());

        try {
            // Extract and process Pet entity
            Pet pet = serializer.extractEntity(request, Pet.class);
            Pet processedPet = applyPetBusinessLogic(pet);
            processedPet.addLastModifiedTimestamp();

            // Clean approach: Use withEntity method with serializer's entityToJsonNode converter
            // This is the recommended approach for simple entity responses
            return serializer.successResponse(request)
                .withSuccess()
                .withEntity(processedPet, serializer::entityToJsonNode)  // Clean entity conversion using interface method
                .build();

        } catch (Exception e) {
            logger.error("Failed to process Pet entity for request {}", request.getId(), e);
            return serializer.errorResponse(request)
                .withError("PROCESSING_ERROR", "Failed to process Pet entity")
                .withAdditionalErrorDetails("Processing error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Processes a valid Pet entity with comprehensive business logic.
     */
    private EntityProcessorCalculationResponse processValidPetEntity(EntityProcessorCalculationRequest request) {
        logger.debug("Processing valid Pet entity for request: {}", request.getId());

        try {
            // Extract and validate Pet entity
            Pet pet = serializer.extractEntity(request, Pet.class);

            // Apply business logic transformations
            Pet processedPet = applyPetBusinessLogic(pet);

            // Validate business rules
            List<String> businessValidationErrors = validatePetBusinessRules(processedPet);
            if (!businessValidationErrors.isEmpty()) {
                String errorDetails = String.join("; ", businessValidationErrors);
                return serializer.errorResponse(request)
                    .withError("BUSINESS_VALIDATION_ERROR", "Pet entity failed business validation")
                    .withAdditionalErrorDetails(errorDetails)
                    .build();
            }

            // Add timestamp
            processedPet.addLastModifiedTimestamp();

            // Demonstrate two approaches for successful response creation:

            // Approach 1: Simple entity response using withEntity method
            // This is cleaner when you just need to return the processed entity
            if (shouldUseSimpleResponse(request)) {
                return serializer.successResponse(request)
                    .withSuccess()
                    .withEntity(processedPet, serializer::entityToJsonNode)  // Use interface method
                    .build();
            }

            // Approach 2: Enhanced response with metadata using withJsonData method
            // This is better when you need additional processing metadata
            JsonNode enhancedData = createEnhancedPetJson(processedPet, request);

            // Perform final validation
            List<String> finalValidationErrors = performFinalPetValidation(enhancedData);
            if (!finalValidationErrors.isEmpty()) {
                String errorDetails = String.join("; ", finalValidationErrors);
                return serializer.errorResponse(request)
                    .withError("FINAL_VALIDATION_ERROR", "Pet entity failed final validation")
                    .withAdditionalErrorDetails(errorDetails)
                    .build();
            }

            // Return successful response with enhanced JSON data
            return serializer.successResponse(request)
                .withSuccess()
                .withJsonData(enhancedData)
                .build();

        } catch (Exception e) {
            logger.error("Failed to process Pet entity for request {}", request.getId(), e);
            return serializer.errorResponse(request)
                .withError("PROCESSING_ERROR", "Failed to process Pet entity")
                .withAdditionalErrorDetails("Processing error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Handles invalid requests with detailed error context.
     */
    private EntityProcessorCalculationResponse handleInvalidRequest(EntityProcessorCalculationRequest request) {
        logger.warn("Invalid Pet entity request: {}", request.getId());

        List<String> validationErrors = validateRequest(request);
        String errorDetails = String.join("; ", validationErrors);

        return serializer.errorResponse(request)
            .withError("INVALID_PET_REQUEST", "Pet entity validation failed")
            .withAdditionalErrorDetails(errorDetails)
            .build();
    }

    // ========================================
    // BUSINESS LOGIC METHODS
    // ========================================

    /**
     * Applies comprehensive business logic transformations to Pet entity.
     */
    private Pet applyPetBusinessLogic(Pet pet) {
        logger.debug("Applying business logic to Pet: {}", pet.getId());

        // Create a copy to avoid modifying the original
        Pet processedPet = createPetCopy(pet);

        // 1. Normalize status
        processedPet.normalizeStatus();

        // 2. Validate and normalize category
        if (processedPet.getCategory() != null) {
            processedPet.setCategory(processedPet.getCategory().toLowerCase());
            if (!VALID_CATEGORIES.contains(processedPet.getCategory())) {
                processedPet.setCategory("other");
                logger.debug("Normalized invalid category to 'other' for Pet: {}", pet.getId());
            }
        }

        // 3. Clean and validate tags
        if (processedPet.getTags() != null) {
            List<String> cleanedTags = processedPet.getTags().stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .limit(MAX_TAGS)
                .collect(Collectors.toList());
            processedPet.setTags(cleanedTags);
        }

        // 4. Validate and clean photo URLs
        if (processedPet.getPhotoUrls() != null) {
            List<String> validUrls = processedPet.getPhotoUrls().stream()
                .filter(this::isValidUrl)
                .limit(MAX_PHOTO_URLS)
                .collect(Collectors.toList());
            processedPet.setPhotoUrls(validUrls);
        }

        // 5. Trim and validate name
        if (processedPet.getName() != null) {
            String trimmedName = processedPet.getName().trim();
            if (trimmedName.length() > MAX_NAME_LENGTH) {
                trimmedName = trimmedName.substring(0, MAX_NAME_LENGTH);
                logger.debug("Truncated Pet name to {} characters for Pet: {}", MAX_NAME_LENGTH, pet.getId());
            }
            processedPet.setName(trimmedName);
        }

        logger.debug("Completed business logic processing for Pet: {}", processedPet.getId());
        return processedPet;
    }

    /**
     * Creates enhanced JSON representation of Pet with additional metadata.
     */
    private JsonNode createEnhancedPetJson(Pet pet, EntityProcessorCalculationRequest request) {
        try {
            // Convert Pet to JSON
            ObjectNode petJson = (ObjectNode) objectMapper.valueToTree(pet);

            // Add processing metadata
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("processedAt", Instant.now().toString());
            metadata.put("processedBy", "AddLastModifiedTimestamp");
            metadata.put("requestId", request.getRequestId());
            metadata.put("entityId", request.getEntityId());
            metadata.put("processingVersion", "1.0");

            // Add validation status
            ObjectNode validationStatus = objectMapper.createObjectNode();
            validationStatus.put("isValid", pet.isValid());
            validationStatus.put("hasStatus", pet.hasStatus());
            validationStatus.put("tagCount", pet.getTags() != null ? pet.getTags().size() : 0);
            validationStatus.put("photoUrlCount", pet.getPhotoUrls() != null ? pet.getPhotoUrls().size() : 0);

            // Combine all data
            ObjectNode enhancedData = objectMapper.createObjectNode();
            enhancedData.set("pet", petJson);
            enhancedData.set("metadata", metadata);
            enhancedData.set("validation", validationStatus);

            return enhancedData;

        } catch (Exception e) {
            logger.error("Failed to create enhanced Pet JSON for Pet: {}", pet.getId(), e);
            // Fallback to basic Pet JSON
            return objectMapper.valueToTree(pet);
        }
    }

    // ========================================
    // VALIDATION METHODS
    // ========================================

    /**
     * Validates if request is suitable for processing.
     */
    private boolean isRequestValidForProcessing(EntityProcessorCalculationRequest request) {
        try {
            // Basic request validation
            if (request == null || request.getPayload() == null || request.getPayload().getData() == null) {
                return false;
            }

            // Try to extract Pet entity
            Pet pet = serializer.extractEntity(request, Pet.class);
            return pet != null && pet.isValid();

        } catch (Exception e) {
            logger.debug("Request validation failed for request: {}",
                request != null ? request.getId() : "null", e);
            return false;
        }
    }

    /**
     * Validates request and returns list of validation errors.
     */
    private List<String> validateRequest(EntityProcessorCalculationRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request is null");
            return errors;
        }

        if (request.getPayload() == null) {
            errors.add("Request payload is null");
        } else if (request.getPayload().getData() == null) {
            errors.add("Request payload data is null");
        }

        try {
            Pet pet = serializer.extractEntity(request, Pet.class);
            if (pet == null) {
                errors.add("Pet entity is null");
            } else if (!pet.isValid()) {
                errors.add("Pet entity is invalid (missing ID or name)");
            }
        } catch (Exception e) {
            errors.add("Failed to extract Pet entity: " + e.getMessage());
        }

        return errors;
    }

    /**
     * Validates Pet entity against business rules.
     */
    private List<String> validatePetBusinessRules(Pet pet) {
        List<String> errors = new ArrayList<>();

        // Validate status
        if (pet.getStatus() != null && !VALID_STATUSES.contains(pet.getStatus().toLowerCase())) {
            errors.add("Invalid pet status: " + pet.getStatus());
        }

        // Validate name length
        if (pet.getName() != null && pet.getName().length() > MAX_NAME_LENGTH) {
            errors.add("Pet name too long (max " + MAX_NAME_LENGTH + " characters)");
        }

        // Validate tags count
        if (pet.getTags() != null && pet.getTags().size() > MAX_TAGS) {
            errors.add("Too many tags (max " + MAX_TAGS + " allowed)");
        }

        // Validate photo URLs count
        if (pet.getPhotoUrls() != null && pet.getPhotoUrls().size() > MAX_PHOTO_URLS) {
            errors.add("Too many photo URLs (max " + MAX_PHOTO_URLS + " allowed)");
        }

        return errors;
    }

    /**
     * Performs final validation on processed Pet data.
     */
    private List<String> performFinalPetValidation(JsonNode processedData) {
        List<String> errors = new ArrayList<>();

        if (processedData == null) {
            errors.add("Processed data is null");
            return errors;
        }

        // Validate structure
        if (!processedData.has("pet")) {
            errors.add("Missing pet data in processed result");
        }

        if (!processedData.has("metadata")) {
            errors.add("Missing metadata in processed result");
        }

        // Validate pet data
        JsonNode petData = processedData.get("pet");
        if (petData != null) {
            if (!petData.has("lastModified")) {
                errors.add("Missing lastModified timestamp");
            }

            if (!petData.has("id") || petData.get("id").isNull()) {
                errors.add("Missing or null pet ID");
            }

            if (!petData.has("name") || petData.get("name").asText().trim().isEmpty()) {
                errors.add("Missing or empty pet name");
            }
        }

        return errors;
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Determines whether to use simple response (just entity) or enhanced response (with metadata).
     * This is just for demonstration purposes - in real scenarios, this could be based on request parameters,
     * configuration, or business logic.
     */
    private boolean shouldUseSimpleResponse(EntityProcessorCalculationRequest request) {
        // For demonstration: use simple response for requests with specific criteria
        // In practice, this could be based on request headers, parameters, or configuration
        return request.getRequestId() != null && request.getRequestId().contains("simple");
    }

    /**
     * Creates a deep copy of Pet entity for safe processing.
     */
    private Pet createPetCopy(Pet original) {
        Pet copy = new Pet();
        copy.setId(original.getId());
        copy.setName(original.getName());
        copy.setStatus(original.getStatus());
        copy.setCategory(original.getCategory());
        copy.setLastModified(original.getLastModified());

        // Deep copy lists
        if (original.getTags() != null) {
            copy.setTags(new ArrayList<>(original.getTags()));
        }
        if (original.getPhotoUrls() != null) {
            copy.setPhotoUrls(new ArrayList<>(original.getPhotoUrls()));
        }

        return copy;
    }

    /**
     * Validates if a string is a valid URL format.
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            // Basic URL validation
            return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets processor name for logging and identification.
     */
    public String getName() {
        return "AddLastModifiedTimestamp";
    }

    /**
     * Gets processor version for tracking.
     */
    public String getVersion() {
        return "2.0.0";
    }

    /**
     * Gets supported entity types.
     */
    public Set<String> getSupportedEntityTypes() {
        return Set.of("pet");
    }
}
