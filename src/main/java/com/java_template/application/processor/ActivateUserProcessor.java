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
            .validate(this::isValidEntity, "Invalid user for activation")
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

        // Simple activation logic per functional requirements
        if ("Admin".equalsIgnoreCase(user.getRole()) && Boolean.TRUE.equals(user.getCreatedByAdmin())) {
            user.setActive(true);
            user.setEmailVerified(true);
        } else {
            // Customer requires email verification
            user.setActive(false);
            user.setEmailVerified(false);
            // In a full system we would generate a verification token and send an email
            user.setVerificationToken("VERIF-" + user.getId());
            user.setVerificationTokenExpiresAt(Instant.now().plusSeconds(24 * 3600).toString());
        }

        user.setUpdatedAt(Instant.now().toString());

        logger.info("User {} activation processed: active={}, emailVerified={}", user.getId(), user.getActive(), user.getEmailVerified());

        return user;
    }
}
