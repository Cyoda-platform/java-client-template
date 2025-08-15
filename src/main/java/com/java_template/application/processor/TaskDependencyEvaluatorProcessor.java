package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class TaskDependencyEvaluatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TaskDependencyEvaluatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TaskDependencyEvaluatorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Task (Dependency Evaluation) for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Task.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Task task) {
        return task != null && task.isValid();
    }

    private Task processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Task> context) {
        Task task = context.entity();
        try {
            List<String> deps = task.getDependencies();
            if (deps == null || deps.isEmpty()) {
                // nothing to evaluate
                return task;
            }

            // fetch all tasks for this project to resolve dependencies (in-memory filter)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.projectId", "EQUALS", task.getProjectId() == null ? "" : task.getProjectId())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Task.ENTITY_NAME,
                String.valueOf(Task.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = itemsFuture.join();

            List<String> blockingDeps = new ArrayList<>();
            for (String dep : deps) {
                boolean found = false;
                boolean satisfied = false;
                for (JsonNode node : items) {
                    String candidateId = node.has("technicalId") && !node.get("technicalId").isNull() ? node.get("technicalId").asText() : null;
                    if (candidateId == null) candidateId = node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null;
                    if (candidateId != null && candidateId.equals(dep)) {
                        found = true;
                        String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
                        if ("completed".equalsIgnoreCase(status)) {
                            satisfied = true;
                        }
                        break;
                    }
                }
                if (!found) {
                    blockingDeps.add(dep); // missing dependency considered blocking
                } else if (!satisfied) {
                    blockingDeps.add(dep); // not yet completed
                }
            }

            if (!blockingDeps.isEmpty()) {
                task.setStatus("blocked");
                if (task.getMetadata() == null) task.setMetadata(new java.util.HashMap<>());
                task.getMetadata().put("blockingDependencies", blockingDeps);
                task.getMetadata().put("dependencyCheckedAt", Instant.now().toString());
                task.setUpdatedAt(Instant.now().toString());
            } else {
                // dependencies satisfied
                if ("blocked".equalsIgnoreCase(task.getStatus())) {
                    // restore to assigned if there is an assignee, otherwise pending
                    if (task.getAssigneeId() != null && !task.getAssigneeId().isBlank()) {
                        task.setStatus("assigned");
                    } else {
                        task.setStatus("pending");
                    }
                    task.setUpdatedAt(Instant.now().toString());
                    if (task.getMetadata() != null) {
                        task.getMetadata().remove("blockingDependencies");
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in TaskDependencyEvaluatorProcessor: ", e);
            if (task != null) {
                if (task.getMetadata() == null) task.setMetadata(new java.util.HashMap<>());
                task.getMetadata().put("lastProcessorError", e.getMessage());
            }
        }
        return task;
    }
}
