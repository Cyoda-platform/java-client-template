package com.java_template.application.processor;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class ProjectEnterInProgressProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProjectEnterInProgressProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProjectEnterInProgressProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Project (EnterInProgress) for request: {}", request.getId());

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
            if (project.getStatus() != null && (project.getStatus().equalsIgnoreCase("in_progress") || project.getStatus().equalsIgnoreCase("completed"))) {
                return project; // idempotent
            }

            // check if any Task exists in in_progress for this project
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.projectId", "EQUALS", project.getId())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Task.ENTITY_NAME,
                String.valueOf(Task.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode tasks = itemsFuture.join();
            boolean anyInProgress = false;
            for (JsonNode node : tasks) {
                String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
                if ("in_progress".equalsIgnoreCase(status)) {
                    anyInProgress = true;
                    break;
                }
            }
            if (anyInProgress) {
                project.setStatus("in_progress");
                String now = Instant.now().toString();
                project.setUpdatedAt(now);
            }
        } catch (Exception e) {
            logger.error("Error in ProjectEnterInProgressProcessor: ", e);
            if (project != null) {
                if (project.getMetadata() == null) project.setMetadata(new java.util.HashMap<>());
                project.getMetadata().put("lastProcessorError", e.getMessage());
            }
        }
        return project;
    }
}
