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
public class CompleteProfileProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CompleteProfileProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteProfileProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CompleteProfile for request: {}", request.getId());

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
        return entity != null;
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        if (user == null) return null;
        try {
            // Mark profile as complete if required fields are present
            boolean hasName = user.getName() != null && !user.getName().trim().isEmpty();
            boolean hasEmail = user.getEmail() != null && !user.getEmail().trim().isEmpty();

            if (hasName && hasEmail) {
                user.setLifecycleState("PROFILE_COMPLETE");
            }
            user.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error completing profile for user {}: {}", user.getTechnicalId(), e.getMessage(), e);
            user.setUpdatedAt(Instant.now().toString());
        }
        return user;
    }
}
