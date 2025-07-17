package com.java_template.application.processor;

import com.java_template.application.entity.UserAppJob;
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
public class UserAppJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public UserAppJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("UserAppJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UserAppJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(UserAppJob.class)
                .withErrorHandler(this::handleUserAppJobError)
                .validate(this::isValidEntity, "Invalid UserAppJob state")
                // Placeholder for additional transformations or validations
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "UserAppJobProcessor".equals(modelSpec.operationName()) &&
               "userAppJob".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(UserAppJob entity) {
        return entity.isValid();
    }

    private ErrorInfo handleUserAppJobError(Throwable throwable, UserAppJob entity) {
        logger.error("Error processing UserAppJob: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }
}
