package com.java_template.application.processor;

import com.java_template.application.entity.User;
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
public class UserProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public UserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("UserProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(User.class)
                .withErrorHandler(this::handleUserError)
                .validate(this::isValidUser, "Invalid User entity state")
                // Add your business logic transformations here if any
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "UserProcessor".equals(modelSpec.operationName()) &&
                "user".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidUser(User user) {
        return user.isValid();
    }

    private ErrorInfo handleUserError(Throwable throwable, User user) {
        logger.error("Error processing User entity", throwable);
        return new ErrorInfo("UserProcessingError", throwable.getMessage());
    }
}
