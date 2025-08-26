package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class UserVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserVerificationProcessor(SerializerFactory serializerFactory) {
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

        // Ensure a default status exists
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("new");
        }

        // If already verified, nothing to do
        if ("verified".equalsIgnoreCase(user.getStatus()) || "active".equalsIgnoreCase(user.getStatus())) {
            logger.debug("User {} already verified/active", user.getId());
            return user;
        }

        // Automatic verification rules:
        // 1. Valid email format (simple heuristic) -> verified
        // 2. Or valid phone number (digits length >= 7) -> verified
        boolean emailValid = isEmailValid(user);
        boolean phoneValid = isPhoneValid(user);

        if (emailValid) {
            user.setStatus("verified");
            logger.info("User {} verified automatically by email check", user.getId());
        } else if (phoneValid) {
            user.setStatus("verified");
            logger.info("User {} verified automatically by phone check", user.getId());
        } else {
            // Keep as 'new' awaiting manual or email verification
            user.setStatus("new");
            logger.info("User {} remains in 'new' status, verification criteria not met", user.getId());
        }

        return user;
    }

    private boolean isEmailValid(User user) {
        String email = user.getEmail();
        if (email == null) return false;
        email = email.trim();
        int at = email.indexOf('@');
        int lastDot = email.lastIndexOf('.');
        // basic checks: contains '@' and a dot after '@'
        return at > 0 && lastDot > at + 1 && lastDot < email.length() - 1;
    }

    private boolean isPhoneValid(User user) {
        String phone = user.getPhone();
        if (phone == null) return false;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= 7;
    }
}