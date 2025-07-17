package com.java_template.application.criterion;

import com.java_template.application.entity.ReportJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Component
public class ReportJobCompletionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ReportJobCompletionCriterion(CriterionSerializer serializerFactory) {
        this.serializer = serializerFactory;
        logger.info("ReportJobCompletionCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ReportJob completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(ReportJob.class, this::validateCompletion)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler(this::handleError)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReportJobCompletionCriterion".equals(modelSpec.operationName()) &&
                "reportJob".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateCompletion(ReportJob reportJob) {
        if ("completed" .equalsIgnoreCase(reportJob.getStatus()) && reportJob.getCompletedAt() == null) {
            return EvaluationOutcome.fail("ReportJob marked as completed but completedAt is null.");
        }
        return EvaluationOutcome.success();
    }

    private ErrorInfo handleError(Throwable error, ReportJob entity) {
        logger.error("Error validating ReportJob completion", error);
        return ErrorInfo.validationError("ReportJob completion validation failed: " + error.getMessage());
    }
}
