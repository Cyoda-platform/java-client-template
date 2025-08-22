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

        // Business logic:
        // - NotifyUserProcessor is triggered when a user becomes verified.
        // - If the user is verified, mark that a notification has been sent by updating the notes field.
        // - Preserve existing notes and avoid duplicating notification entries.
        if (Boolean.TRUE.equals(user.getVerified())) {
            String existingNotes = user.getNotes();
            String marker = "Notified of verification";
            boolean alreadyNotified = existingNotes != null && existingNotes.contains(marker);

            if (!alreadyNotified) {
                String timestamp = Instant.now().toString();
                String newEntry = marker + " at " + timestamp;

                String updatedNotes;
                if (existingNotes == null || existingNotes.isBlank()) {
                    updatedNotes = newEntry;
                } else {
                    updatedNotes = existingNotes + "\n" + newEntry;
                }

                user.setNotes(updatedNotes);
                logger.info("User {} verified - appended notification note", user.getId());
            } else {
                logger.info("User {} already has notification note; skipping update", user.getId());
            }
        } else {
            logger.info("User {} is not verified; no notification performed", user.getId());
        }

        return user;
    }
}