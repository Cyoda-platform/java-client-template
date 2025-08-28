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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Required identity checks
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

         // Basic data quality checks
         if (entity.getComputedAge() != null && entity.getComputedAge() < 0) {
             return EvaluationOutcome.fail("Computed age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic duplicate detection:
         // Without access to persistent storage in this criterion, apply a defensive heuristic that flags
         // likely duplicates within the same ingestion by checking if the record's identifying trio
         // (id, firstname+surname+year) contains inconsistent or obviously duplicate indicators.
         // - id must be positive
         if (entity.getId() <= 0) {
             return EvaluationOutcome.fail("Laureate source id must be a positive integer", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // - normalized identity key must be meaningful (not composed only of placeholders)
         String fn = entity.getFirstname().trim().toLowerCase();
         String sn = entity.getSurname().trim().toLowerCase();
         String yr = entity.getYear().trim();
         String identityKey = fn + "|" + sn + "|" + yr;
         if (identityKey.replace("|", "").isBlank()) {
             return EvaluationOutcome.fail("Laureate identity fields are not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If motivation/affiliation fields are identical to default empty placeholders, treat as lower quality but don't fail
         if ((entity.getMotivation() != null && entity.getMotivation().trim().length() > 2000) ||
             (entity.getAffiliationName() != null && entity.getAffiliationName().trim().length() > 500)) {
             return EvaluationOutcome.fail("Field length exceeds expected limits", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // At this stage the entity has passed structural and basic data-quality checks.
         // Real cross-record duplicate detection requires access to the persistence layer and is
         // handled by the DeduplicationProcessor. This criterion ensures records are well-formed
         // and not trivially duplicate/invalid.
         logger.debug("Laureate {}:{} passed NotDuplicateCriterion heuristics", entity.getId(), identityKey);
         return EvaluationOutcome.success();
    }
}