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
public class AvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AvailabilityCriterion(SerializerFactory serializerFactory) {
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

         // Basic required checks (use existing getters only)
         if (entity == null) {
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Pet status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only pets with status AVAILABLE should pass this availability criterion
         if (!"AVAILABLE".equalsIgnoreCase(status.trim())) {
             return EvaluationOutcome.fail("Pet status is not AVAILABLE", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validation: species must be present for matching/availability
         String species = entity.getSpecies();
         if (species == null || species.isBlank()) {
             return EvaluationOutcome.fail("Species is required for availability", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: age, if present, must be non-negative
         Integer age = entity.getAge();
         if (age != null && age < 0) {
             return EvaluationOutcome.fail("Age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: must have at least one photo URL or a sourceUrl to obtain photos
         if ((entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty())
             && (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank())) {
             return EvaluationOutcome.fail("No photos and no sourceUrl present to verify listing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, pet is considered available
         return EvaluationOutcome.success();
    }
}