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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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
             logger.debug("SearchFilter entity is null");
             return EvaluationOutcome.fail("SearchFilter entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identity and ownership fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Name presence (entity requires name)
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required boolean flags
         if (entity.getIsActive() == null) {
             return EvaluationOutcome.fail("isActive flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getVaccinationRequired() == null) {
             return EvaluationOutcome.fail("vaccinationRequired flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Location center validation
         SearchFilter.LocationCenter lc = entity.getLocationCenter();
         if (lc == null) {
             return EvaluationOutcome.fail("locationCenter is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (lc.getLat() == null || lc.getLon() == null) {
             return EvaluationOutcome.fail("locationCenter lat and lon are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (lc.getCity() == null || lc.getCity().isBlank()) {
             return EvaluationOutcome.fail("locationCenter.city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric validations
         if (entity.getPageSize() != null && entity.getPageSize() <= 0) {
             return EvaluationOutcome.fail("pageSize must be > 0 if provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRadiusKm() != null && entity.getRadiusKm() < 0) {
             return EvaluationOutcome.fail("radiusKm must be >= 0 if provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAgeMin() != null && entity.getAgeMin() < 0) {
             return EvaluationOutcome.fail("ageMin must be >= 0 if provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAgeMax() != null && entity.getAgeMax() < 0) {
             return EvaluationOutcome.fail("ageMax must be >= 0 if provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAgeMin() != null && entity.getAgeMax() != null && entity.getAgeMin() > entity.getAgeMax()) {
             return EvaluationOutcome.fail("ageMin must not be greater than ageMax", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Collections element validations
         if (entity.getBreeds() != null) {
             for (String b : entity.getBreeds()) {
                 if (b == null || b.isBlank()) {
                     return EvaluationOutcome.fail("breeds must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }
         if (entity.getSize() != null) {
             for (String s : entity.getSize()) {
                 if (s == null || s.isBlank()) {
                     return EvaluationOutcome.fail("size list must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }
         if (entity.getTemperamentTags() != null) {
             for (String t : entity.getTemperamentTags()) {
                 if (t == null || t.isBlank()) {
                     return EvaluationOutcome.fail("temperamentTags must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }

         // Optional simple string fields (if present must not be blank)
         if (entity.getAgeUnitPreference() != null && entity.getAgeUnitPreference().isBlank()) {
             return EvaluationOutcome.fail("ageUnitPreference, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSex() != null && entity.getSex().isBlank()) {
             return EvaluationOutcome.fail("sex, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSortBy() != null && entity.getSortBy().isBlank()) {
             return EvaluationOutcome.fail("sortBy, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() != null && entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic sanity/business checks
         // Page size large but allowed; warn rather than fail in such cases
         if (entity.getPageSize() != null && entity.getPageSize() > 1000) {
             // large page sizes are data-quality concerns, not hard validation failures
             return EvaluationOutcome.fail("pageSize is unusually large", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}