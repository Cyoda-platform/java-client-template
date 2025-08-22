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

import java.util.Collection;
import java.util.Set;

@Component
public class PetInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetInvalidCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Pet entity is null in PetInvalidCriterion");
             return EvaluationOutcome.fail("pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Name required
         String name = entity.getName();
         if (name == null || name.trim().isEmpty()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Species required
         String species = entity.getSpecies();
         if (species == null || species.trim().isEmpty()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be one of canonical values if present
         String status = entity.getStatus();
         if (status != null) {
             Set<String> allowedStatuses = Set.of("new", "validated", "available", "reserved", "adopted", "archived", "validation_failed");
             if (!allowedStatuses.contains(status)) {
                 return EvaluationOutcome.fail("status has invalid value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Age must be non-negative if present
         Object ageObj;
         try {
             ageObj = entity.getAge();
         } catch (NoSuchMethodError | NoSuchFieldError e) {
             ageObj = null;
         }
         if (ageObj instanceof Number) {
             Number ageNum = (Number) ageObj;
             if (ageNum.doubleValue() < 0) {
                 return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Gender if present must be one of allowed
         String gender = entity.getGender();
         if (gender != null && !gender.trim().isEmpty()) {
             String normalized = gender.trim().toLowerCase();
             if (!(normalized.equals("male") || normalized.equals("female") || normalized.equals("unknown"))) {
                 return EvaluationOutcome.fail("gender has invalid value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Photos: if present, entries must be non-empty strings
         Object photosObj;
         try {
             photosObj = entity.getPhotos();
         } catch (NoSuchMethodError | NoSuchFieldError e) {
             photosObj = null;
         }
         if (photosObj instanceof Collection) {
             Collection<?> coll = (Collection<?>) photosObj;
             for (Object o : coll) {
                 if (!(o instanceof String) || ((String) o).trim().isEmpty()) {
                     return EvaluationOutcome.fail("photos contain invalid url entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         } else if (photosObj instanceof String[]) {
             String[] arr = (String[]) photosObj;
             for (String s : arr) {
                 if (s == null || s.trim().isEmpty()) {
                     return EvaluationOutcome.fail("photos contain invalid url entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // All basic validation passed
         return EvaluationOutcome.success();
    }
}