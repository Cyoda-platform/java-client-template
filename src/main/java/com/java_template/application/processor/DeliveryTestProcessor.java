package com.java_template.application.processor;

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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class DeliveryTestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryTestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    @Autowired
    public DeliveryTestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Business logic:
        // DeliveryTestProcessor should attempt a delivery test based on subscriber delivery preference.
        // - For webhook preference: attempt an HTTP POST to webhookUrl with a small JSON payload.
        //   If POST returns 2xx -> mark active = true, otherwise active = false.
        // - For email preference: perform a basic validation of the email (presence of '@')
        //   and mark active = true if it looks valid, otherwise false.
        // - If delivery preference or required contact info is missing -> mark active = false.

        if (entity == null) {
            logger.warn("Subscriber entity is null in DeliveryTestProcessor");
            return entity;
        }

        String preference = entity.getDeliveryPreference();
        if (preference == null || preference.isBlank()) {
            logger.warn("Subscriber {} has no delivery preference, marking inactive", entity.getSubscriberId());
            entity.setActive(false);
            return entity;
        }

        try {
            if ("webhook".equalsIgnoreCase(preference)) {
                String webhook = entity.getWebhookUrl();
                if (webhook == null || webhook.isBlank()) {
                    logger.warn("Subscriber {} webhook preference but no webhookUrl provided", entity.getSubscriberId());
                    entity.setActive(false);
                    return entity;
                }

                // Prepare payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "delivery_test");
                payload.put("subscriberId", entity.getSubscriberId());
                payload.put("name", entity.getName());

                String body = objectMapper.writeValueAsString(payload);

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhook))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                logger.info("Sending delivery test webhook to {} for subscriber {}", webhook, entity.getSubscriberId());
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    logger.info("Webhook delivery test succeeded for subscriber {} with status {}", entity.getSubscriberId(), status);
                    entity.setActive(true);
                } else {
                    logger.warn("Webhook delivery test failed for subscriber {} with status {} and body {}", entity.getSubscriberId(), status, response.body());
                    entity.setActive(false);
                }

            } else if ("email".equalsIgnoreCase(preference)) {
                String email = entity.getContactEmail();
                if (email != null && email.contains("@")) {
                    // Simulate email delivery test success by basic validation
                    logger.info("Email delivery test considered successful for subscriber {} (email looks valid)", entity.getSubscriberId());
                    entity.setActive(true);
                } else {
                    logger.warn("Email delivery test failed/invalid email for subscriber {}", entity.getSubscriberId());
                    entity.setActive(false);
                }
            } else {
                logger.warn("Unknown delivery preference '{}' for subscriber {}, marking inactive", preference, entity.getSubscriberId());
                entity.setActive(false);
            }
        } catch (Exception ex) {
            logger.error("Exception during delivery test for subscriber {}: {}", entity.getSubscriberId(), ex.getMessage(), ex);
            entity.setActive(false);
        }

        return entity;
    }
}