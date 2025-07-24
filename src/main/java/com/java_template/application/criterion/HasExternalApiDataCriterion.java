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
public class HasExternalApiDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public HasExternalApiDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("HasExternalApiDataCriterion initialized with SerializerFactory");
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
        return "HasExternalApiDataCriterion".equals(modelSpec.operationName()) &&
               "externalApiData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(ExternalApiData entity) {
        if (entity.getJobTechnicalId() == null || entity.getJobTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("JobTechnicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getDataPayload() == null || entity.getDataPayload().isBlank()) {
            return EvaluationOutcome.fail("DataPayload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) {
            return EvaluationOutcome.fail("RetrievedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
