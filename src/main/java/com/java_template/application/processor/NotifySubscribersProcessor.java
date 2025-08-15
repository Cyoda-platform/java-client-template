package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.util.*;
import java.util.concurrent.CompletionException;

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
        logger.info("Processing NotifySubscribers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            logger.info("Starting notification phase for job id={}", job.getId());
            // Query subscribers filtered by job.subscriberFilters if present
            List<ObjectNode> subs = new ArrayList<>();
            try {
                if (job.getSubscriberFilters() != null && !job.getSubscriberFilters().isEmpty()) {
                    // Build simple search condition based on provided filters (supports equality only)
                    // TODO: For now use in-memory filtering after fetching all subscribers
                    subs = entityService.getItems(
                        Subscriber.ENTITY_NAME,
                        String.valueOf(Subscriber.ENTITY_VERSION)
                    ).join();
                } else {
                    subs = entityService.getItems(
                        Subscriber.ENTITY_NAME,
                        String.valueOf(Subscriber.ENTITY_VERSION)
                    ).join();
                }
            } catch (CompletionException ce) {
                logger.error("Failed to fetch subscribers for notification: {}", ce.getMessage(), ce);
            }

            int success = 0;
            int failure = 0;

            for (ObjectNode node : subs) {
                try {
                    Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                    if (s.getActive() == null || !s.getActive()) continue;

                    // Basic filter matching: if job.subscriberFilters present, ensure subscriber's filters contain the entries
                    boolean matches = true;
                    if (job.getSubscriberFilters() != null && !job.getSubscriberFilters().isEmpty()) {
                        Map<String,Object> sf = job.getSubscriberFilters();
                        Map<String,Object> sfSub = s.getFilters();
                        for (Map.Entry<String,Object> e : sf.entrySet()) {
                            if (sfSub == null || !Objects.equals(sfSub.get(e.getKey()), e.getValue())) {
                                matches = false; break;
                            }
                        }
                    }
                    if (!matches) continue;

                    // Create a simple notification payload
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("jobId", job.getId());
                    payload.put("jobStatus", job.getStatus());
                    payload.put("laureatesProcessed", job.getProcessedRecordsCount() == null ? 0 : job.getProcessedRecordsCount());

                    // Simulate delivery: if webhookUrl present, assume success; else if email present assume success; otherwise failure
                    boolean delivered = false;
                    if (s.getWebhookUrl() != null && !s.getWebhookUrl().isBlank()) {
                        delivered = true; // In real implementation use HTTP client
                    } else if (s.getEmail() != null && !s.getEmail().isBlank()) {
                        delivered = true; // In real implementation send email
                    }

                    if (delivered) {
                        success++;
                        s.setLastNotifiedAt(Instant.now().toString());
                        // update subscriber lastNotifiedAt
                        try {
                            entityService.updateItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                UUID.fromString(node.get("technicalId").asText()),
                                s
                            ).join();
                        } catch (Exception e) {
                            logger.debug("Failed to update subscriber lastNotifiedAt: {}", e.getMessage());
                        }
                    } else {
                        failure++;
                        // Increment deliveryFailures and possibly suspend subscriber
                        Integer df = s.getDeliveryFailures();
                        df = (df == null ? 0 : df) + 1;
                        s.setDeliveryFailures(df);
                        Map<String,Object> rp = s.getRetryPolicy();
                        Integer max = rp != null && rp.get("maxRetries") instanceof Integer ? (Integer) rp.get("maxRetries") : 3;
                        if (df >= max) {
                            s.setActive(false);
                        }
                        try {
                            entityService.updateItem(
                                Subscriber.ENTITY_NAME,
                                String.valueOf(Subscriber.ENTITY_VERSION),
                                UUID.fromString(node.get("technicalId").asText()),
                                s
                            ).join();
                        } catch (Exception e) {
                            logger.debug("Failed to update subscriber deliveryFailures: {}", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing subscriber notification: {}", e.getMessage(), e);
                }
            }

            job.setLastNotifiedAt(Instant.now().toString());
            logger.info("Notification summary for job id={} success={} failure={}", job.getId(), success, failure);

        } catch (Exception e) {
            logger.error("Unexpected error in NotifySubscribersProcessor: {}", e.getMessage(), e);
            job.setLastError(e.getMessage());
        }
        return job;
    }
}
