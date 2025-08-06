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
public class IsInvalidSquadSnapshot implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsInvalidSquadSnapshot(SerializerFactory serializerFactory) {
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

        // This criterion succeeds when the entity is invalid (opposite of IsValidSquadSnapshot)
        
        // Check for missing teamSnapshotId
        if (entity.getTeamSnapshotId() == null || entity.getTeamSnapshotId().isBlank()) {
            return EvaluationOutcome.success(); // Success means invalid entity detected
        }

        // Check for missing playerName
        if (entity.getPlayerName() == null || entity.getPlayerName().isBlank()) {
            return EvaluationOutcome.success();
        }

        // Check for missing position
        if (entity.getPosition() == null || entity.getPosition().isBlank()) {
            return EvaluationOutcome.success();
        }

        // Check for missing dateOfBirth
        if (entity.getDateOfBirth() == null || entity.getDateOfBirth().isBlank()) {
            return EvaluationOutcome.success();
        }

        // Check for missing nationality
        if (entity.getNationality() == null || entity.getNationality().isBlank()) {
            return EvaluationOutcome.success();
        }

        // Check for invalid playerId
        if (entity.getPlayerId() == null || entity.getPlayerId() <= 0) {
            return EvaluationOutcome.success();
        }

        // If all validations pass, the entity is valid, so this criterion fails
        return EvaluationOutcome.fail("SquadSnapshot entity is valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
