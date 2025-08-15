package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
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

@Component
public class ProcessingErrorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public ProcessingErrorCriterion(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.objectMapper = objectMapper;
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
        if (job == null) return EvaluationOutcome.fail("Job payload missing", StandardEvalReasonCategories.VALIDATION_FAILURE);

        String rsStr = job.getResultSummary();
        if (rsStr == null || rsStr.isBlank()) return EvaluationOutcome.success();

        try {
            JsonNode rs = objectMapper.readTree(rsStr);
            if (rs.has("failed") && rs.get("failed").isNumber()) {
                if (rs.get("failed").asInt() > 0) {
                    return EvaluationOutcome.fail("Processing errors detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to parse resultSummary for job {}: {}", job.getTechnicalId(), e.getMessage());
            // If parsing fails, conservatively succeed so as not to block flow
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.success();
    }
}
