package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Component
public class ValidatePetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidatePetCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) {
            logger.warn("Pet entity is null in context");
            return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required string fields
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
            return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAvailability_status() == null || entity.getAvailability_status().isBlank()) {
            return EvaluationOutcome.fail("availability_status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCreated_at() == null || entity.getCreated_at().isBlank()) {
            return EvaluationOutcome.fail("created_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate created_at is ISO-8601 timestamp
        try {
            Instant.parse(entity.getCreated_at());
        } catch (DateTimeParseException ex) {
            logger.debug("Invalid created_at format: {}", entity.getCreated_at(), ex);
            return EvaluationOutcome.fail("created_at must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate age
        if (entity.getAge_value() == null) {
            return EvaluationOutcome.fail("age_value is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAge_value() < 0) {
            return EvaluationOutcome.fail("age_value must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAge_unit() == null || entity.getAge_unit().isBlank()) {
            return EvaluationOutcome.fail("age_unit is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } else {
            String au = entity.getAge_unit().toLowerCase(Locale.ROOT);
            if (!("years".equals(au) || "months".equals(au))) {
                return EvaluationOutcome.fail("age_unit must be 'years' or 'months'", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Species-specific business rule: if species is dog or cat, breed must be present
        String species = entity.getSpecies().toLowerCase(Locale.ROOT);
        if ("dog".equals(species) || "cat".equals(species)) {
            if (entity.getBreed() == null || entity.getBreed().isBlank()) {
                return EvaluationOutcome.fail("breed is required for species 'dog' or 'cat'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Validate availability_status allowed values
        String avail = entity.getAvailability_status().toLowerCase(Locale.ROOT);
        if (!("available".equals(avail) || "adopted".equals(avail) || "pending".equals(avail))) {
            return EvaluationOutcome.fail("availability_status must be one of available|adopted|pending", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate health_status if present
        if (entity.getHealth_status() != null && !entity.getHealth_status().isBlank()) {
            String hs = entity.getHealth_status().toLowerCase(Locale.ROOT);
            if (!("vaccinated".equals(hs) || "needs_care".equals(hs))) {
                return EvaluationOutcome.fail("health_status must be 'vaccinated' or 'needs_care' if present", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Validate sex if present
        if (entity.getSex() != null && !entity.getSex().isBlank()) {
            String sex = entity.getSex();
            if (!("M".equals(sex) || "F".equals(sex))) {
                return EvaluationOutcome.fail("sex must be 'M' or 'F' if present", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Validate size if present
        if (entity.getSize() != null && !entity.getSize().isBlank()) {
            String size = entity.getSize().toLowerCase(Locale.ROOT);
            if (!("small".equals(size) || "medium".equals(size) || "large".equals(size))) {
                return EvaluationOutcome.fail("size must be one of small|medium|large if present", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // Validate location (geo validity)
        Pet.Location loc = entity.getLocation();
        if (loc == null) {
            return EvaluationOutcome.fail("location is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (loc.getCity() == null || loc.getCity().isBlank()) {
            return EvaluationOutcome.fail("location.city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (loc.getLat() == null || loc.getLon() == null) {
            return EvaluationOutcome.fail("location.lat and location.lon are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        double lat = loc.getLat();
        double lon = loc.getLon();
        if (lat < -90.0 || lat > 90.0) {
            return EvaluationOutcome.fail("location.lat must be between -90 and 90", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (lon < -180.0 || lon > 180.0) {
            return EvaluationOutcome.fail("location.lon must be between -180 and 180", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Photos validation (data quality)
        List<String> photos = entity.getPhotos();
        if (photos == null) {
            return EvaluationOutcome.fail("photos must be present (can be empty list) but not null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        for (String p : photos) {
            if (p == null || p.isBlank()) {
                return EvaluationOutcome.fail("photos must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Temperament tags validation (data quality)
        List<String> tags = entity.getTemperament_tags();
        if (tags == null) {
            return EvaluationOutcome.fail("temperament_tags must be present (can be empty) but not null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        for (String t : tags) {
            if (t == null || t.isBlank()) {
                return EvaluationOutcome.fail("temperament_tags must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // All validations passed
        return EvaluationOutcome.success();
    }
}