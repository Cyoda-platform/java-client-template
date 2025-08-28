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
public class PetDataIncompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetDataIncompleteCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Pet id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required basic attributes
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Pet species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSex() == null || entity.getSex().isBlank()) {
             return EvaluationOutcome.fail("Pet sex is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Pet status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Age must be non-negative if provided
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("Pet age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Photos: require at least one photo for listing
         if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
             return EvaluationOutcome.fail("At least one pet photo is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate status values against expected canonical statuses from functional requirements.
         // If an explicit unknown status is present, mark as data quality failure.
         String status = entity.getStatus();
         if (status != null && !status.isBlank()) {
             String normalized = status.strip().toLowerCase();
             boolean allowed = "available".equals(normalized) || "pending".equals(normalized) || "adopted".equals(normalized);
             if (!allowed) {
                 return EvaluationOutcome.fail("Unknown pet status: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}