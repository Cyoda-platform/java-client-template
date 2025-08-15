package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.HttpUtils;
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
    private final HttpUtils httpUtils;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper, HttpUtils httpUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpUtils = httpUtils;
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
            // Query subscribers (fetch all, then apply filters in-memory for prototype simplicity)
            List<ObjectNode> subs = new ArrayList<>();
            try {
                subs = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION)
                ).join();
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

                    // Prepare notification payload
                    ObjectNode payload = objectMapper.createObjectNode();
                    payload.put("jobId", job.getId());
                    payload.put("jobStatus", job.getStatus());
                    payload.put("laureatesProcessed", job.getProcessedRecordsCount() == null ? 0 : job.getProcessedRecordsCount());
                    payload.put("timestamp", Instant.now().toString());

                    // Allow subscriber-level retry policy
                    Map<String,Object> rp = s.getRetryPolicy();
                    int maxRetries = rp != null && rp.get("maxRetries") instanceof Number ? ((Number) rp.get("maxRetries")).intValue() : 3;
                    int initialBackoff = rp != null && rp.get("initialBackoffSeconds") instanceof Number ? ((Number) rp.get("initialBackoffSeconds")).intValue() : 2;
                    boolean exponential = rp != null && rp.get("exponential") instanceof Boolean ? (Boolean) rp.get("exponential") : true;

                    boolean deliveredOverall = false;

                    // Deliver via preferred channels; try webhook first if present
                    if (s.getChannels() != null && !s.getChannels().isEmpty()) {
                        for (String ch : s.getChannels()) {
                            if ("WEBHOOK".equalsIgnoreCase(ch) && s.getWebhookUrl() != null && !s.getWebhookUrl().isBlank()) {
                                boolean delivered = deliverWithRetriesWebhook(s, payload, maxRetries, initialBackoff, exponential, node);
                                if (delivered) deliveredOverall = true;
                            } else if ("EMAIL".equalsIgnoreCase(ch) && s.getEmail() != null && !s.getEmail().isBlank()) {
                                // For prototype we simulate email delivery — in real world integrate with email provider
                                boolean delivered = simulateEmailDelivery(s, payload);
                                if (delivered) deliveredOverall = true;
                            }
                        }
                    } else {
                        // No channels specified — consider as failure
                        logger.debug("Subscriber {} has no channels configured", s.getName());
                    }

                    if (deliveredOverall) {
                        success++;
                        s.setLastNotifiedAt(Instant.now().toString());
                        s.setDeliveryFailures(0);
                        // persist subscriber update
                        try {
                            if (node.has("technicalId")) {
                                entityService.updateItem(
                                    Subscriber.ENTITY_NAME,
                                    String.valueOf(Subscriber.ENTITY_VERSION),
                                    UUID.fromString(node.get("technicalId").asText()),
                                    s
                                ).join();
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to update subscriber lastNotifiedAt: {}", e.getMessage());
                        }
                    } else {
                        failure++;
                        Integer df = s.getDeliveryFailures();
                        df = (df == null ? 0 : df) + 1;
                        s.setDeliveryFailures(df);
                        Map<String,Object> srp = s.getRetryPolicy();
                        Integer max = srp != null && srp.get("maxRetries") instanceof Number ? ((Number) srp.get("maxRetries")).intValue() : 3;
                        if (df >= max) {
                            s.setActive(false);
                        }
                        try {
                            if (node.has("technicalId")) {
                                entityService.updateItem(
                                    Subscriber.ENTITY_NAME,
                                    String.valueOf(Subscriber.ENTITY_VERSION),
                                    UUID.fromString(node.get("technicalId").asText()),
                                    s
                                ).join();
                            }
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

    private boolean deliverWithRetriesWebhook(Subscriber s, ObjectNode payload, int maxRetries, int initialBackoff, boolean exponential, com.fasterxml.jackson.databind.node.ObjectNode nodeMeta) {
        int attempt = 0;
        while (attempt <= maxRetries) {
            attempt++;
            try {
                // Use HttpUtils to POST payload to subscriber's webhookUrl
                com.fasterxml.jackson.databind.node.ObjectNode resp = httpUtils.sendPostRequest(null, s.getWebhookUrl(), null, payload).join();
                int status = resp.has("status") ? resp.get("status").asInt() : 200;
                if (status >= 200 && status < 300) {
                    logger.info("Delivered webhook to subscriber {} status={}", s.getName(), status);
                    return true;
                } else {
                    logger.warn("Webhook delivery to {} returned status {}", s.getName(), status);
                }
            } catch (Exception e) {
                logger.warn("Webhook delivery attempt {} to {} failed: {}", attempt, s.getName(), e.getMessage());
            }

            if (attempt > maxRetries) break;
            int backoff = initialBackoff * (exponential ? (1 << (attempt - 1)) : attempt);
            try {
                Thread.sleep(backoff * 1000L);
            } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        return false;
    }

    private boolean simulateEmailDelivery(Subscriber s, ObjectNode payload) {
        // Prototype simulation: mark as delivered and log. Real implementation should integrate with email provider.
        logger.info("Simulating email delivery to {} <{}>", s.getName(), s.getEmail());
        return true;
    }
}
