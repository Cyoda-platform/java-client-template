package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.importtask.version_1.ImportTask;
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
public class MonitorTasksCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public MonitorTasksCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            if (job == null) {
                return EvaluationOutcome.fail("ImportJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            // Query tasks for this job
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobTechnicalId", "EQUALS", job.getTechnicalId())
            );
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode tasks = itemsFuture.get();
            if (tasks == null || tasks.size() == 0) {
                return EvaluationOutcome.success(); // no tasks created yet -> keep in progress
            }

            boolean allSucceeded = true;
            boolean anyFailed = false;
            for (JsonNode t : tasks) {
                String status = t.has("status") && !t.get("status").isNull() ? t.get("status").asText() : null;
                if (status == null) continue;
                if (!"SUCCEEDED".equalsIgnoreCase(status)) allSucceeded = false;
                if ("FAILED".equalsIgnoreCase(status)) anyFailed = true;
            }

            if (allSucceeded) {
                return EvaluationOutcome.success(); // All tasks succeeded -> job can move to completed
            }
            if (anyFailed) {
                return EvaluationOutcome.fail("One or more tasks failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error monitoring tasks for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Error monitoring tasks: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
