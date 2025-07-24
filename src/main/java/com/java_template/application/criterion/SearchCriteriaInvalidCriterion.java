package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.PetJob;
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
public class SearchCriteriaInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchCriteriaInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("SearchCriteriaInvalidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SearchCriteriaInvalidCriterion".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetJob entity) {
        String payload = entity.getRequestPayload();
        if (payload == null || payload.isBlank()) {
            return EvaluationOutcome.fail("Search criteria payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (!jsonNode.has("category") || jsonNode.get("category").asText().isBlank()) {
                return EvaluationOutcome.fail("Category is required in search criteria", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!jsonNode.has("status") || jsonNode.get("status").asText().isBlank()) {
                return EvaluationOutcome.fail("Status is required in search criteria", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Invalid JSON payload", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
