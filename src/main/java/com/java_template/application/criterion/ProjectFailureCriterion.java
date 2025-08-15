package com.java_template.application.criterion;

import com.java_template.application.entity.project.version_1.Project;
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

import java.util.Map;

@Component
public class ProjectFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // configurable threshold; hardcode reasonable default
    private final int FAILURE_THRESHOLD = 3;

    public ProjectFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Project.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Project> context) {
        Project project = context.entity();
        try {
            Map<String, Object> metadata = project.getMetadata();
            if (metadata == null) metadata = java.util.Collections.emptyMap();
            Object val = metadata.getOrDefault("failureCount", 0);
            int failures = 0;
            if (val instanceof Number) failures = ((Number) val).intValue();
            else if (val instanceof String) {
                try { failures = Integer.parseInt((String) val); } catch (NumberFormatException ex) { failures = 0; }
            }
            if (failures >= FAILURE_THRESHOLD) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Failure threshold not reached", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            logger.error("Error in ProjectFailureCriterion: ", e);
            return EvaluationOutcome.fail("Error evaluating project failure: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
