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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class ValidPetDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidPetDataCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion/operation name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.warn("Pet entity is null in ValidPetDataCriterion");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required core fields (based on Pet.isValid())
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Photos: if present, entries must be non-blank and look like URLs
         List<String> photos = entity.getPhotos();
         if (photos != null) {
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("photos contain blank entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 String lower = p.toLowerCase();
                 if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                     return EvaluationOutcome.fail("photo URL invalid: " + p, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Tags: if present, entries must be non-blank
         List<String> tags = entity.getTags();
         if (tags != null) {
             for (String t : tags) {
                 if (t == null || t.isBlank()) {
                     return EvaluationOutcome.fail("tags contain blank entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // importedAt: if present, must be ISO-8601 parseable
         String importedAt = entity.getImportedAt();
         if (importedAt != null && !importedAt.isBlank()) {
             try {
                 OffsetDateTime.parse(importedAt);
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("importedAt is not a valid ISO-8601 timestamp: " + importedAt,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // sex: if present, must be one of expected values
         String sex = entity.getSex();
         if (sex != null && !sex.isBlank()) {
             String s = sex.trim().toLowerCase();
             if (!(s.equals("m") || s.equals("f") || s.equals("unknown"))) {
                 return EvaluationOutcome.fail("sex must be 'M', 'F' or 'unknown' if provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // size: if present, validate allowed values
         String size = entity.getSize();
         if (size != null && !size.isBlank()) {
             String sz = size.trim().toLowerCase();
             if (!(sz.equals("small") || sz.equals("medium") || sz.equals("large"))) {
                 return EvaluationOutcome.fail("size must be one of 'small', 'medium', 'large' if provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Age: allow free-form but reject obviously invalid blanks handled above by isValid() requirements (not mandatory here)
         // Business rule example: species should be reasonable short value (avoid extremely long species strings)
         if (entity.getSpecies() != null && entity.getSpecies().length() > 100) {
             return EvaluationOutcome.fail("species value is unusually long", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}