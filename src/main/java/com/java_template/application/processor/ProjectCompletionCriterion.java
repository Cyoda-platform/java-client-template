package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.project.version_1.Project;
import com.java_template.application.entity.task.version_1.Task;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ProjectCompletionCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProjectCompletionCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProjectCompletionCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Project (Completion Check) for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Project.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Project project) {
        return project != null && project.isValid();
    }

    private Project processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Project> context) {
        Project project = context.entity();
        try {
            // if already completed, idempotent
            if (project.getStatus() != null && project.getStatus().equalsIgnoreCase("completed")) {
                return project;
            }

            // fetch tasks for this project by business id
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
                // no actionable tasks -> do not auto-complete
                return project;
            }

            if (completedCount == relevantCount) {
                project.setStatus("completed");
                String now = Instant.now().toString();
                project.setCompletedAt(now);
                project.setUpdatedAt(now);
            }

        } catch (Exception e) {
            logger.error("Error in ProjectCompletionCriterion processor: ", e);
            if (project != null) {
                if (project.getMetadata() == null) project.setMetadata(new java.util.HashMap<>());
                project.getMetadata().put("lastProcessorError", e.getMessage());
            }
        }
        return project;
    }
}
