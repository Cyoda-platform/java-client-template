package com.java_template.application.criterion;

import com.java_template.application.entity.ExternalApiData;
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
public class ExternalApiCallFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ExternalApiCallFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ExternalApiCallFailureCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(ExternalApiData.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ExternalApiCallFailureCriterion".equals(modelSpec.operationName()) &&
                "externalApiData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ExternalApiData entity) {
        if (entity.getResponseData() == null || entity.getResponseData().isBlank()) {
            return EvaluationOutcome.fail("responseData is missing or empty, indicating a failure", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        // Example failure condition: fetchedAt is null or in the future
        if (entity.getFetchedAt() == null) {
            return EvaluationOutcome.fail("fetchedAt timestamp is missing, indicating a failure", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getFetchedAt().isAfter(java.time.Instant.now())) {
            return EvaluationOutcome.fail("fetchedAt timestamp is in the future, indicating invalid data", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
