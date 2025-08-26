package com.java_template.application.criterion;

import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
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

import java.util.List;

@Component
public class ValidateSearchFilterCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateSearchFilterCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(SearchFilter.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SearchFilter> context) {
        SearchFilter entity = context.entity();
        if (entity == null) {
            logger.debug("SearchFilter entity payload is null");
            return EvaluationOutcome.fail("SearchFilter payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required identifiers / metadata
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getUserId() == null || entity.getUserId().isBlank()) {
            return EvaluationOutcome.fail("user_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("created_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required flags
        if (entity.getIsActive() == null) {
            return EvaluationOutcome.fail("is_active flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getVaccinationRequired() == null) {
            return EvaluationOutcome.fail("vaccination_required flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Location center validations
        SearchFilter.LocationCenter loc = entity.getLocationCenter();
        if (loc == null) {
            return EvaluationOutcome.fail("location_center is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (loc.getLat() == null || loc.getLon() == null) {
            return EvaluationOutcome.fail("location_center must contain lat and lon", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (loc.getCity() == null || loc.getCity().isBlank()) {
            return EvaluationOutcome.fail("location_center.city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Numeric validations
        if (entity.getPageSize() != null && entity.getPageSize() <= 0) {
            return EvaluationOutcome.fail("page_size must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getRadiusKm() != null && entity.getRadiusKm() < 0) {
            return EvaluationOutcome.fail("radius_km must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAgeMin() != null && entity.getAgeMin() < 0) {
            return EvaluationOutcome.fail("age_min must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAgeMax() != null && entity.getAgeMax() < 0) {
            return EvaluationOutcome.fail("age_max must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getAgeMin() != null && entity.getAgeMax() != null && entity.getAgeMin() > entity.getAgeMax()) {
            return EvaluationOutcome.fail("age_min cannot be greater than age_max", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Collections element validations
        List<String> breeds = entity.getBreeds();
        if (breeds != null) {
            for (String b : breeds) {
                if (b == null || b.isBlank()) {
                    return EvaluationOutcome.fail("breeds must not contain blank elements", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        }
        List<String> sizes = entity.getSize();
        if (sizes != null) {
            for (String s : sizes) {
                if (s == null || s.isBlank()) {
                    return EvaluationOutcome.fail("size must not contain blank elements", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        }
        List<String> temperamentTags = entity.getTemperamentTags();
        if (temperamentTags != null) {
            for (String t : temperamentTags) {
                if (t == null || t.isBlank()) {
                    return EvaluationOutcome.fail("temperament_tags must not contain blank elements", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        }

        // Optional simple string fields (if present, must not be blank)
        if (entity.getAgeUnitPreference() != null && entity.getAgeUnitPreference().isBlank()) {
            return EvaluationOutcome.fail("age_unit_preference, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getSex() != null && entity.getSex().isBlank()) {
            return EvaluationOutcome.fail("sex, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getSortBy() != null && entity.getSortBy().isBlank()) {
            return EvaluationOutcome.fail("sort_by, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getSpecies() != null && entity.getSpecies().isBlank()) {
            return EvaluationOutcome.fail("species, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // All validations passed
        logger.debug("SearchFilter [{}] passed validation", entity.getId());
        return EvaluationOutcome.success();
    }
}