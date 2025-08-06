package com.java_template.application.criterion;

import com.java_template.application.entity.SquadSnapshot;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsValidSquadSnapshot implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidSquadSnapshot(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(SquadSnapshot.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SquadSnapshot> context) {

        SquadSnapshot entity = context.entity();

        // Validate teamSnapshotId is present
        if (entity.getTeamSnapshotId() == null || entity.getTeamSnapshotId().isBlank()) {
            return EvaluationOutcome.fail("Team snapshot ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate playerName is present
        if (entity.getPlayerName() == null || entity.getPlayerName().isBlank()) {
            return EvaluationOutcome.fail("Player name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate position is present
        if (entity.getPosition() == null || entity.getPosition().isBlank()) {
            return EvaluationOutcome.fail("Position is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate dateOfBirth is present
        if (entity.getDateOfBirth() == null || entity.getDateOfBirth().isBlank()) {
            return EvaluationOutcome.fail("Date of birth is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate nationality is present
        if (entity.getNationality() == null || entity.getNationality().isBlank()) {
            return EvaluationOutcome.fail("Nationality is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate playerId is present and positive
        if (entity.getPlayerId() == null || entity.getPlayerId() <= 0) {
            return EvaluationOutcome.fail("Player ID must be a positive number", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
