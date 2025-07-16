package com.java_template.application.processor;

import com.java_template.application.entity.log.Log;
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
public class ProcessLogProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public ProcessLogProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ProcessLogProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Log for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Log.class)
                .withErrorHandler(this::handleLogError)
                .validate(this::isValidLog, "Invalid Log state")
                .map(this::processLogEntry)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "log".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidLog(Log log) {
        // Add validation logic for Log
        return log != null && log.getId() != null;
    }

    private Log processLogEntry(Log log) {
        // Implement processing logic for Log
        log.setProcessed(true);
        return log;
    }

    private ErrorInfo handleLogError(Throwable error, Log log) {
        logger.error("Error processing Log: {}", error.getMessage(), error);
        return new ErrorInfo("LogProcessingError", error.getMessage());
    }
}
