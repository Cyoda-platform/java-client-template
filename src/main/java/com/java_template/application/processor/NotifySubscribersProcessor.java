package com.java_template.application.processor;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CoverPhoto.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CoverPhoto entity) {
        return entity != null && entity.isValid();
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();

        // Ensure basic fields are initialized and consistent for downstream processors.
        // Do NOT call entity service to update this entity here — Cyoda will persist changes automatically.

        // Initialize viewCount if absent
        if (entity.getViewCount() == null) {
            entity.setViewCount(0);
            logger.debug("Initialized viewCount to 0 for CoverPhoto id={}", entity.getId());
        }

        // Ensure comments list is not null
        if (entity.getComments() == null) {
            entity.setComments(new ArrayList<>());
            logger.debug("Initialized empty comments list for CoverPhoto id={}", entity.getId());
        } else {
            // Defensive: remove any null entries
            List<CoverPhoto.Comment> cleaned = new ArrayList<>();
            for (CoverPhoto.Comment c : entity.getComments()) {
                if (c != null) cleaned.add(c);
            }
            if (cleaned.size() != entity.getComments().size()) {
                entity.setComments(cleaned);
                logger.debug("Cleaned null comments for CoverPhoto id={}", entity.getId());
            }
        }

        // Update updatedAt timestamp if missing or blank
        if (entity.getUpdatedAt() == null || entity.getUpdatedAt().isBlank()) {
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            entity.setUpdatedAt(now);
            logger.debug("Set updatedAt for CoverPhoto id={} to {}", entity.getId(), now);
        }

        // Business intent:
        // NotifySubscribersProcessor should notify all subscribed users about a newly published CoverPhoto.
        // Sending actual notifications (email/push) / creating separate Notification entities is environment-specific
        // and should be implemented via an external service or a dedicated entity service call.
        // Here we only log the action and prepare the entity state for the next transition (e.g., VISIBLE).
        logger.info("NotifySubscribersProcessor prepared CoverPhoto id={} (title='{}') for notifications. " +
                "Subscribers will be notified by downstream integration when available.", entity.getId(), entity.getTitle());

        // No modification to business id or other mandatory fields.
        return entity;
    }
}