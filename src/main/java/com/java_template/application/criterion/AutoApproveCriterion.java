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

@Component
public class AutoApproveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AutoApproveCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic structural validation using entity's own isValid method (use only existing API)
         try {
             if (!entity.isValid()) {
                 return EvaluationOutcome.fail("Entity failed basic validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             logger.warn("isValid() check threw exception for pet id {}: {}", entity.getId(), e.getMessage(), e);
             return EvaluationOutcome.fail("Entity validation threw exception", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only auto-approve pets that are currently available
         String availability = entity.getAvailability_status();
         if (availability == null || !"available".equalsIgnoreCase(availability)) {
             return EvaluationOutcome.fail("Pet not marked as available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: require vaccinated health status for auto-approval
         String health = entity.getHealth_status();
         if (health == null || !"vaccinated".equalsIgnoreCase(health)) {
             return EvaluationOutcome.fail("Pet not vaccinated", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: require at least one photo for auto-approval
         if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
             return EvaluationOutcome.fail("No photos provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Location sanity checks (lat/lon ranges)
         if (entity.getLocation() == null) {
             return EvaluationOutcome.fail("Location missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         Double lat = entity.getLocation().getLat();
         Double lon = entity.getLocation().getLon();
         if (lat == null || lon == null) {
             return EvaluationOutcome.fail("Location coordinates missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
             return EvaluationOutcome.fail("Invalid location coordinates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Age plausibility checks:
         Integer ageValue = entity.getAge_value();
         String ageUnit = entity.getAge_unit();
         if (ageValue == null) {
             return EvaluationOutcome.fail("Age value missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (ageUnit == null || ageUnit.isBlank()) {
             return EvaluationOutcome.fail("Age unit missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Reject obviously implausible ages:
         // - if age unit is months, require <= 240 months (~20 years)
         // - if age unit is years, require <= 30 years
         String au = ageUnit.trim().toLowerCase();
         if ("months".equals(au) || "month".equals(au)) {
             if (ageValue > 240) {
                 return EvaluationOutcome.fail("Age implausible for months (>240)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if ("years".equals(au) || "year".equals(au)) {
             if (ageValue > 30) {
                 return EvaluationOutcome.fail("Age implausible for years (>30)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             // Unknown age unit -> treat as data quality failure
             return EvaluationOutcome.fail("Unknown age unit", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Additional simple checks: name and species presence (isValid already checked, but double-check defensively)
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Name missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Species missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If all checks pass, auto-approve
         return EvaluationOutcome.success();
    }
}