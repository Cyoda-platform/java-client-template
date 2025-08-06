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
public class SquadSnapshotNoChanges implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SquadSnapshotNoChanges(SerializerFactory serializerFactory) {
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

        // This criterion succeeds when no changes are detected (opposite of SquadSnapshotChangesDetected)
        
        // Check if entity is incomplete or not fully processed
        if (entity.getTeamSnapshotId() == null || entity.getTeamSnapshotId().isBlank() ||
            entity.getPlayerName() == null || entity.getPlayerName().isBlank() ||
            entity.getPosition() == null || entity.getPosition().isBlank() ||
            entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            
            // No changes detected - entity is incomplete
            return EvaluationOutcome.success();
        }

        // Changes were detected - entity has been fully processed
        return EvaluationOutcome.fail("Changes detected in SquadSnapshot processing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
