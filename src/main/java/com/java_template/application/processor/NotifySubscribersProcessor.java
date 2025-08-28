package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public NotifySubscribersProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
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

        try {
            // Only proceed if job reached a terminal ingestion result state (SUCCEEDED or FAILED)
            String state = job.getState();
            if (state == null) {
                logger.warn("Job state is null, skipping notifications for job id={}", job.getId());
                return job;
            }
            boolean notifyPhase = "SUCCEEDED".equalsIgnoreCase(state) || "FAILED".equalsIgnoreCase(state);
            if (!notifyPhase) {
                logger.info("Job state is '{}' - NotifySubscribersProcessor only runs for SUCCEEDED or FAILED. job id={}", state, job.getId());
                return job;
            }

            // Fetch active subscribers
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                Subscriber.ENTITY_VERSION,
                null, null, null
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No subscribers found to notify for job id={}", job.getId());
                // Update job to notified state even if none to notify
                job.setState("NOTIFIED_SUBSCRIBERS");
                job.setFinishedAt(Instant.now().toString());
                return job;
            }

            List<Subscriber> subscribers = new ArrayList<>();
            for (DataPayload payload : dataPayloads) {
                try {
                    Subscriber s = objectMapper.treeToValue(payload.getData(), Subscriber.class);
                    if (s != null) subscribers.add(s);
                } catch (Exception ex) {
                    logger.warn("Failed to deserialize subscriber payload: {}", ex.getMessage(), ex);
                }
            }

            int notifyAttempts = 0;
            int notifySuccess = 0;
            int notifyFailed = 0;
            List<String> failures = new ArrayList<>();

            for (Subscriber s : subscribers) {
                try {
                    // Only notify active subscribers
                    if (s.getActive() == null || !s.getActive()) {
                        continue;
                    }

                    // Simple filter matching:
                    // If subscriber defines a filter containing "state=" or "status=", only notify when job.state matches value.
                    // Otherwise, default to notify.
                    boolean matches = true;
                    String filters = s.getFilters();
                    if (filters != null && !filters.isBlank()) {
                        String lower = filters.toLowerCase();
                        if (lower.contains("state=") || lower.contains("status=")) {
                            String[] parts = filters.split(";");
                            matches = false;
                            for (String p : parts) {
                                p = p.trim();
                                if (p.isEmpty()) continue;
                                String[] kv = p.split("=", 2);
                                if (kv.length != 2) continue;
                                String key = kv[0].trim().toLowerCase();
                                String value = kv[1].trim();
                                if (("state".equals(key) || "status".equals(key)) && value.equalsIgnoreCase(state)) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                        // Other filter types are not applicable at job-level; default to notify (matches = true)
                    }

                    if (!matches) {
                        logger.debug("Subscriber {} filter does not match job state '{}', skipping", s.getId(), state);
                        continue;
                    }

                    notifyAttempts++;

                    // Prefer webhook if available, otherwise simulate email by logging
                    if (s.getWebhookUrl() != null && !s.getWebhookUrl().isBlank()) {
                        try {
                            String body = objectMapper.writeValueAsString(job);
                            HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(s.getWebhookUrl()))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();

                            CompletableFuture<HttpResponse<String>> respFuture = httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString());
                            // wait a short time for delivery, but do not block indefinitely
                            HttpResponse<String> resp = respFuture.get();
                            int status = resp.statusCode();
                            if (status >= 200 && status < 300) {
                                notifySuccess++;
                                logger.info("Notified subscriber {} via webhook (status={}) for job id={}", s.getId(), status, job.getId());
                            } else {
                                notifyFailed++;
                                String msg = String.format("Webhook notify failed for subscriber %s status=%d", s.getId(), status);
                                failures.add(msg);
                                logger.warn(msg);
                            }
                        } catch (Exception ex) {
                            notifyFailed++;
                            String msg = String.format("Webhook notify error for subscriber %s: %s", s.getId(), ex.getMessage());
                            failures.add(msg);
                            logger.error(msg, ex);
                        }
                    } else if (s.getEmail() != null && !s.getEmail().isBlank()) {
                        // Simulate email sending by logging (actual email provider not configured)
                        try {
                            logger.info("Simulated email to {} <{}> for job id={} state={}", s.getName(), s.getEmail(), job.getId(), state);
                            notifySuccess++;
                        } catch (Exception ex) {
                            notifyFailed++;
                            String msg = String.format("Email notify error for subscriber %s: %s", s.getId(), ex.getMessage());
                            failures.add(msg);
                            logger.error(msg, ex);
                        }
                    } else {
                        // No contact method
                        notifyFailed++;
                        String msg = String.format("Subscriber %s has no contact method", s.getId());
                        failures.add(msg);
                        logger.warn(msg);
                    }
                } catch (Exception ex) {
                    notifyFailed++;
                    String msg = String.format("Unexpected error notifying subscriber %s: %s", s == null ? "unknown" : s.getId(), ex.getMessage());
                    failures.add(msg);
                    logger.error(msg, ex);
                }
            }

            // Update job state to NOTIFIED_SUBSCRIBERS and record summary
            job.setState("NOTIFIED_SUBSCRIBERS");
            job.setFinishedAt(Instant.now().toString());

            String summary = String.format("notificationsAttempted=%d, success=%d, failed=%d", notifyAttempts, notifySuccess, notifyFailed);
            if (!failures.isEmpty()) {
                summary = summary + ", errors=[" + String.join("; ", failures) + "]";
            }
            job.setErrorSummary(summary);

            logger.info("Notification summary for job id={}: {}", job.getId(), summary);

        } catch (Exception ex) {
            logger.error("Failed to notify subscribers for job id={}: {}", job == null ? "unknown" : job.getId(), ex.getMessage(), ex);
            // In case of unexpected failure, attempt to mark job as NOTIFIED_SUBSCRIBERS with error summary
            if (job != null) {
                job.setState("NOTIFIED_SUBSCRIBERS");
                job.setFinishedAt(Instant.now().toString());
                job.setErrorSummary("NOTIFY_ERROR: " + ex.getMessage());
            }
        }

        return job;
    }
}