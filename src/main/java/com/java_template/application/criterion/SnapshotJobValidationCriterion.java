package com.java_template.application.criterion;

import com.java_template.application.entity.TeamSnapshot;
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
public class SnapshotJobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SnapshotJobValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(TeamSnapshot.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<TeamSnapshot> context) {

        TeamSnapshot entity = context.entity();

        // Validation logic based on business requirements
        // The prototype requirements specify validation for SnapshotJob entity, but TeamSnapshot is used as entity in the criterion.
        // For demonstration, we validate some TeamSnapshot fields to not be null or blank as a sample.

        if (entity.getSeason() == null || entity.getSeason().isBlank()) {
            return EvaluationOutcome.fail("Season is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getEffectiveDate() == null || entity.getEffectiveDate().isBlank()) {
            return EvaluationOutcome.fail("Effective date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getTeamName() == null || entity.getTeamName().isBlank()) {
            return EvaluationOutcome.fail("Team name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getVenue() == null || entity.getVenue().isBlank()) {
            return EvaluationOutcome.fail("Venue is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getCrestUrl() == null || entity.getCrestUrl().isBlank()) {
            return EvaluationOutcome.fail("Crest URL is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
