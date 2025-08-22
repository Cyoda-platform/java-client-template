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
public class PetValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidationFailedCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         StringBuilder missing = new StringBuilder();

         // Required: name
         try {
             String name = entity.getName();
             if (name == null || name.trim().isEmpty()) {
                 missing.append("name is required; ");
             }
         } catch (NoSuchMethodError | Exception e) {
             logger.debug("getName() not available on Pet or invocation failed: {}", e.getMessage());
             // If getter doesn't exist, treat as validation failure conservatively.
             missing.append("name is required; ");
         }

         // Required: species
         try {
             String species = entity.getSpecies();
             if (species == null || species.trim().isEmpty()) {
                 missing.append("species is required; ");
             }
         } catch (NoSuchMethodError | Exception e) {
             logger.debug("getSpecies() not available on Pet or invocation failed: {}", e.getMessage());
             missing.append("species is required; ");
         }

         if (missing.length() > 0) {
             String msg = missing.toString().trim();
             // normalize trailing semicolon/space
             if (msg.endsWith(";")) {
                 msg = msg.substring(0, msg.length() - 1).trim();
             }
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}