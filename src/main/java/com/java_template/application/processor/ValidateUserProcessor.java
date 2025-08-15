package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateUser for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.getId() != null;
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        // Basic validation: ensure createdAt and flags
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now().toString());
        }

        // Default flags
        if (user.getActive() == null) {
            user.setActive(false);
        }

        logger.info("User {} validated", user.getId());

        return user;
    }
}
