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

import java.time.DateTimeParseException;
import java.time.Instant;
import java.util.List;

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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required presence checks (basic completeness)
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
         if (entity.getAge_value() == null || entity.getAge_value() < 0) {
             return EvaluationOutcome.fail("age_value is required and must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getAge_unit() == null || entity.getAge_unit().isBlank()) {
             return EvaluationOutcome.fail("age_unit is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLocation() == null) {
             return EvaluationOutcome.fail("location is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLocation().getCity() == null || entity.getLocation().getCity().isBlank()) {
             return EvaluationOutcome.fail("location.city is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLocation().getLat() == null || entity.getLocation().getLon() == null) {
             return EvaluationOutcome.fail("location.lat and location.lon are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPhotos() == null) {
             return EvaluationOutcome.fail("photos collection is required (may be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String p : entity.getPhotos()) {
             if (p == null || p.isBlank()) {
                 return EvaluationOutcome.fail("photos must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }
         if (entity.getTemperament_tags() == null) {
             return EvaluationOutcome.fail("temperament_tags collection is required (may be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String t : entity.getTemperament_tags()) {
             if (t == null || t.isBlank()) {
                 return EvaluationOutcome.fail("temperament_tags must not contain blank entries", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Data quality checks
         Double lat = entity.getLocation().getLat();
         Double lon = entity.getLocation().getLon();
         if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
             return EvaluationOutcome.fail("location coordinates out of bounds", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // created_at should be ISO timestamp
         try {
             Instant.parse(entity.getCreated_at());
         } catch (DateTimeParseException ex) {
             return EvaluationOutcome.fail("created_at is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic URL quality check for photos
         for (String p : entity.getPhotos()) {
             String lower = p.toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("photo URLs must start with http:// or https://", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule validations
         String species = entity.getSpecies().trim().toLowerCase();
         if (!(species.equals("dog") || species.equals("cat") || species.equals("other"))) {
             return EvaluationOutcome.fail("species must be one of: dog, cat, other", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String ageUnit = entity.getAge_unit().trim().toLowerCase();
         if (!(ageUnit.equals("years") || ageUnit.equals("months"))) {
             return EvaluationOutcome.fail("age_unit must be 'years' or 'months'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (entity.getSex() != null && !entity.getSex().isBlank()) {
             String sex = entity.getSex().trim().toUpperCase();
             if (!(sex.equals("M") || sex.equals("F"))) {
                 return EvaluationOutcome.fail("sex must be 'M' or 'F' when present", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         if (entity.getSize() != null && !entity.getSize().isBlank()) {
             String size = entity.getSize().trim().toLowerCase();
             if (!(size.equals("small") || size.equals("medium") || size.equals("large"))) {
                 return EvaluationOutcome.fail("size must be one of: small, medium, large when present", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         String availability = entity.getAvailability_status().trim().toLowerCase();
         if (!(availability.equals("available") || availability.equals("adopted") || availability.equals("pending"))) {
             return EvaluationOutcome.fail("availability_status must be one of: available, adopted, pending", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (entity.getHealth_status() != null && !entity.getHealth_status().isBlank()) {
             String hs = entity.getHealth_status().trim().toLowerCase();
             if (!(hs.equals("vaccinated") || hs.equals("needs_care"))) {
                 return EvaluationOutcome.fail("health_status must be 'vaccinated' or 'needs_care' when present", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}