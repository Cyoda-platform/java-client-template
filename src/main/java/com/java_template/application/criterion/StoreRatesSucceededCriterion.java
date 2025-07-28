package com.java_template.application.criterion;

import com.java_template.application.entity.ReportJob;
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
public class StoreRatesSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StoreRatesSucceededCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {

        ReportJob reportJob = context.entity();

        // Validation: btcUsdRate, btcEurRate, and timestamp must not be null to consider storing succeeded
        if (reportJob.getBtcUsdRate() == null) {
            return EvaluationOutcome.fail("BTC to USD rate is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (reportJob.getBtcEurRate() == null) {
            return EvaluationOutcome.fail("BTC to EUR rate is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (reportJob.getTimestamp() == null) {
            return EvaluationOutcome.fail("Timestamp is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
