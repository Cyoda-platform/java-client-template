package com.java_template.application.processor;

import com.java_template.application.entity.reminder.version_1.Reminder;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: This processor handles reminder creation operations, setting timestamps
 * and performing validation when reminders are created in the CRM system.
 */
@Component
public class ReminderCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReminderCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReminderCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Reminder.class)
                .validate(this::isValidEntityWithMetadata, "Invalid reminder entity wrapper")
                .map(this::processReminderCreateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Reminder
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Reminder> entityWithMetadata) {
        Reminder reminder = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return reminder != null && reminder.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for reminder creation processing
     */
    private EntityWithMetadata<Reminder> processReminderCreateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Reminder> context) {

        EntityWithMetadata<Reminder> entityWithMetadata = context.entityResponse();
        Reminder reminder = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing reminder creation: {} in state: {}", reminder.getReminderId(), currentState);

        // Set creation and update timestamps
        LocalDateTime now = LocalDateTime.now();
        reminder.setCreatedAt(now);
        reminder.setUpdatedAt(now);

        // Set default priority if not provided
        if (reminder.getPriority() == null || reminder.getPriority().trim().isEmpty()) {
            reminder.setPriority("medium");
        }

        // Initialize completion status
        if (reminder.getCompleted() == null) {
            reminder.setCompleted(false);
        }

        logger.info("Reminder {} created successfully", reminder.getReminderId());

        return entityWithMetadata;
    }
}
