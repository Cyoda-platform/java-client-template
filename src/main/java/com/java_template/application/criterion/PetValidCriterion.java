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

import java.util.Set;
import java.util.Objects;

@Component
public class PetValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidCriterion(SerializerFactory serializerFactory) {
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

         // Required fields: name and species
         if (entity.getName() == null || entity.getName().trim().isEmpty()) {
             return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().trim().isEmpty()) {
             return EvaluationOutcome.fail("Pet species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If status is present, ensure it is one of the canonical values
         String status = entity.getStatus();
         if (status != null) {
             Set<String> allowedStatuses = Set.of(
                 "new", "validated", "available", "reserved", "adopted", "archived", "validation_failed"
             );
             if (!allowedStatuses.contains(status)) {
                 return EvaluationOutcome.fail("Invalid pet.status: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Basic business rule: name should not be only whitespace (covered above) and species normalized non-empty.
         // More detailed checks (photos accessibility, enrichment) are handled by other criteria/processors.
         return EvaluationOutcome.success();
    }
}