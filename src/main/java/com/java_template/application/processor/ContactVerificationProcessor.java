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
import java.time.Duration;

@Component
public class ContactVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ContactVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final HttpClient httpClient;

    public ContactVerificationProcessor(SerializerFactory serializerFactory,
                                        ObjectMapper objectMapper,
                                        EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
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
            String contactType = entity.getContactType();
            Subscriber.ContactDetails details = entity.getContactDetails();
            boolean verified = false;

            if (contactType != null) {
                String ct = contactType.trim().toLowerCase();

                if ("webhook".equals(ct)) {
                    // Attempt HTTP GET to webhook URL; success if 2xx
                    if (details != null && details.getUrl() != null && !details.getUrl().isBlank()) {
                        try {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(details.getUrl()))
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build();
                            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                            int status = resp.statusCode();
                            verified = status >= 200 && status < 300;
                            logger.info("Webhook verification for subscriber {} returned status {}", entity.getId(), status);
                        } catch (Exception e) {
                            verified = false;
                            logger.warn("Failed to verify webhook for subscriber {} : {}", entity.getId(), e.getMessage());
                        }
                    } else {
                        verified = false;
                        logger.warn("Webhook contactDetails.url missing for subscriber {}", entity.getId());
                    }
                } else if ("email".equals(ct)) {
                    // Basic heuristic: if contactDetails.url contains '@' or starts with mailto:
                    if (details != null && details.getUrl() != null && !details.getUrl().isBlank()) {
                        String url = details.getUrl().trim();
                        if (url.startsWith("mailto:")) {
                            String addr = url.substring(7);
                            verified = addr.contains("@");
                        } else {
                            verified = url.contains("@");
                        }
                        logger.info("Email verification heuristic for subscriber {} result={}", entity.getId(), verified);
                    } else {
                        verified = false;
                        logger.warn("Email contactDetails.url missing for subscriber {}", entity.getId());
                    }
                } else {
                    // Other contact types: cannot actively verify -> mark as false
                    verified = false;
                    logger.info("Unsupported contactType '{}' for subscriber {}, leaving verified=false", contactType, entity.getId());
                }
            } else {
                logger.warn("contactType is null for subscriber {}", entity.getId());
            }

            entity.setVerified(verified);
        } catch (Exception ex) {
            logger.error("Unexpected error during contact verification for subscriber {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // On unexpected error, mark as not verified to be safe
            if (entity != null) {
                entity.setVerified(Boolean.FALSE);
            }
        }

        return entity;
    }
}