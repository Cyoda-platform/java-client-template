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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

@Component
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public SubscriberValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        // - For webhook subscribers: attempt a lightweight GET to the webhook URL.
        //   If response is 2xx -> mark verified = true, otherwise verified = false.
        // - For email and other contact types: do not attempt network verification here;
        //   ensure verified is non-null (default false) so downstream processors can act.
        // - Do not change activation state automatically; validation only affects 'verified' flag.
        try {
            String contactType = entity.getContactType();
            if (contactType != null && contactType.equalsIgnoreCase("webhook")) {
                Subscriber.ContactDetails cd = entity.getContactDetails();
                String url = cd != null ? cd.getUrl() : null;
                if (url != null && !url.isBlank()) {
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                        HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                        int status = resp.statusCode();
                        boolean ok = status >= 200 && status < 300;
                        entity.setVerified(Boolean.valueOf(ok));
                        logger.info("Webhook verification for subscriber {} returned status {}; verified={}", entity.getId(), status, ok);
                    } catch (Exception ex) {
                        logger.warn("Failed to verify webhook for subscriber {}: {}", entity.getId(), ex.getMessage());
                        entity.setVerified(Boolean.FALSE);
                    }
                } else {
                    logger.warn("Webhook subscriber {} has no URL; marking as unverified", entity.getId());
                    entity.setVerified(Boolean.FALSE);
                }
            } else {
                // For email and other types we don't verify here.
                if (entity.getVerified() == null) {
                    entity.setVerified(Boolean.FALSE);
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error during subscriber validation for {}: {}", entity != null ? entity.getId() : "unknown", e.getMessage(), e);
            if (entity != null) {
                entity.setVerified(Boolean.FALSE);
            }
        }

        return entity;
    }
}