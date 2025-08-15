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
public class AllTasksSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public AllTasksSucceededCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobTechnicalId", "EQUALS", job.getTechnicalId())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ImportTask.ENTITY_NAME,
                String.valueOf(ImportTask.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode tasks = itemsFuture.get();
            if (tasks == null || tasks.size() == 0) {
                return EvaluationOutcome.fail("No tasks found for job", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            for (JsonNode t : tasks) {
                String status = t.has("status") && !t.get("status").isNull() ? t.get("status").asText() : null;
                if (!"SUCCEEDED".equalsIgnoreCase(status)) {
                    return EvaluationOutcome.fail("Not all tasks succeeded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error evaluating AllTasksSucceededCriterion for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Error evaluating tasks: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
