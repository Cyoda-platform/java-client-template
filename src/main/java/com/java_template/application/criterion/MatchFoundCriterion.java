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

         if (entity == null) {
             logger.warn("Laureate entity is null in MatchFoundCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String externalId = entity.getExternalId();
         String fullName = entity.getFullName();
         Integer prizeYear = entity.getPrizeYear();
         String prizeCategory = entity.getPrizeCategory();

         // Primary matching strategy: externalId
         if (externalId != null && !externalId.isBlank()) {
             logger.debug("MatchFoundCriterion: externalId present, treating as match for id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // Secondary matching strategy: composite key (fullName + prizeYear + prizeCategory)
         boolean hasFullName = fullName != null && !fullName.isBlank();
         boolean hasPrizeCategory = prizeCategory != null && !prizeCategory.isBlank();
         boolean hasPrizeYear = prizeYear != null;

         if (hasFullName && hasPrizeYear && hasPrizeCategory) {
             logger.debug("MatchFoundCriterion: composite keys present (fullName, prizeYear, prizeCategory) for id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // If neither primary nor secondary identifiers are present, we cannot determine a match.
         StringBuilder msg = new StringBuilder("Insufficient identifiers for matching. Require externalId or (fullName + prizeYear + prizeCategory). Missing:");
         if (!hasFullName) msg.append(" fullName");
         if (!hasPrizeYear) msg.append(" prizeYear");
         if (!hasPrizeCategory) msg.append(" prizeCategory");
         if ((externalId == null || externalId.isBlank()) && !hasFullName && !hasPrizeYear && !hasPrizeCategory) {
             logger.warn("MatchFoundCriterion: {}", msg.toString());
             return EvaluationOutcome.fail(msg.toString(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If some fields present but not enough to match reliably, treat as validation issue.
         logger.warn("MatchFoundCriterion: partial identifiers present but insufficient to assert match for id={}. {}", entity.getId(), msg.toString());
         return EvaluationOutcome.fail(msg.toString(), StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}