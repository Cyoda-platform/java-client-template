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
        if (user == null) {
            logger.warn("User entity is null in execution context");
            return null;
        }

        boolean emailOk = false;
        if (user.getEmail() != null) {
            String email = user.getEmail().trim();
            emailOk = !email.isBlank() && email.contains("@") && !email.startsWith("@") && !email.endsWith("@");
        }

        boolean contactOk = user.getContact() != null && !user.getContact().isBlank();

        // Basic automatic verification rules:
        // - If both email and contact look valid, mark as verified
        // - Otherwise, mark as not verified (pending manual review)
        Boolean before = user.getVerified();
        if (emailOk && contactOk) {
            user.setVerified(Boolean.TRUE);
            user.setNotes(appendNote(user.getNotes(), "User auto-verified by UserVerificationProcessor"));
            logger.info("User [{}] auto-verified (emailOk={}, contactOk={})", user.getId(), emailOk, contactOk);
        } else {
            user.setVerified(Boolean.FALSE);
            user.setNotes(appendNote(user.getNotes(), "User verification pending/manual review"));
            logger.info("User [{}] verification pending (emailOk={}, contactOk={})", user.getId(), emailOk, contactOk);
        }

        // If verification state changed, emit an info log (auditing handled elsewhere)
        if ((before == null && user.getVerified() != null) || (before != null && !before.equals(user.getVerified()))) {
            logger.info("User [{}] verification flag changed from {} to {}", user.getId(), before, user.getVerified());
        }

        return user;
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " | " + addition;
    }
}