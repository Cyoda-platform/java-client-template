package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class DeduplicationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityService entityService;

    public DeduplicationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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
            JsonNode payloadNode = mapper.convertValue(job.getPayload(), JsonNode.class);
            if (payloadNode == null || payloadNode.isNull()) {
                return EvaluationOutcome.fail("payload is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            ArrayNode arr;
            if (payloadNode.isArray()) arr = (ArrayNode) payloadNode;
            else if (payloadNode.isObject()) { arr = mapper.createArrayNode(); arr.add(payloadNode); }
            else return EvaluationOutcome.fail("unsupported payload type", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);

            List<ObjectNode> details = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                ObjectNode d = mapper.createObjectNode();
                d.put("index", i);
                if (item == null || item.isNull() || !item.isObject()) {
                    d.put("action", "FAILED");
                    d.put("reason", "invalid item");
                    details.add(d);
                    continue;
                }
                JsonNode idNode = item.get("id");
                if (idNode == null || idNode.isNull() || !idNode.isNumber()) {
                    d.put("action", "FAILED");
                    d.put("reason", "missing or invalid id");
                    details.add(d);
                    continue;
                }
                long businessId = idNode.asLong();

                SearchConditionRequest cond = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", String.valueOf(businessId))
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    cond,
                    true
                );
                ArrayNode found = itemsFuture.get();
                if (found == null || found.size() == 0) {
                    d.put("action", "CREATE");
                } else {
                    ObjectNode existing = (ObjectNode) found.get(0);
                    JsonNode existingRaw = existing.get("rawJson");
                    JsonNode incomingRaw = item.deepCopy();
                    boolean equal = existingRaw != null && existingRaw.equals(incomingRaw);
                    if (equal) {
                        d.put("action", "IGNORE");
                    } else {
                        d.put("action", "MERGE");
                    }
                }
                details.add(d);
            }

            job.setProcessingDetails(mapper.convertValue(details, Object.class));
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Deduplication check failed: {}", e.getMessage(), e);
            return EvaluationOutcome.fail("dedupe error", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
