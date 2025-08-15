package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Component
public class DeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DeliveryProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Delivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber subscriber) {
        return subscriber != null && subscriber.getTechnicalId() != null && !subscriber.getTechnicalId().isEmpty();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        try {
            logger.info("Delivering notification to subscriber {} (type={})", subscriber.getTechnicalId(), subscriber.getContactType());

            boolean delivered = false;
            String failureReason = null;

            // Build a minimal payload from subscriber preferences if present - in many flows the payload will be passed via a separate event
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("subscriberTechnicalId", subscriber.getTechnicalId());
            payload.put("timestamp", Instant.now().toString());

            if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("webhook")) {
                String url = subscriber.getContactDetails();
                String idempotencyKey = subscriber.getTechnicalId() + ":delivery"; // best-effort idempotency key
                try {
                    delivered = deliverWebhook(url, payload, idempotencyKey);
                } catch (Exception e) {
                    delivered = false;
                    failureReason = e.getMessage();
                }
            } else if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("email")) {
                // Simulate email delivery for now (real implementation would integrate with an email provider)
                logger.info("Simulating email delivery to {} for subscriber {}", subscriber.getContactDetails(), subscriber.getTechnicalId());
                delivered = true;
            } else {
                logger.warn("Unknown contactType {} for subscriber {}", subscriber.getContactType(), subscriber.getTechnicalId());
                delivered = false;
                failureReason = "Unknown contactType";
            }

            if (delivered) {
                subscriber.setLastNotifiedAt(Instant.now().toString());
                subscriber.setLastNotificationStatus("DELIVERED");
            } else {
                subscriber.setLastNotificationStatus("FAILED");
            }

            if (!delivered) {
                logger.warn("Delivery failed for subscriber {}: {}", subscriber.getTechnicalId(), failureReason);
            }

        } catch (Exception e) {
            logger.error("Delivery failed for subscriber {}: {}", subscriber != null ? subscriber.getTechnicalId() : "unknown", e.getMessage(), e);
        }

        // Return the modified subscriber; persistence of the subscriber context will be handled by the framework serializer
        return subscriber;
    }

    private boolean deliverWebhook(String url, ObjectNode payload, String idempotencyKey) throws Exception {
        int attempts = 0;
        int maxAttempts = 3;
        Duration backoff = Duration.ofSeconds(2);
        while (attempts < maxAttempts) {
            attempts++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code >= 200 && code < 300) return true;
                if (code >= 500 && attempts < maxAttempts) {
                    Thread.sleep(backoff.toMillis());
                    backoff = backoff.multipliedBy(2);
                    continue;
                }
                // Non-retryable
                throw new RuntimeException("HTTP error " + code + ": " + response.body());
            } catch (Exception e) {
                if (attempts >= maxAttempts) throw e;
                Thread.sleep(backoff.toMillis());
                backoff = backoff.multipliedBy(2);
            }
        }
        return false;
    }
}
