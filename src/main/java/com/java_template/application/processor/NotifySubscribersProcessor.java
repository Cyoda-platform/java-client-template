package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.analysisjob.version_1.AnalysisJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalysisJob> context) {
        AnalysisJob job = context.entity();

        // Business rule: Only notify when a reportRef is present (report generated)
        // and job status is COMPLETED (to ensure report is ready).
        if (job.getReportRef() == null || job.getReportRef().isBlank()) {
            logger.info("AnalysisJob {} has no reportRef; skipping notifications.", job.getId());
            return job;
        }
        if (job.getStatus() == null || !job.getStatus().equalsIgnoreCase("COMPLETED")) {
            logger.info("AnalysisJob {} is not COMPLETED (status={}); skipping notifications.", job.getId(), job.getStatus());
            return job;
        }

        // Fetch all subscribers and filter active ones who haven't opted out.
        List<Subscriber> matchingSubscribers = new ArrayList<>();
        try {
            CompletableFuture<ArrayNode> subsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode subsArray = subsFuture.join();
            if (subsArray != null) {
                for (JsonNode node : subsArray) {
                    try {
                        Subscriber s = objectMapper.convertValue(node, Subscriber.class);
                        // Validate using existing getters and rules:
                        boolean active = s.getActive() != null && s.getActive();
                        boolean optedOut = s.getOptOutAt() != null && !s.getOptOutAt().isBlank();
                        if (active && !optedOut) {
                            matchingSubscribers.add(s);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to convert subscriber node to Subscriber object: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load subscribers for notification: {}", e.getMessage(), e);
            return job;
        }

        if (matchingSubscribers.isEmpty()) {
            logger.info("No active subscribers found for AnalysisJob {}. Nothing to enqueue.", job.getId());
            // still mark as NOTIFIED to avoid repeated attempts if business wants it; keep current behavior
            job.setStatus("NOTIFIED");
            return job;
        }

        // For each matching subscriber, create a delivery task entity (separate entity) via EntityService.
        for (Subscriber subscriber : matchingSubscribers) {
            try {
                ObjectNode delivery = objectMapper.createObjectNode();
                delivery.put("id", UUID.randomUUID().toString());
                delivery.put("analysisJobId", job.getId());
                delivery.put("subscriberId", subscriber.getId());
                delivery.put("reportRef", job.getReportRef());
                delivery.put("status", "PENDING");
                delivery.put("createdAt", Instant.now().toString());

                CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DeliveryTask",
                    "1",
                    delivery
                );
                UUID createdTechnicalId = idFuture.join();
                String subId = subscriber.getId() != null ? subscriber.getId() : "unknown";
                logger.info("Created DeliveryTask {} for subscriber {} (job {})", createdTechnicalId, subId, job.getId());
            } catch (Exception ex) {
                String subId = subscriber.getId() != null ? subscriber.getId() : "unknown";
                logger.error("Failed to create delivery task for subscriber {}: {}", subId, ex.getMessage(), ex);
            }
        }

        // Update job state to indicate notifications were enqueued. This entity will be persisted automatically by Cyoda.
        job.setStatus("NOTIFIED");

        return job;
    }
}