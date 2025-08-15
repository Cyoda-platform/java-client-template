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

@Component
public class UserPostProcessingNotifier implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserPostProcessingNotifier.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserPostProcessingNotifier(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User post-processing notifications for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user for post-processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.getStatus() != null;
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        try {
            // For prototype: log welcome email conditionally
            if ("Active".equalsIgnoreCase(user.getStatus()) && "Customer".equalsIgnoreCase(user.getRole())) {
                logger.info("Would send welcome email to {}", user.getEmail());
            }
        } catch (Exception e) {
            logger.error("Error during user post-processing notification: {}", e.getMessage(), e);
        }
        return user;
    }
}
