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
public class CreateReportSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CreateReportSucceededCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report entity = context.entity();

        // Validate that reportJobId is not null or blank to confirm report creation
        if (entity.getReportJobId() == null || entity.getReportJobId().isBlank()) {
            return EvaluationOutcome.fail("ReportJobId must not be null or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate btcUsdRate and btcEurRate positive
        if (entity.getBtcUsdRate() == null || entity.getBtcUsdRate().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.fail("BTC/USD rate must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getBtcEurRate() == null || entity.getBtcEurRate().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return EvaluationOutcome.fail("BTC/EUR rate must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getTimestamp() == null) {
            return EvaluationOutcome.fail("Timestamp must not be null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
