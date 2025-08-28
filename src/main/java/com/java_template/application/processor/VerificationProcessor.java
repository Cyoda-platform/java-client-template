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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class VerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public VerificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
        // - If delivery preference is "webhook": attempt a lightweight POST to webhookUrl.
        //   On successful HTTP 2xx response -> mark active = true, otherwise active = false.
        // - If delivery preference is "email": perform simple email format validation.
        //   If valid -> active = true, otherwise active = false.
        // - For any other delivery preference or missing contact -> set active = false.

        String deliveryPref = entity.getDeliveryPreference();
        if (deliveryPref == null) {
            logger.warn("Subscriber {} has null deliveryPreference -> marking inactive", entity.getSubscriberId());
            entity.setActive(false);
            return entity;
        }

        try {
            if ("webhook".equalsIgnoreCase(deliveryPref)) {
                String webhook = entity.getWebhookUrl();
                if (webhook == null || webhook.isBlank()) {
                    logger.warn("Subscriber {} requested webhook but webhookUrl is missing -> marking inactive", entity.getSubscriberId());
                    entity.setActive(false);
                    return entity;
                }
                // Prepare simple verification payload
                String payload = objectMapper.writeValueAsString(
                        new java.util.HashMap<String, String>() {{
                            put("event", "verification");
                            put("subscriberId", entity.getSubscriberId());
                        }}
                );
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(webhook))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                try {
                    HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                    int status = resp.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.info("Webhook verification succeeded for subscriber {} (status {})", entity.getSubscriberId(), status);
                        entity.setActive(true);
                    } else {
                        logger.warn("Webhook verification failed for subscriber {} (status {})", entity.getSubscriberId(), status);
                        entity.setActive(false);
                    }
                } catch (Exception e) {
                    logger.error("Error calling webhook for subscriber {}: {}", entity.getSubscriberId(), e.getMessage());
                    entity.setActive(false);
                }
            } else if ("email".equalsIgnoreCase(deliveryPref)) {
                String email = entity.getContactEmail();
                if (email == null || email.isBlank()) {
                    logger.warn("Subscriber {} requested email delivery but contactEmail is missing -> marking inactive", entity.getSubscriberId());
                    entity.setActive(false);
                    return entity;
                }
                // Simple email validation
                String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
                boolean validEmail = email.matches(emailPattern);
                if (validEmail) {
                    logger.info("Email verification format OK for subscriber {}", entity.getSubscriberId());
                    entity.setActive(true);
                } else {
                    logger.warn("Email verification format invalid for subscriber {} -> marking inactive", entity.getSubscriberId());
                    entity.setActive(false);
                }
            } else {
                logger.warn("Unknown delivery preference '{}' for subscriber {} -> marking inactive", deliveryPref, entity.getSubscriberId());
                entity.setActive(false);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during verification for subscriber {}: {}", entity.getSubscriberId(), ex.getMessage(), ex);
            entity.setActive(false);
        }

        return entity;
    }
}