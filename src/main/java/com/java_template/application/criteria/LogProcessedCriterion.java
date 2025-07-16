package com.java_template.application.criteria;

import com.java_template.application.entity.log.Log;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class LogProcessedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public LogProcessedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("LogProcessedCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Log processed validity for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Log.class, this::validateLogProcessed)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .withErrorHandler((error, log) -> {
                    logger.debug("Log processed validation failed for request: {}", request.getId(), error);
                    return ErrorInfo.validationError("Log processed validation failed: " + error.getMessage());
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "log".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateLogProcessed(Log log) {
        // Example validation: Log must be marked processed to pass criterion
        if (log.getProcessed() != null && log.getProcessed()) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.failure("Log is not processed yet");
        }
    }
}
