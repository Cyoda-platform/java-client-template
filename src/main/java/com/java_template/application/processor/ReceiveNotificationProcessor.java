package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class ReceiveNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReceiveNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ReceiveNotificationProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        if (entity == null) return null;

        // Only process active subscribers
        if (entity.getActive() == null || !entity.getActive()) {
            logger.info("Subscriber {} is not active; skipping notification", entity.getId());
            return entity;
        }

        // Build a minimal notification payload indicating the subscriber and timestamp.
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriberId", entity.getId());
        payload.put("subscriberName", entity.getName());
        payload.put("notifiedAt", now);

        String contactType = entity.getContactType();
        String contactDetails = entity.getContactDetails();

        try {
            if (contactType != null && contactType.equalsIgnoreCase("webhook") && contactDetails != null && !contactDetails.isBlank()) {
                // Attempt to POST notification payload to webhook URL
                String body = objectMapper.writeValueAsString(payload);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(contactDetails))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                logger.info("Webhook notification to {} responded with status {}", contactDetails, status);
                // Record attempt time regardless of success; detailed delivery results are handled by RecordDeliveryResultProcessor
                entity.setLastNotifiedAt(now);
            } else if (contactType != null && contactType.equalsIgnoreCase("email") && contactDetails != null && !contactDetails.isBlank()) {
                // Simulate email send by logging; actual email delivery handled elsewhere/integration
                logger.info("Simulating email notification to {} for subscriber {}", contactDetails, entity.getId());
                entity.setLastNotifiedAt(now);
            } else {
                // Other contact types or missing details: record attempt and log
                logger.warn("Unknown or missing contact details for subscriber {}: type={} details={}", entity.getId(), contactType, contactDetails);
                entity.setLastNotifiedAt(now);
            }
        } catch (Exception e) {
            // On exception, log it and still record the attempt time so downstream processors can act upon failures.
            logger.error("Failed to deliver notification to subscriber {} at {}: {}", entity.getId(), contactDetails, e.getMessage(), e);
            entity.setLastNotifiedAt(now);
        }

        return entity;
    }
}