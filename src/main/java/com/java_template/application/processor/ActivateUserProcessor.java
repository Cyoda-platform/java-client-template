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
public class ActivateUserProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ActivateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ActivateUser for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && ("PROFILE_COMPLETE".equalsIgnoreCase(entity.getLifecycleState()) || (entity.getEmail() != null && !userEmailBlank(entity)));
    }

    private boolean userEmailBlank(User user) {
        return user.getEmail() == null || user.getEmail().trim().isEmpty();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        if (user == null) return null;
        try {
            user.setLifecycleState("ACTIVE");
            user.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error activating user {}: {}", user.getTechnicalId(), e.getMessage(), e);
            user.setUpdatedAt(Instant.now().toString());
        }
        return user;
    }
}
