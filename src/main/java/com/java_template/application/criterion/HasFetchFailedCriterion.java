package com.java_template.application.criterion;

import com.java_template.application.entity.Report;
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
public class HasFetchFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public HasFetchFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("HasFetchFailedCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HasFetchFailedCriterion".equals(modelSpec.operationName()) &&
               "report".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Report entity) {
        // Business logic: Fail if any critical field for fetch is missing or invalid
        if (entity.getJobTechnicalId() == null || entity.getJobTechnicalId().isBlank()) {
            return EvaluationOutcome.fail("JobTechnicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getGeneratedAt() == null) {
            return EvaluationOutcome.fail("GeneratedAt timestamp is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getBtcUsdRate() == null || entity.getBtcEurRate() == null) {
            return EvaluationOutcome.fail("BTC rates are incomplete", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getEmailSent() == null) {
            return EvaluationOutcome.fail("EmailSent flag is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional logic could be added here to detect fetch failure conditions
        return EvaluationOutcome.success();
    }
}
