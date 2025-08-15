package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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
import java.util.concurrent.TimeUnit;

@Component
public class ImportJobCompletionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public ImportJobCompletionCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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
        if (job == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Use jobName as the jobTechnicalId surrogate (ImportJobProcessor sets ImportTask.jobTechnicalId = job.getJobName())
        String jobRef = job.getJobName();
        if (jobRef == null || jobRef.isBlank()) {
            return EvaluationOutcome.fail("jobName is required to evaluate completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                com.java_template.application.entity.importtask.version_1.ImportTask.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.importtask.version_1.ImportTask.ENTITY_VERSION),
                SearchConditionRequest.group("AND", Condition.of("$.jobTechnicalId", "EQUALS", jobRef)),
                true
            );
            ArrayNode tasks = future.get(10, TimeUnit.SECONDS);
            if (tasks == null || tasks.size() == 0) {
                return EvaluationOutcome.fail("No tasks found for job", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            boolean allSucceeded = true;
            for (int i = 0; i < tasks.size(); i++) {
                ObjectNode t = (ObjectNode) tasks.get(i);
                String status = t.has("status") && !t.get("status").isNull() ? t.get("status").asText() : "";
                if (!"SUCCEEDED".equalsIgnoreCase(status)) {
                    allSucceeded = false;
                    break;
                }
            }
            if (allSucceeded) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("Not all tasks succeeded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        } catch (Exception e) {
            logger.warn("Failed to evaluate import job completion for job {}: {}", jobRef, e.getMessage());
            return EvaluationOutcome.fail("Failed to evaluate related tasks", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
