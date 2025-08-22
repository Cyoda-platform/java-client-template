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
public class IsValidPetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidPetCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Pet entity is null in IsValidPetCriterion");
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

         // photos must be present (can be empty list but not null)
         if (entity.getPhotos() == null) {
             return EvaluationOutcome.fail("photos collection must be present (can be empty)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // age if present must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // gender if present should be one of expected values
         if (entity.getGender() != null && !entity.getGender().isBlank()) {
             String g = entity.getGender().trim().toLowerCase();
             if (!g.equals("male") && !g.equals("female") && !g.equals("unknown")) {
                 return EvaluationOutcome.fail("gender must be one of: male, female, unknown", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // status should be one of allowed workflow states
         String s = entity.getStatus().trim().toLowerCase();
         if (!(s.equals("available") || s.equals("reserved") || s.equals("adopted") || s.equals("created") || s.equals("validation") || s.equals("enrichment"))) {
             // classify invalid status as validation failure because it prevents workflow transition
             return EvaluationOutcome.fail("status must be one of: available, reserved, adopted", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // source if present can be validated against known sources (optional)
         if (entity.getSource() != null && !entity.getSource().isBlank()) {
             String src = entity.getSource().trim().toLowerCase();
             if (!(src.equals("petstore") || src.equals("manual"))) {
                 return EvaluationOutcome.fail("source should be either 'Petstore' or 'Manual' when provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // All validations passed
         return EvaluationOutcome.success();
    }
}