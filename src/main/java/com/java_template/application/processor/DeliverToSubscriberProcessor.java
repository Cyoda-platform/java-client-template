package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
import java.util.Map;

@Component
public class DeliverToSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliverToSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public DeliverToSubscriberProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
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
        // Only deliver to valid and active subscribers
        return entity != null && entity.isValid() && Boolean.TRUE.equals(entity.getActive());
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        if (entity == null) {
            logger.warn("Subscriber entity is null in execution context");
            return null;
        }

        // Update lastNotifiedAt on any attempt
        String notifiedAt = Instant.now().toString();

        try {
            String contactType = entity.getContactType() != null ? entity.getContactType().toLowerCase(Locale.ROOT).trim() : "unknown";
            if ("webhook".equals(contactType)) {
                if (entity.getContactDetails() == null || entity.getContactDetails().getUrl() == null || entity.getContactDetails().getUrl().isBlank()) {
                    logger.warn("Subscriber {} has no webhook URL configured", entity.getSubscriberId());
                } else {
                    String url = entity.getContactDetails().getUrl();
                    // Build payload - minimal notification (subscribers may request preferredPayload but full payload building
                    // is performed by DeliveryBuilderProcessor; keep a simple notification here)
                    Map<String, Object> payload = Map.of(
                        "subscriberId", entity.getSubscriberId(),
                        "preferredPayload", entity.getPreferredPayload(),
                        "notifiedAt", notifiedAt
                    );
                    String body = objectMapper.writeValueAsString(payload);

                    HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    int status = resp.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.info("Successfully delivered notification to subscriber {} via webhook {} (status={})", entity.getSubscriberId(), url, status);
                    } else {
                        logger.warn("Failed to deliver notification to subscriber {} via webhook {} (status={}, body={})", entity.getSubscriberId(), url, status, resp.body());
                    }
                }
            } else if ("email".equals(contactType)) {
                // Email delivery is out-of-scope for this processor (no mail service injected).
                // Simulate/email enqueue by logging; real implementation would call an email service.
                logger.info("Simulated email delivery to subscriber {} (preferredPayload={})", entity.getSubscriberId(), entity.getPreferredPayload());
            } else {
                logger.warn("Unsupported contactType '{}' for subscriber {}", entity.getContactType(), entity.getSubscriberId());
            }
        } catch (Exception e) {
            logger.error("Error while delivering notification to subscriber {}: {}", entity.getSubscriberId(), e.getMessage(), e);
        } finally {
            // Update the entity's lastNotifiedAt timestamp so Cyoda persists this change
            try {
                entity.setLastNotifiedAt(notifiedAt);
            } catch (Exception ex) {
                logger.warn("Unable to set lastNotifiedAt for subscriber {}: {}", entity.getSubscriberId(), ex.getMessage());
            }
        }

        return entity;
    }
}