package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.time.Year;
import java.util.Set;

@Component
public class NotDuplicateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotDuplicateCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Basic identity presence checks
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Laureate source id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("Laureate firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("Laureate surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Laureate award year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getIngestJobId() == null || entity.getIngestJobId().isBlank()) {
             return EvaluationOutcome.fail("Ingest job reference is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         if (entity.getComputedAge() != null && entity.getComputedAge() < 0) {
             return EvaluationOutcome.fail("Computed age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // ID numeric sanity
         if (entity.getId() <= 0) {
             return EvaluationOutcome.fail("Laureate source id must be a positive integer", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Normalize identity key and perform plausibility checks
         String fn = entity.getFirstname() != null ? entity.getFirstname().trim().toLowerCase() : "";
         String sn = entity.getSurname() != null ? entity.getSurname().trim().toLowerCase() : "";
         String yr = entity.getYear() != null ? entity.getYear().trim() : "";

         String identityKey = fn + "|" + sn + "|" + yr;
         if (identityKey.replace("|", "").isBlank()) {
             return EvaluationOutcome.fail("Laureate identity fields are not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Reject obvious placeholder values for names (helps avoid trivial duplicates)
         Set<String> placeholders = Set.of("", "n/a", "na", "unknown", "none", "-", "--");
         if (placeholders.contains(fn) || placeholders.contains(sn)) {
             return EvaluationOutcome.fail("Laureate name contains placeholder values", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure names are reasonably long
         if (fn.length() < 2 || sn.length() < 2) {
             return EvaluationOutcome.fail("Laureate firstname and surname must be at least 2 characters", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Year should be a 4-digit plausible year
         try {
             int y = Integer.parseInt(yr);
             int current = Year.now().getValue();
             if (y < 1800 || y > current + 1) { // allow next-year award edgecases
                 return EvaluationOutcome.fail("Laureate award year is out of plausible range", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("Laureate award year must be a numeric year", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Field length checks to avoid obviously malformed records
         if ((entity.getMotivation() != null && entity.getMotivation().trim().length() > 2000) ||
             (entity.getAffiliationName() != null && entity.getAffiliationName().trim().length() > 500)) {
             return EvaluationOutcome.fail("Field length exceeds expected limits", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic duplicate detection note:
         // The real cross-record duplicate detection requires access to persistent storage and is
         // handled by the DeduplicationProcessor. This criterion enforces that records are well-formed
         // and rejects trivially invalid or placeholder-filled records that would otherwise pollute deduplication.
         logger.debug("Laureate {}:{} passed NotDuplicateCriterion heuristics", entity.getId(), identityKey);
         return EvaluationOutcome.success();
    }
}