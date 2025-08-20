package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.activity.version_1.Activity;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class IngestionCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public IngestionCompleteCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
        Job job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("Job missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Map<String, Object> params = job.getParameters();
        if (params == null) {
            return EvaluationOutcome.fail("Job parameters missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            // For demo: consider ingestion complete when at least one activity exists with sourceJobId == job.technicalId
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                return EvaluationOutcome.fail("No activities ingested", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            for (int i = 0; i < items.size(); i++) {
                ObjectNode node = (ObjectNode) items.get(i);
                if (node.has("sourceJobId") && job.getTechnicalId().equals(node.get("sourceJobId").asText())) {
                    // found at least one activity for job
                    return EvaluationOutcome.success();
                }
            }

            return EvaluationOutcome.fail("No activities for job", StandardEvalReasonCategories.VALIDATION_FAILURE);

        } catch (Exception ex) {
            logger.error("Error evaluating ingestion completeness for job {}: {}", job.getTechnicalId(), ex.getMessage(), ex);
            return EvaluationOutcome.fail("Error checking ingestion store", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
