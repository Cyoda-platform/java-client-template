package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
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

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BuildDeliveryBatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BuildDeliveryBatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper;

    public BuildDeliveryBatchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper mapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.mapper = mapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BuildDeliveryBatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    @SuppressWarnings("unchecked")
    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Fetch subscribers from EntityService
            ArrayNode subscribersNode = entityService.getItems(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION)).join();
            if (subscribersNode == null) {
                logger.warn("No subscribers retrieved for Job {}", job.getId());
                return job;
            }

            List<JsonNode> activeSubscribers = new ArrayList<>();
            for (JsonNode sn : subscribersNode) {
                String status = sn.path("subscriptionStatus").asText(null);
                if ("active".equalsIgnoreCase(status)) {
                    activeSubscribers.add(sn);
                }
            }

            int queued = 0;
            Map<String, Object> jobParams = job.getParameters();
            String globalSendDay = null;
            if (jobParams != null && jobParams.get("globalSendDay") instanceof String) {
                globalSendDay = (String) jobParams.get("globalSendDay");
            }

            for (JsonNode subNode : activeSubscribers) {
                String subscriberId = subNode.path("id").asText(null);
                String tz = subNode.path("timezone").asText("UTC");
                ZoneId zone = ZoneId.of(tz);
                ZonedDateTime scheduledAt;

                if (job.getScheduledAt() != null && !job.getScheduledAt().isEmpty()) {
                    scheduledAt = ZonedDateTime.parse(job.getScheduledAt());
                } else if (globalSendDay != null && !globalSendDay.isBlank()) {
                    // Attempt to parse globalSendDay as DayOfWeek name
                    try {
                        java.time.DayOfWeek dow = java.time.DayOfWeek.valueOf(globalSendDay.toUpperCase());
                        scheduledAt = ZonedDateTime.now(zone).with(java.time.temporal.TemporalAdjusters.nextOrSame(dow));
                    } catch (Exception e) {
                        scheduledAt = ZonedDateTime.now(zone);
                    }
                } else {
                    // default to now in subscriber tz
                    scheduledAt = ZonedDateTime.now(zone);
                }

                ObjectNode delivery = mapper.createObjectNode();
                delivery.put("id", UUID.randomUUID().toString());
                delivery.put("job_id", job.getId());
                delivery.put("subscriber_id", subscriberId != null ? subscriberId : "");
                // select a fact: query CatFact entities and pick first active
                ArrayNode facts = entityService.getItems("CatFact", "1").join();
                String factId = null;
                if (facts != null) {
                    for (JsonNode fn : facts) {
                        if ("active".equalsIgnoreCase(fn.path("status").asText(""))) {
                            factId = fn.path("id").asText(null);
                            break;
                        }
                    }
                }
                if (factId != null) {
                    delivery.put("fact_id", factId);
                } else {
                    delivery.putNull("fact_id");
                }
                delivery.put("scheduled_at", scheduledAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                delivery.put("attempts", 0);
                delivery.put("status", "PENDING");

                ObjectNode rp = mapper.createObjectNode();
                Map<String, Integer> retries = job.getRetriesPolicy();
                int maxRetries = (retries != null && retries.get("maxRetries") != null) ? retries.get("maxRetries") : 2;
                rp.put("maxRetries", maxRetries);
                delivery.set("retries_policy", rp);

                // Persist the delivery as a system record via EntityService
                try {
                    entityService.addItem("Delivery", "1", delivery).join();
                    queued++;
                    logger.info("Created Delivery {} for subscriber {}", delivery.path("id").asText(), subscriberId);
                } catch (Exception ex) {
                    logger.error("Failed to persist Delivery for subscriber {}: {}", subscriberId, ex.getMessage(), ex);
                }
            }

            // update job resultSummary queued
            var summary = job.getResultSummary();
            if (summary != null) {
                summary.put("queued", summary.getOrDefault("queued", 0) + queued);
            }

            logger.info("BuildDeliveryBatchProcessor queued {} deliveries for Job {}", queued, job.getId());

        } catch (Exception ex) {
            logger.error("Error building delivery batch for Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
