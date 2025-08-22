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
public class VerifyIdentityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyIdentityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyIdentityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

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
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        // Business intent:
        // - Trigger identity verification flow: if minimal contact info present, emulate verification steps.
        // - Update timestamps and apply sensible defaults if missing.
        // Note: The User entity in this prototype does not contain a "status" field, so we cannot set status here.
        // We operate only on available fields (role, updatedAt, etc.) and log verification state.

        // Ensure updatedAt reflects processing time
        String now = Instant.now().toString();
        user.setUpdatedAt(now);

        // Basic heuristic: if phone is present, treat as contact-verified candidate
        boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();

        if (hasEmail && hasPhone) {
            // Both contact channels present — consider identity verification succeeded by automated checks.
            // Apply a sensible default role if not set.
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("customer");
            }
            logger.info("User [{}] identity verification succeeded (email+phone present)", user.getId());
        } else if (hasEmail) {
            // Email-only: mark for manual verification (log). Keep role if present; otherwise leave unset.
            logger.info("User [{}] requires additional verification (email only)", user.getId());
        } else {
            // Neither email nor phone: cannot verify. Log and leave user unchanged except updatedAt.
            logger.warn("User [{}] missing contact information; verification cannot proceed", user.getId());
        }

        return user;
    }
}