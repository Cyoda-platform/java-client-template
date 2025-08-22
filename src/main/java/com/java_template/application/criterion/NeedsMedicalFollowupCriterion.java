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
public class NeedsMedicalFollowupCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NeedsMedicalFollowupCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Pet entity is null in NeedsMedicalFollowupCriterion");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = pet.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("Pet.status is missing for pet id={}", pet.getId());
             return EvaluationOutcome.fail("Pet status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // This criterion only applies for pets that have been adopted
         if (!"adopted".equalsIgnoreCase(status)) {
             logger.debug("Pet id={} not in adopted status (status={})", pet.getId(), status);
             return EvaluationOutcome.fail("Pet not adopted", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If medicalNotes are present, assume follow-up is required
         String medicalNotes = pet.getMedicalNotes();
         if (medicalNotes != null && !medicalNotes.isBlank()) {
             logger.debug("Pet id={} has medical notes -> needs follow-up", pet.getId());
             return EvaluationOutcome.success();
         }

         // Check vaccination summary for hints that follow-up is needed (overdue, due, incomplete, pending)
         String vacc = pet.getVaccinationSummary();
         if (vacc != null && !vacc.isBlank()) {
             String vlow = vacc.toLowerCase();
             if (vlow.contains("due") || vlow.contains("overdue") || vlow.contains("incomplete") || vlow.contains("pending") || vlow.contains("needs")) {
                 logger.debug("Pet id={} vaccination summary indicates follow-up needed: {}", pet.getId(), vacc);
                 return EvaluationOutcome.success();
             }
         }

         // Very young animals may require post-adoption medical checks
         Integer ageMonths = pet.getAgeMonths();
         if (ageMonths != null && ageMonths < 6) {
             logger.debug("Pet id={} is very young ({} months) -> needs follow-up", pet.getId(), ageMonths);
             return EvaluationOutcome.success();
         }

         // Default: no medical follow-up required
         logger.debug("Pet id={} does not require medical follow-up", pet.getId());
         return EvaluationOutcome.fail("No medical follow-up required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}