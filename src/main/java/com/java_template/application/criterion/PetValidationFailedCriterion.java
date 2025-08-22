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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.debug("Pet entity is null in context");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String name = entity.getName();
         String species = entity.getSpecies();
         String status = entity.getStatus();

         boolean missingName = (name == null) || name.trim().isEmpty();
         boolean missingSpecies = (species == null) || species.trim().isEmpty();

         // If required fields are missing, this is a validation failure.
         if (missingName || missingSpecies) {
             StringBuilder msg = new StringBuilder("Missing required fields:");
             if (missingName) msg.append(" name");
             if (missingSpecies) {
                 if (missingName) msg.append(",");
                 msg.append(" species");
             }
             return EvaluationOutcome.fail(msg.toString(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the pet is marked as validation_failed but the basic required fields are present,
         // surface a data quality failure so operators can inspect enrichment/photo issues.
         if ("validation_failed".equals(status)) {
             return EvaluationOutcome.fail("Pet status is 'validation_failed' despite required fields being present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Otherwise the entity passes this criterion.
         return EvaluationOutcome.success();
    }
}