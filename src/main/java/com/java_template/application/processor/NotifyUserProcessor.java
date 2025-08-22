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

import java.time.Instant;

@Component
public class NotifyUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyUserProcessor(SerializerFactory serializerFactory) {
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

        try {
            // Business logic: when a user is verified, perform notification steps and mark user as active via notes.
            // Use only existing getters/setters on User. User has fields: id,name,email,contact,notes,role,savedPets,verified
            if (Boolean.TRUE.equals(user.getVerified())) {
                String now = Instant.now().toString();
                String existingNotes = user.getNotes();
                String notifyMessage = "NOTIFIED: user verified and activated at " + now;
                if (existingNotes == null || existingNotes.isBlank()) {
                    user.setNotes(notifyMessage);
                } else {
                    user.setNotes(existingNotes + " | " + notifyMessage);
                }
                logger.info("User {} verified - notification appended to notes", user.getId());
            } else {
                // If not verified, ensure we do not send notification; optionally record reason.
                logger.debug("User {} not verified - skipping notify", user.getId());
            }
        } catch (Exception e) {
            // Do not throw; log and allow serializer to persist current entity state.
            logger.error("Error while processing NotifyUserProcessor for user {}: {}", user != null ? user.getId() : "null", e.getMessage(), e);
        }

        return user;
    }
}