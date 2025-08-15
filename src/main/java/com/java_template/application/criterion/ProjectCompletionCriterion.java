package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.project.version_1.Project;
import com.java_template.application.entity.task.version_1.Task;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ProjectCompletionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public ProjectCompletionCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
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
            // fetch tasks by project technical id
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.projectId", "EQUALS", project.getId() == null ? "" : project.getId())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Task.ENTITY_NAME,
                String.valueOf(Task.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode tasks = itemsFuture.join();
            // filter out cancelled
            int relevantCount = 0;
            int completedCount = 0;
            for (JsonNode node : tasks) {
                String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
                if (status == null) continue;
                if (status.equalsIgnoreCase("cancelled")) continue;
                relevantCount++;
                if (status.equalsIgnoreCase("completed")) completedCount++;
            }
            if (relevantCount == 0) {
                // require at least one real task to auto-complete
                return EvaluationOutcome.fail("No non-cancelled tasks present, cannot auto-complete project", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (completedCount == relevantCount) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Not all tasks completed", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            logger.error("Error in ProjectCompletionCriterion: ", e);
            return EvaluationOutcome.fail("Error evaluating project completion: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
