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

import java.util.List;
import java.util.Objects;

@Component
public class PetValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidationCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return modelSpec.operationName().equals(className);
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.warn("Pet entity is null in evaluation context");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields: id, name, species, status
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

         // Age: if present must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Status must be one of allowed values (available/pending/adopted/removed)
         String status = entity.getStatus().trim().toLowerCase();
         if (!status.equals("available") && !status.equals("pending")
             && !status.equals("adopted") && !status.equals("removed")) {
             return EvaluationOutcome.fail("status must be one of: available, pending, adopted, removed",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Gender, if present, should be one of male/female/unknown
         if (entity.getGender() != null && !entity.getGender().isBlank()) {
             String gender = entity.getGender().trim().toLowerCase();
             if (!gender.equals("male") && !gender.equals("female") && !gender.equals("unknown")) {
                 return EvaluationOutcome.fail("gender must be one of: male, female, unknown",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Photos list: if present, must not contain null/blank entries
         List<String> photos = entity.getPhotos();
         if (photos != null) {
             for (String url : photos) {
                 if (url == null || url.isBlank()) {
                     return EvaluationOutcome.fail("photos must not contain empty or null URLs",
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Validate sourceMetadata if present
         Pet.SourceMetadata sm = entity.getSourceMetadata();
         if (sm != null) {
             if (sm.getExternalId() == null || sm.getExternalId().isBlank()) {
                 return EvaluationOutcome.fail("source_metadata.externalId is required when source_metadata is present",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sm.getSource() == null || sm.getSource().isBlank()) {
                 return EvaluationOutcome.fail("source_metadata.source is required when source_metadata is present",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Basic business rule: species should be a short string (avoid excessively long values)
         if (entity.getSpecies() != null && entity.getSpecies().length() > 100) {
             return EvaluationOutcome.fail("species value is unreasonably long",
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all checks passed
         return EvaluationOutcome.success();
    }
}