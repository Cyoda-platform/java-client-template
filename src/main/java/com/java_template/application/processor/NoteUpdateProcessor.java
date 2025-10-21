package com.java_template.application.processor;

import com.java_template.application.entity.note.version_1.Note;
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
 * ABOUTME: This processor handles note update operations, setting timestamps
 * and performing validation when notes are updated in the CRM system.
 */
@Component
public class NoteUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NoteUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NoteUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Note.class)
                .validate(this::isValidEntityWithMetadata, "Invalid note entity wrapper")
                .map(this::processNoteUpdateLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Note
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Note> entityWithMetadata) {
        Note note = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return note != null && note.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for note update processing
     */
    private EntityWithMetadata<Note> processNoteUpdateLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Note> context) {

        EntityWithMetadata<Note> entityWithMetadata = context.entityResponse();
        Note note = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing note update: {} in state: {}", note.getNoteId(), currentState);

        // Update timestamp
        note.setUpdatedAt(LocalDateTime.now());

        // Set created timestamp if not already set
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Note {} updated successfully", note.getNoteId());

        return entityWithMetadata;
    }
}
