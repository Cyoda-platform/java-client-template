package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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
public class StaffDeclineCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StaffDeclineCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionJob> context) {
         AdoptionJob entity = context.entity();

         // Basic presence check for status
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required on AdoptionJob", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion applies only when staff has declined the job
         if (!"declined".equals(entity.getStatus())) {
             return EvaluationOutcome.success();
         }

         // For declined jobs, decisionBy must be present (staff identifier)
         if (entity.getDecisionBy() == null || entity.getDecisionBy().isBlank()) {
             return EvaluationOutcome.fail("decisionBy is required when AdoptionJob is declined", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // processedAt should be recorded when a decision is made
         if (entity.getProcessedAt() == null || entity.getProcessedAt().isBlank()) {
             return EvaluationOutcome.fail("processedAt timestamp is required when AdoptionJob is declined", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: require an explanation for the decline in either notes or resultDetails
         boolean hasNotes = entity.getNotes() != null && !entity.getNotes().isBlank();
         boolean hasResultDetails = entity.getResultDetails() != null && !entity.getResultDetails().isBlank();
         if (!hasNotes && !hasResultDetails) {
             return EvaluationOutcome.fail("declined AdoptionJob must include notes or resultDetails explaining the decision", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}