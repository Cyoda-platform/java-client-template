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

@Component
public class QuarantineCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public QuarantineCriterion(SerializerFactory serializerFactory) {
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
         Pet pet = context.entity();

         if (pet == null) {
             logger.warn("QuarantineCriterion invoked with null Pet entity");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<String> healthRecords = pet.getHealthRecords();

         // If health records are absent or empty -> quarantine required
         if (healthRecords == null || healthRecords.isEmpty()) {
             String msg = "Missing health records; quarantine required";
             logger.info("Pet [{}] - {}", pet.getId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         boolean hasVaccinationRecord = false;
         String matchingFlagRecord = null;

         for (String record : healthRecords) {
             if (record == null) continue;
             String lower = record.toLowerCase();

             // explicit flags that indicate vet check / quarantine
             if (lower.contains("requires_vet") || lower.contains("requires vet")
                 || lower.contains("needs_vet") || lower.contains("needs vet")
                 || lower.contains("quarantine") || lower.contains("contagious")
                 || lower.contains("isolation") || lower.contains("infectious")
                 || lower.contains("infection") || lower.contains("injury")) {
                 matchingFlagRecord = record;
                 break;
             }

             // indicators of vaccination/immunization records
             if (lower.contains("vacc") || lower.contains("vax") || lower.contains("immun")
                 || lower.contains("rabies") || lower.contains("distemper") || lower.contains("parvo")) {
                 hasVaccinationRecord = true;
             }
         }

         if (matchingFlagRecord != null) {
             String msg = "Health record indicates vet check required: " + matchingFlagRecord;
             logger.info("Pet [{}] - {}", pet.getId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (!hasVaccinationRecord) {
             String msg = "Missing vaccination records; quarantine required";
             logger.info("Pet [{}] - {}", pet.getId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}