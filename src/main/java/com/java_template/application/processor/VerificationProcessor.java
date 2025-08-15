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
public class VerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Verification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid user entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User user) {
        return user != null && user.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();
        try {
            // If already verified or beyond, idempotent
            if ("verified".equalsIgnoreCase(user.getStatus()) || "active".equalsIgnoreCase(user.getStatus())) {
                logger.info("User {} already verified or active", user.getId());
                return user;
            }

            // Basic verification logic: mark verified if contact info exists
            boolean hasEmail = user.getEmail() != null && !user.getEmail().isEmpty();
            boolean hasPhone = user.getPhone() != null && !user.getPhone().isEmpty();

            if (!hasEmail && !hasPhone) {
                logger.warn("Cannot verify user {}: no contact methods", user.getId());
                return user;
            }

            user.setStatus("verified");
            user.setUpdatedAt(Instant.now().toString());
            logger.info("User {} marked as verified", user.getId());
        } catch (Exception e) {
            logger.error("Error in VerificationProcessor for user {}: {}", user.getId(), e.getMessage(), e);
        }
        return user;
    }
}
