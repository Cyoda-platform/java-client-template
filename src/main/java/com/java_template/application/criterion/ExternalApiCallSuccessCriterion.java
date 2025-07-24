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
public class ExternalApiCallSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ExternalApiCallSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ExternalApiCallSuccessCriterion initialized with SerializerFactory");
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
        return "ExternalApiCallSuccessCriterion".equals(modelSpec.operationName()) &&
                "externalApiData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ExternalApiData entity) {
        if (entity.getJobTechnicalId() == null || entity.getJobTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("jobTechnicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getApiEndpoint() == null || entity.getApiEndpoint().isBlank()) {
            return EvaluationOutcome.fail("apiEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getResponseData() == null || entity.getResponseData().isBlank()) {
            return EvaluationOutcome.fail("responseData is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getFetchedAt() == null) {
            return EvaluationOutcome.fail("fetchedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
