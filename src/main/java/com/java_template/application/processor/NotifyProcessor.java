package com.java_template.application.processor;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class NotifyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob entity = context.entity();

        // Business logic:
        // Notify the requester (requestedBy) when ingestion job is COMPLETED or FAILED.
        // Ensure completedAt timestamp is set and summary counts are non-null (default to 0).
        if (entity == null) {
            logger.warn("IngestionJob entity is null in context");
            return entity;
        }

        String status = entity.getStatus();
        if (status == null) {
            logger.warn("IngestionJob.status is null for job requestedBy={}", entity.getRequestedBy());
            return entity;
        }

        boolean isTerminal = status.equalsIgnoreCase("COMPLETED") || status.equalsIgnoreCase("FAILED");

        if (!isTerminal) {
            logger.info("IngestionJob status is not terminal ({}). No notification will be sent.", status);
            return entity;
        }

        // Ensure summary exists and counts are non-null
        IngestionJob.Summary summary = entity.getSummary();
        if (summary == null) {
            summary = new IngestionJob.Summary();
            summary.setCreated(0);
            summary.setUpdated(0);
            summary.setFailed(0);
            entity.setSummary(summary);
        } else {
            if (summary.getCreated() == null) summary.setCreated(0);
            if (summary.getUpdated() == null) summary.setUpdated(0);
            if (summary.getFailed() == null) summary.setFailed(0);
        }

        // Ensure completedAt is set
        if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setCompletedAt(now);
            logger.debug("Set completedAt for IngestionJob requestedBy={} to {}", entity.getRequestedBy(), now);
        }

        // Attempt to resolve requester as an Owner entity and log/send notification info.
        String requester = entity.getRequestedBy();
        if (requester != null && !requester.isBlank()) {
            try {
                // If requestedBy is a stored entity technicalId (UUID), try to fetch it
                CompletableFuture<DataPayload> ownerFuture = entityService.getItem(UUID.fromString(requester));
                DataPayload payload = ownerFuture.get();
                if (payload != null && payload.getData() != null) {
                    Owner owner = objectMapper.treeToValue(payload.getData(), Owner.class);
                    if (owner != null) {
                        String recipient = owner.getEmail();
                        if (recipient != null && !recipient.isBlank()) {
                            // In a real implementation we'd send an email/notification.
                            // Here we log the notification intent.
                            String message = String.format("Ingestion job %s completed with status=%s. summary(created=%d, updated=%d, failed=%d)",
                                    payload.getMeta() != null && payload.getMeta().get("entityId") != null ? payload.getMeta().get("entityId").asText() : "unknown",
                                    status,
                                    summary.getCreated(),
                                    summary.getUpdated(),
                                    summary.getFailed()
                            );
                            logger.info("NotifyProcessor: would send notification to owner email {} : {}", recipient, message);
                        } else {
                            logger.info("NotifyProcessor: requester resolved to Owner without email. ownerId={}", owner.getId());
                        }
                    } else {
                        logger.warn("NotifyProcessor: unable to convert payload to Owner for requester={}", requester);
                    }
                } else {
                    logger.info("NotifyProcessor: no payload found for requester technicalId={}", requester);
                }
            } catch (IllegalArgumentException iae) {
                // requestedBy is not a UUID; treat as a system/user identifier string
                logger.info("NotifyProcessor: requestedBy is not a technical UUID, treating as identifier: {}", requester);
                String message = String.format("Ingestion job completed with status=%s. summary(created=%d, updated=%d, failed=%d)",
                        status, summary.getCreated(), summary.getUpdated(), summary.getFailed());
                logger.info("NotifyProcessor: would notify identifier {} with message: {}", requester, message);
            } catch (Exception ex) {
                logger.warn("NotifyProcessor: error while resolving requester {} : {}", requester, ex.getMessage(), ex);
            }
        } else {
            logger.info("NotifyProcessor: no requester specified for job");
        }

        // No external entity modifications performed here. The updated IngestionJob entity
        // (completedAt, summary defaults) will be persisted by Cyoda workflow automatically.
        return entity;
    }
}