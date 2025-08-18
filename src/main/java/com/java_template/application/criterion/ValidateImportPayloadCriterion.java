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

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidateImportPayloadCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidateImportPayloadCriterion(SerializerFactory serializerFactory) {
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
            JsonNode payloadNode = mapper.convertValue(job.getPayload(), JsonNode.class);
            List<ObjectNode> errors = new ArrayList<>();

            if (payloadNode == null || payloadNode.isNull()) {
                ObjectNode e = mapper.createObjectNode();
                e.put("index", -1);
                e.put("message", "payload is null");
                errors.add(e);
                job.setProcessingDetails(mapper.convertValue(errors, Object.class));
                job.setStatus("FAILED");
                job.setErrorMessage("payload is null");
                return EvaluationOutcome.fail("payload is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            ArrayNode arr;
            if (payloadNode.isArray()) {
                arr = (ArrayNode) payloadNode;
            } else if (payloadNode.isObject()) {
                arr = mapper.createArrayNode();
                arr.add(payloadNode);
            } else {
                job.setProcessingDetails(mapper.convertValue(new ArrayList<>(), Object.class));
                job.setStatus("FAILED");
                job.setErrorMessage("unsupported payload type");
                return EvaluationOutcome.fail("unsupported payload type", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                ObjectNode err = mapper.createObjectNode();
                err.put("index", i);
                if (item == null || item.isNull() || !item.isObject()) {
                    err.put("message", "item is null or not an object");
                    errors.add(err);
                    continue;
                }
                JsonNode idNode = item.get("id");
                if (idNode == null || idNode.isNull()) {
                    err.put("message", "missing id");
                    errors.add(err);
                    continue;
                }
                if (!idNode.isNumber()) {
                    err.put("message", "id must be numeric");
                    errors.add(err);
                }
                JsonNode typeNode = item.get("type");
                if (typeNode == null || typeNode.isNull()) {
                    err.put("message", "missing type");
                    errors.add(err);
                }
            }

            if (!errors.isEmpty()) {
                job.setProcessingDetails(mapper.convertValue(errors, Object.class));
                job.setStatus("FAILED");
                job.setErrorMessage("validation failed");
                return EvaluationOutcome.fail("validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // validation success - advance status to PROCESSING
            job.setStatus("PROCESSING");
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Validation error on ImportJob {}: {}", context.requestId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            return EvaluationOutcome.fail("validation error", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
