package com.java_template.application.criteria;

import com.java_template.application.entity.pet.Pet;
import com.java_template.application.serializer.CriterionSerializer;
import com.java_template.application.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Criteria implementation that validates Pet entities using the new Jackson serializers.
 * Demonstrates advanced usage patterns including:
 * - Type-safe entity extraction
 * - Multi-criteria evaluation with business rules
 * - Pattern matching with sealed interfaces
 * - Comprehensive validation logic
 */
@Component
public class IsValidPet implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(IsValidPet.class);

    // Business validation constants
    private static final Set<String> VALID_STATUSES = Set.of("available", "pending", "sold");
    private static final Set<String> REQUIRED_FIELDS = Set.of("id", "name");
    private static final int MIN_NAME_LENGTH = 1;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_TAGS = 10;

    private final CriterionSerializer serializer;

    public IsValidPet(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsValidPet criteria initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet validity for request: {}", request.getId());

        try {
            // Use executeFunction for multi-criteria evaluation
            return serializer.executeFunction(request, jacksonSerializer -> {
                // Evaluate all criteria using basic interface methods
                boolean allValid = validateBasicPetStructure(request) &&
                                 validatePetBusinessRules(request) &&
                                 validatePetDataQuality(request);

                return allValid ?
                    jacksonSerializer.matchResponse(request).withSuccessfulMatch("All Pet validation criteria passed").build() :
                    jacksonSerializer.matchResponse(request).withSuccessfulNonMatch("Pet validation criteria failed").build();
            });

        } catch (Exception e) {
            logger.error("Error checking Pet validity for request {}", request.getId(), e);
            return serializer.errorResponse(request).withMatchException("VALIDATION_ERROR", e).build();
        }
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        if (opsSpec instanceof OperationSpecification.Entity entitySpec) {
            ModelSpec modelSpec = entitySpec.modelKey();
            return "pet".equals(modelSpec.getName()) &&
                   Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.getVersion();
        }
        return false;
    }

    // ========================================
    // VALIDATION METHODS
    // ========================================

    /**
     * Validates basic Pet entity structure and required fields.
     */
    private boolean validateBasicPetStructure(EntityCriteriaCalculationRequest request) {
        try {
            Pet pet = serializer.extractEntity(request, Pet.class);

            // Check if Pet entity exists and is valid
            if (pet == null) {
                logger.debug("Pet entity is null for request: {}", request.getId());
                return false;
            }

            // Use Pet's built-in validation
            if (!pet.isValid()) {
                logger.debug("Pet entity failed basic validation for request: {}", request.getId());
                return false;
            }

            // Validate required fields
            if (pet.getId() == null || pet.getId() <= 0) {
                logger.debug("Pet has invalid ID for request: {}", request.getId());
                return false;
            }

            if (pet.getName() == null || pet.getName().trim().isEmpty()) {
                logger.debug("Pet has invalid name for request: {}", request.getId());
                return false;
            }

            logger.debug("Pet passed basic structure validation: id={}, name={}", pet.getId(), pet.getName());
            return true;

        } catch (Exception e) {
            logger.debug("Failed to validate basic Pet structure for request: {}", request.getId(), e);
            return false;
        }
    }

    /**
     * Validates Pet entity against business rules.
     */
    private boolean validatePetBusinessRules(EntityCriteriaCalculationRequest request) {
        try {
            Pet pet = serializer.extractEntity(request, Pet.class);

            // Validate status if present
            if (pet.getStatus() != null && !VALID_STATUSES.contains(pet.getStatus().toLowerCase())) {
                logger.debug("Pet has invalid status '{}' for request: {}", pet.getStatus(), request.getId());
                return false;
            }

            // Validate name length
            if (pet.getName() != null) {
                int nameLength = pet.getName().trim().length();
                if (nameLength < MIN_NAME_LENGTH || nameLength > MAX_NAME_LENGTH) {
                    logger.debug("Pet name length {} is invalid for request: {}", nameLength, request.getId());
                    return false;
                }
            }

            // Validate tags count
            if (pet.getTags() != null && pet.getTags().size() > MAX_TAGS) {
                logger.debug("Pet has too many tags ({}) for request: {}", pet.getTags().size(), request.getId());
                return false;
            }

            logger.debug("Pet passed business rules validation for request: {}", request.getId());
            return true;

        } catch (Exception e) {
            logger.debug("Failed to validate Pet business rules for request: {}", request.getId(), e);
            return false;
        }
    }

    /**
     * Validates Pet entity data quality and consistency.
     */
    private boolean validatePetDataQuality(EntityCriteriaCalculationRequest request) {
        try {
            Pet pet = serializer.extractEntity(request, Pet.class);

            // Validate photo URLs if present
            if (pet.getPhotoUrls() != null) {
                for (String url : pet.getPhotoUrls()) {
                    if (url != null && !isValidUrl(url)) {
                        logger.debug("Pet has invalid photo URL '{}' for request: {}", url, request.getId());
                        return false;
                    }
                }
            }

            // Validate tags if present
            if (pet.getTags() != null) {
                for (String tag : pet.getTags()) {
                    if (tag == null || tag.trim().isEmpty()) {
                        logger.debug("Pet has invalid tag for request: {}", request.getId());
                        return false;
                    }
                }
            }

            // Validate category if present
            if (pet.getCategory() != null && pet.getCategory().trim().isEmpty()) {
                logger.debug("Pet has empty category for request: {}", request.getId());
                return false;
            }

            logger.debug("Pet passed data quality validation for request: {}", request.getId());
            return true;

        } catch (Exception e) {
            logger.debug("Failed to validate Pet data quality for request: {}", request.getId(), e);
            return false;
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

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
     * Gets criteria name for logging and identification.
     */
    public String getName() {
        return "IsValidPet";
    }

    /**
     * Gets criteria version for tracking.
     */
    public String getVersion() {
        return "1.0.0";
    }

    /**
     * Gets supported entity types.
     */
    public Set<String> getSupportedEntityTypes() {
        return Set.of("pet");
    }
}
