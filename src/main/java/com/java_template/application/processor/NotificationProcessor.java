package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
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

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        if (job == null) return null;

        logger.info("NotificationProcessor: preparing notifications for jobId={}, state={}", job.getJobId(), job.getState());

        try {
            // Fetch all subscribers
            CompletableFuture<List<DataPayload>> subsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> subPayloads = subsFuture.get();
            List<Subscriber> subscribers = new ArrayList<>();
            if (subPayloads != null) {
                for (DataPayload payload : subPayloads) {
                    try {
                        JsonNode data = (JsonNode) payload.getData();
                        Subscriber s = objectMapper.treeToValue(data, Subscriber.class);
                        if (s != null) subscribers.add(s);
                    } catch (Exception e) {
                        logger.warn("Failed to parse subscriber payload: {}", e.getMessage(), e);
                    }
                }
            }

            // Fetch laureates ingested by this job (to evaluate filters)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.ingestJobId", "EQUALS", job.getJobId())
            );
            CompletableFuture<List<DataPayload>> laureateFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> laureatePayloads = laureateFuture.get();
            List<Laureate> laureates = new ArrayList<>();
            if (laureatePayloads != null) {
                for (DataPayload payload : laureatePayloads) {
                    try {
                        JsonNode data = (JsonNode) payload.getData();
                        Laureate l = objectMapper.treeToValue(data, Laureate.class);
                        if (l != null) laureates.add(l);
                    } catch (Exception e) {
                        logger.warn("Failed to parse laureate payload: {}", e.getMessage(), e);
                    }
                }
            }

            // Build notification summary
            JsonNode summaryNode = objectMapper.createObjectNode()
                .put("total", job.getTotalRecords() == null ? 0 : job.getTotalRecords())
                .put("succeeded", job.getSucceededCount() == null ? 0 : job.getSucceededCount())
                .put("failed", job.getFailedCount() == null ? 0 : job.getFailedCount());

            // Notify subscribers based on active flag and filters
            for (Subscriber s : subscribers) {
                try {
                    if (s.getActive() == null || !s.getActive()) {
                        continue; // only active subscribers
                    }

                    boolean matchesFilter = false;
                    // If subscriber has no filters, notify them
                    if (s.getFilters() == null || s.getFilters().isEmpty()) {
                        matchesFilter = true;
                    } else {
                        // If any laureate matches any of the subscriber filters (by category), we notify
                        for (Subscriber.Filter f : s.getFilters()) {
                            if (f == null || f.getCategory() == null) continue;
                            for (Laureate l : laureates) {
                                if (l.getCategory() != null && l.getCategory().equalsIgnoreCase(f.getCategory())) {
                                    matchesFilter = true;
                                    break;
                                }
                            }
                            if (matchesFilter) break;
                        }
                    }

                    if (!matchesFilter) {
                        logger.debug("Subscriber {} skipped due to filter mismatch", s.getSubscriberId());
                        continue;
                    }

                    // Construct a simple payload for logging/delivery simulation
                    JsonNode payload = objectMapper.createObjectNode()
                        .put("jobId", job.getJobId())
                        .put("state", job.getState())
                        .set("summary", summaryNode);

                    // Simulate delivery by logging per channel; real delivery would be implemented separately
                    if (s.getChannels() != null) {
                        for (Subscriber.Channel c : s.getChannels()) {
                            if (c == null) continue;
                            String type = c.getType();
                            String address = c.getAddress();
                            logger.info("Delivering notification to subscriberId={} via {}:{} payload={}",
                                s.getSubscriberId(), type, address, payload.toString());
                        }
                    } else {
                        logger.info("No channels defined for subscriberId={}, skipping delivery", s.getSubscriberId());
                    }

                    // Update subscriber.lastNotifiedAt to now and persist the change if possible
                    s.setLastNotifiedAt(Instant.now().toString());
                    try {
                        // Try to update by subscriberId if it is a UUID string
                        if (s.getSubscriberId() != null && !s.getSubscriberId().isBlank()) {
                            UUID sid = UUID.fromString(s.getSubscriberId());
                            CompletableFuture<UUID> updated = entityService.updateItem(sid, s);
                            updated.get();
                        } else {
                            logger.warn("Cannot update subscriber without a valid subscriberId: {}", s);
                        }
                    } catch (IllegalArgumentException iae) {
                        // subscriberId is not a UUID - cannot update by technical id; log and continue
                        logger.warn("SubscriberId is not a UUID, skipping update of lastNotifiedAt for subscriberId={}", s.getSubscriberId());
                    } catch (Exception ex) {
                        logger.warn("Failed to update subscriber lastNotifiedAt for subscriberId={}: {}", s.getSubscriberId(), ex.getMessage(), ex);
                    }

                } catch (Exception inner) {
                    logger.error("Error while processing subscriber {}: {}", s == null ? "null" : s.getSubscriberId(), inner.getMessage(), inner);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process notifications for job {}: {}", job.getJobId(), e.getMessage(), e);
        }

        return job;
    }
}