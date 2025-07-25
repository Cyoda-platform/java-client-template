package com.java_template.application.criterion;

import com.java_template.application.entity.HNItem;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HNItemTypeAndIdCriterionNegation implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public HNItemTypeAndIdCriterionNegation(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("HNItemTypeAndIdCriterionNegation initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HNItemTypeAndIdCriterionNegation".equals(modelSpec.operationName()) &&
               "hnItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(HNItem entity) {
        // This negation criterion fails if the positive criterion passes
        if (entity.getPayload() != null && !entity.getPayload().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(entity.getPayload());

                boolean hasType = jsonNode.hasNonNull("type") && !jsonNode.get("type").asText().isBlank();
                boolean hasId = jsonNode.hasNonNull("id") && !jsonNode.get("id").asText().isBlank();

                if (hasType && hasId) {
                    return EvaluationOutcome.fail("Payload has both 'type' and 'id' fields, negation fails", StandardEvalReasonCategories.VALIDATION_FAILURE);
                } else {
                    return EvaluationOutcome.success();
                }
            } catch (Exception e) {
                return EvaluationOutcome.success(); // If invalid JSON, consider negation pass
            }
        }
        // If payload null or blank, negation passes
        return EvaluationOutcome.success();
    }
}
