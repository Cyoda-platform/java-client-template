package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class DeduplicationIgnoredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper mapper = new ObjectMapper();

    public DeduplicationIgnoredCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
            Object details = job.getProcessingDetails();
            if (details == null) return EvaluationOutcome.fail("no processing details", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            JsonNode detailsNode = mapper.convertValue(details, JsonNode.class);
            boolean hasIgnored = false;
            if (detailsNode.isArray()) {
                for (JsonNode d : detailsNode) {
                    if (d.has("action") && "IGNORE".equalsIgnoreCase(d.get("action").asText())) {
                        hasIgnored = true; break;
                    }
                    if (d.has("outcome") && "IGNORED".equalsIgnoreCase(d.get("outcome").asText())) { hasIgnored = true; break; }
                }
            }
            if (hasIgnored) return EvaluationOutcome.success();
            return EvaluationOutcome.fail("no ignored items", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        } catch (Exception e) {
            logger.error("DeduplicationIgnoredCriterion failed: {}", e.getMessage(), e);
            return EvaluationOutcome.fail("error checking ignored", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
