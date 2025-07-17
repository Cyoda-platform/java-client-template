package com.java_template.application.criterion;

import com.java_template.application.entity.AnalyzeRequest;
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

@Component
public class AnalyzeRequestValidation implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public AnalyzeRequestValidation(CriterionSerializer serializerFactory) {
        this.serializer = serializerFactory;
        logger.info("AnalyzeRequestValidation initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AnalyzeRequest validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(AnalyzeRequest.class, this::validateAnalyzeRequest)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, analyzeRequest) -> {
                    logger.debug("AnalyzeRequest validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("AnalyzeRequest validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AnalyzeRequestValidation".equals(modelSpec.operationName()) &&
                "analyzeRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateAnalyzeRequest(AnalyzeRequest analyzeRequest) {
        if (analyzeRequest.getTriggerDate() == null || analyzeRequest.getTriggerDate().isEmpty()) {
            return EvaluationOutcome.success();
        }
        // Validate date format YYYY-MM-DD
        boolean matches = analyzeRequest.getTriggerDate().matches("\\d{4}-\\d{2}-\\d{2}");
        if (!matches) {
            return EvaluationOutcome.fail("triggerDate must be in YYYY-MM-DD format");
        }
        return EvaluationOutcome.success();
    }
}
