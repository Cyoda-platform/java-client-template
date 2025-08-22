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
public class NoFollowupRequiredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NoFollowupRequiredCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("No entity provided to NoFollowupRequiredCriterion");
             return EvaluationOutcome.fail("Pet entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion is intended to be evaluated when the pet has been adopted.
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Pet status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (!"adopted".equalsIgnoreCase(status)) {
             // Not applicable unless the pet is adopted; treat as validation failure so callers can route appropriately
             return EvaluationOutcome.fail("Criterion applicable only for adopted pets", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If medical notes explicitly indicate follow-up, require follow-up
         String medicalNotes = entity.getMedicalNotes();
         if (medicalNotes != null && !medicalNotes.isBlank()) {
             String mn = medicalNotes.toLowerCase();
             if (mn.contains("follow") || mn.contains("recheck") || mn.contains("monitor") ||
                 mn.contains("suture") || mn.contains("surgery") || mn.contains("fracture") ||
                 mn.contains("needs check") || mn.contains("needs follow")) {
                 return EvaluationOutcome.fail("Medical notes indicate follow-up required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Vaccination summary: if missing or indicates vaccinations are due/overdue, require follow-up or mark as data quality issue
         String vac = entity.getVaccinationSummary();
         if (vac == null || vac.isBlank()) {
             return EvaluationOutcome.fail("Vaccination summary missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         } else {
             String vs = vac.toLowerCase();
             if (vs.contains("due") || vs.contains("overdue") || vs.contains("not up to date") || vs.contains("expired") || vs.contains("pending")) {
                 return EvaluationOutcome.fail("Vaccinations not up to date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // No explicit indicators of required medical follow-up found
         return EvaluationOutcome.success();
    }
}