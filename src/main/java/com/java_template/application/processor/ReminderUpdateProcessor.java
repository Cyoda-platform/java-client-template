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
 * ABOUTME: This processor handles reminder update operations, setting timestamps
 * and performing validation when reminders are updated in the CRM system.
 */
@Component
public class ReminderUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReminderUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReminderUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Reminder.class)
                .validate(this::isValidEntityWithMetadata, "Invalid reminder entity wrapper")
                .map(this::processReminderUpdateLogic)
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
     * Main business logic for reminder update processing
     */
    private EntityWithMetadata<Reminder> processReminderUpdateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Reminder> context) {

        EntityWithMetadata<Reminder> entityWithMetadata = context.entityResponse();
        Reminder reminder = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing reminder update: {} in state: {}", reminder.getReminderId(), currentState);

        // Update timestamp
        reminder.setUpdatedAt(LocalDateTime.now());

        // Set created timestamp if not already set
        if (reminder.getCreatedAt() == null) {
            reminder.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Reminder {} updated successfully", reminder.getReminderId());

        return entityWithMetadata;
    }
}
