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
public class ReportCreatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportCreatedCriterion(SerializerFactory serializerFactory) {
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

        // Validate that rates and timestamp are present and valid
        if (entity.getBtcUsdRate() == null) {
            return EvaluationOutcome.fail("BTC/USD rate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getBtcUsdRate().doubleValue() <= 0) {
            return EvaluationOutcome.fail("BTC/USD rate must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getBtcEurRate() == null) {
            return EvaluationOutcome.fail("BTC/EUR rate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getBtcEurRate().doubleValue() <= 0) {
            return EvaluationOutcome.fail("BTC/EUR rate must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getTimestamp() == null) {
            return EvaluationOutcome.fail("Timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
