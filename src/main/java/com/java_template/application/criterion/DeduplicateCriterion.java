package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.concurrent.CompletionException;

@Component
public class DeduplicateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public DeduplicateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
        Laureate l = context.entity();
        if (l == null) return EvaluationOutcome.fail("Laureate not provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        // Simple deduplication: check existing laureates by source id equality
        try {
            ArrayNode items = entityService.getItems(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION)
            ).join();

            if (items != null) {
                Iterator<JsonNode> it = items.elements();
                while (it.hasNext()) {
                    JsonNode node = it.next();
                    if (node != null && node.has("id") && node.get("id").asInt() == (l.getId() == null ? -1 : l.getId())) {
                        // duplicate found
                        return EvaluationOutcome.fail("Duplicate laureate found by source id", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                    }
                }
            }
        } catch (CompletionException ce) {
            logger.error("Error fetching laureates for deduplication: {}", ce.getMessage(), ce);
            return EvaluationOutcome.fail("Deduplication check failed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
