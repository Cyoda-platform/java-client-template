package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

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
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
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
        if (job == null) return job;

        String state = job.getState();
        if (state == null) {
            logger.warn("Job state is null for jobId={}", job.getJobId());
            return job;
        }

        // Only proceed when job reached a terminal state that requires notifications
        if (!"SUCCEEDED".equalsIgnoreCase(state) && !"FAILED".equalsIgnoreCase(state)) {
            logger.info("Job '{}' is in state '{}' - no notifications required", job.getJobId(), state);
            return job;
        }

        try {
            // Fetch all subscribers
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION)
            );
            ArrayNode subscribersArray = itemsFuture.join();
            if (subscribersArray == null || subscribersArray.size() == 0) {
                logger.info("No subscribers found to notify for jobId={}", job.getJobId());
            } else {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                Iterator<JsonNode> it = subscribersArray.elements();
                while (it.hasNext()) {
                    JsonNode node = it.next();
                    Subscriber subscriber;
                    try {
                        subscriber = objectMapper.treeToValue(node, Subscriber.class);
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize subscriber node: {}", e.getMessage());
                        continue;
                    }
                    if (subscriber == null) continue;

                    // Only active subscribers
                    if (subscriber.getActive() == null || !subscriber.getActive()) {
                        continue;
                    }

                    // Filter matching (simple parsing of "key=value" and substring match against job.resultSummary)
                    boolean matches = true;
                    String filters = subscriber.getFilters();
                    if (filters != null && !filters.isBlank()) {
                        // support simple single filter like "category=Chemistry" or "year=2010"
                        String[] parts = filters.split("=", 2);
                        if (parts.length == 2) {
                            String expectedValue = parts[1].trim();
                            String resultSummary = job.getResultSummary();
                            if (resultSummary == null || !resultSummary.toLowerCase().contains(expectedValue.toLowerCase())) {
                                matches = false;
                            }
                        } else {
                            // If filter is present but not parseable, try contains check
                            String resultSummary = job.getResultSummary();
                            if (resultSummary == null || !resultSummary.toLowerCase().contains(filters.toLowerCase())) {
                                matches = false;
                            }
                        }
                    }

                    if (!matches) {
                        continue;
                    }

                    boolean delivered = false;
                    String contactType = subscriber.getContactType();
                    String contactAddress = subscriber.getContactAddress();

                    if (contactType != null && "WEBHOOK".equalsIgnoreCase(contactType) && contactAddress != null && !contactAddress.isBlank()) {
                        try {
                            String payload = objectMapper.writeValueAsString(job);
                            HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create(contactAddress))
                                .timeout(Duration.ofSeconds(10))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload))
                                .build();

                            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            int status = response.statusCode();
                            if (status >= 200 && status < 300) {
                                delivered = true;
                            } else {
                                logger.warn("Failed to deliver webhook to {} for subscriber {}: status={}", contactAddress, subscriber.getSubscriberId(), status);
                            }
                        } catch (Exception e) {
                            logger.warn("Error while sending webhook to {} for subscriber {}: {}", contactAddress, subscriber.getSubscriberId(), e.getMessage());
                        }
                    } else if (contactType != null && "EMAIL".equalsIgnoreCase(contactType) && contactAddress != null && !contactAddress.isBlank()) {
                        // Email sending is out of scope; simulate delivery attempt success by logging
                        logger.info("Simulating email send to {} for subscriber {} about job {}", contactAddress, subscriber.getSubscriberId(), job.getJobId());
                        delivered = true;
                    } else {
                        // Unsupported contact type - skip
                        logger.info("Unsupported contactType '{}' for subscriber {}", contactType, subscriber.getSubscriberId());
                    }

                    if (delivered) {
                        // update subscriber.lastNotifiedAt and persist via entityService.updateItem
                        try {
                            subscriber.setLastNotifiedAt(Instant.now().toString());
                            String technicalId = subscriber.getId();
                            if (technicalId != null && !technicalId.isBlank()) {
                                CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                                    Subscriber.ENTITY_NAME,
                                    String.valueOf(Subscriber.ENTITY_VERSION),
                                    UUID.fromString(technicalId),
                                    subscriber
                                );
                                updatedIdFuture.join();
                                logger.info("Updated subscriber.lastNotifiedAt for subscriber {}", subscriber.getSubscriberId());
                            } else {
                                logger.warn("Subscriber missing technical id; cannot update lastNotifiedAt for subscriberId={}", subscriber.getSubscriberId());
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to update subscriber lastNotifiedAt for {}: {}", subscriber.getSubscriberId(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while notifying subscribers for job {}: {}", job.getJobId(), e.getMessage());
            // do not set job to NOTIFIED_SUBSCRIBERS in case of catastrophic failure? Per requirements we still transition.
        }

        // Transition job to NOTIFIED_SUBSCRIBERS
        job.setState("NOTIFIED_SUBSCRIBERS");
        // set finishedAt if not present
        if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
            job.setFinishedAt(Instant.now().toString());
        }

        return job;
    }
}