package com.java_template.application.processor;

import com.java_template.application.entity.CyodaEnvJob;
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

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class CyodaEnvJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CyodaEnvJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("CyodaEnvJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CyodaEnvJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(CyodaEnvJob.class)
                .withErrorHandler(this::handleCyodaEnvJobError)
                .validate(this::isValidEntity, "Invalid CyodaEnvJob state")
                // Placeholder for additional transformations or validations
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CyodaEnvJobProcessor".equals(modelSpec.operationName()) &&
               "cyodaEnvJob".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(CyodaEnvJob entity) {
        return entity.isValid();
    }

    private ErrorInfo handleCyodaEnvJobError(Throwable throwable, CyodaEnvJob entity) {
        logger.error("Error processing CyodaEnvJob: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }
}
