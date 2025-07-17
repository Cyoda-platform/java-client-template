package com.java_template.application.processor;

import com.java_template.application.entity.AnalyzeRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AnalyzeRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AnalyzeRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyzeRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AnalyzeRequest.class)
                .withErrorHandler(this::handleAnalyzeRequestError)
                .validate(AnalyzeRequest::isValid, "Invalid AnalyzeRequest entity state")
                .complete();
    }

    private ErrorInfo handleAnalyzeRequestError(Throwable t, AnalyzeRequest analyzeRequest) {
        logger.error("Error processing AnalyzeRequest entity", t);
        return new ErrorInfo("AnalyzeRequestProcessingError", t.getMessage());
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AnalyzeRequestProcessor".equals(modelSpec.operationName()) &&
                "analyzeRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
