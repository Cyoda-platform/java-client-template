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

import java.util.List;
import java.util.Objects;

@Component
public class VerifyContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Basic contact verification:
        // - Consider email verified if it contains '@' (simple check)
        // - Consider phone verified if present and has at least 7 digits (sanitized)
        boolean emailOk = false;
        boolean phoneOk = false;

        if (user.getEmail() != null) {
            emailOk = user.getEmail().contains("@");
        }

        if (user.getPhone() != null) {
            String digits = user.getPhone().replaceAll("\\D", "");
            phoneOk = digits.length() >= 7;
        }

        // Use preferences list as a place to record verification status markers
        List<String> prefs = user.getPreferences();
        if (prefs == null) {
            // defensive: if null, create a new list (entity will be persisted)
            // but keep null-check minimal since User initializes it by default
            prefs = new java.util.ArrayList<>();
            user.setPreferences(prefs);
        }

        // Remove any previous markers about contact verification to avoid duplicates
        prefs.removeIf(Objects::isNull);
        prefs.removeIf(s -> s.equalsIgnoreCase("contact_verified"));
        prefs.removeIf(s -> s.equalsIgnoreCase("contact_verification_failed"));
        prefs.removeIf(s -> s.equalsIgnoreCase("contact_verification_needed"));

        if (emailOk && phoneOk) {
            prefs.add("contact_verified");
            logger.info("User {} contact verification passed (email and phone).", user.getId());
        } else {
            // Mark as needing verification; keep processor idempotent by using same marker
            prefs.add("contact_verification_needed");
            logger.info("User {} contact verification failed or incomplete. emailOk={}, phoneOk={}", user.getId(), emailOk, phoneOk);
        }

        // No updates to other entities should be performed here per rules.
        // The entity will be persisted automatically by Cyoda based on workflow.

        return user;
    }
}