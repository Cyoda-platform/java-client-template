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
public class PetValidDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidDataCriterion(SerializerFactory serializerFactory) {
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
        // must match exact criterion name
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.debug("Pet entity is null in context");
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields: name, species, status
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Missing required field: name", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Missing required field: species", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Missing required field: status", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Age business rule: if present must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("Age must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Photos data quality: entries must not be null/blank
         if (entity.getPhotos() != null) {
             for (String p : entity.getPhotos()) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("Photos contain blank or null entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // If sourceId is provided, sourceUrl should be provided as well for enrichment traceability
         if (entity.getSourceId() != null && !entity.getSourceId().isBlank()) {
             if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
                 return EvaluationOutcome.fail("sourceId provided but sourceUrl is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Optional fields (breed, description) if provided must not be blank
         if (entity.getBreed() != null && entity.getBreed().isBlank()) {
             return EvaluationOutcome.fail("breed is blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getDescription() != null && entity.getDescription().isBlank()) {
             return EvaluationOutcome.fail("description is blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}