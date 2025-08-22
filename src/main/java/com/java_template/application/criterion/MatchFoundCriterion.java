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
public class MatchFoundCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MatchFoundCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.warn("MatchFoundCriterion: received null entity in context");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic validation: required fields to attempt deduplication must be present
         String externalId = entity.getExternalId();
         String fullName = entity.getFullName();
         Integer prizeYear = entity.getPrizeYear();
         String prizeCategory = entity.getPrizeCategory();

         // If externalId is present, we treat this as a reliable key for matching existing records.
         if (externalId != null && !externalId.isBlank()) {
             logger.debug("MatchFoundCriterion: externalId present ({}). Treating as match-key candidate.", externalId);
             return EvaluationOutcome.success();
         }

         // Heuristic matching: if fullName + prizeYear + prizeCategory present, we can consider this a potential match.
         if (fullName != null && !fullName.isBlank()
             && prizeYear != null
             && prizeCategory != null && !prizeCategory.isBlank()) {
             logger.debug("MatchFoundCriterion: fullName/prizeYear/prizeCategory present ({} / {} / {}). Treating as match-key candidate.",
                 fullName, prizeYear, prizeCategory);
             return EvaluationOutcome.success();
         }

         // Insufficient data to determine a match
         logger.info("MatchFoundCriterion: insufficient identifying information for deduplication. externalId={}, fullName={}, prizeYear={}, prizeCategory={}",
             externalId, fullName, prizeYear, prizeCategory);

         return EvaluationOutcome.fail(
             "Insufficient identifying information to determine existing match (need externalId or fullName+prizeYear+prizeCategory)",
             StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}