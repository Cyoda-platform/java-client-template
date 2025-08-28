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

@Component
public class SubscriberVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public SubscriberVerificationProcessor(SerializerFactory serializerFactory,
                                           EntityService entityService,
                                           ObjectMapper objectMapper) {
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

        try {
            // If already active, nothing to do
            if (Boolean.TRUE.equals(entity.getActive())) {
                logger.info("Subscriber {} already active. No verification required.", entity.getId());
                return entity;
            }

            String contactType = entity.getContactType();
            String contactDetail = entity.getContactDetail();

            if (contactType == null || contactDetail == null || contactDetail.isBlank()) {
                logger.warn("Subscriber {} has missing contactType or contactDetail. Keeping inactive.", entity.getId());
                entity.setActive(false);
                return entity;
            }

            // For webhook contacts, attempt a lightweight verification call.
            if ("webhook".equalsIgnoreCase(contactType)) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(contactDetail))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .method("GET", HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                    int status = resp.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.info("Webhook verification succeeded for subscriber {} with status {}", entity.getId(), status);
                        entity.setActive(true);
                    } else {
                        logger.warn("Webhook verification failed for subscriber {} with status {}", entity.getId(), status);
                        entity.setActive(false);
                    }
                } catch (Exception ex) {
                    logger.error("Error while verifying webhook for subscriber {}: {}", entity.getId(), ex.getMessage());
                    entity.setActive(false);
                }
                return entity;
            }

            // For email contacts, verification is manual (e.g., user clicks link).
            if ("email".equalsIgnoreCase(contactType)) {
                // We cannot send an email here (no mail service). Keep inactive and log.
                logger.info("Subscriber {} uses email verification. Manual verification required; keeping inactive.", entity.getId());
                entity.setActive(false);
                return entity;
            }

            // Unknown contact type: keep inactive and log
            logger.warn("Subscriber {} has unknown contactType '{}'. Keeping inactive.", entity.getId(), contactType);
            entity.setActive(false);
            return entity;

        } catch (Exception e) {
            logger.error("Unhandled error during subscriber verification for {}: {}", entity != null ? entity.getId() : "unknown", e.getMessage(), e);
            if (entity != null) {
                entity.setActive(false);
            }
            return entity;
        }
    }
}