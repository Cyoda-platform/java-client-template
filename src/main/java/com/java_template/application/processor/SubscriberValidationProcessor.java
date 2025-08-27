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
import java.lang.reflect.Field;

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

        // Implement business logic using reflection to avoid compile-time dependency on Lombok generated accessors.
        // Rationale:
        // Some build environments may not run Lombok annotation processing; using reflection ensures runtime access
        // to the fields declared in the Subscriber POJO without requiring generated getters/setters at compile time.
        try {
            // Read contactType
            String contactType = (String) readField(entity, "contactType");

            if (contactType != null && contactType.equalsIgnoreCase("webhook")) {
                Object contactDetailsObj = readField(entity, "contactDetails");
                String url = null;
                if (contactDetailsObj != null) {
                    url = (String) readField(contactDetailsObj, "url");
                }
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
                        writeField(entity, "verified", Boolean.valueOf(ok));
                        String id = String.valueOf(readField(entity, "id"));
                        logger.info("Webhook verification for subscriber {} returned status {}; verified={}", id, status, ok);
                    } catch (Exception ex) {
                        String id = String.valueOf(readField(entity, "id"));
                        logger.warn("Failed to verify webhook for subscriber {}: {}", id, ex.getMessage());
                        writeField(entity, "verified", Boolean.FALSE);
                    }
                } else {
                    String id = String.valueOf(readField(entity, "id"));
                    logger.warn("Webhook subscriber {} has no URL; marking as unverified", id);
                    writeField(entity, "verified", Boolean.FALSE);
                }
            } else {
                // For email and other types we don't verify here.
                Object verified = readField(entity, "verified");
                if (verified == null) {
                    writeField(entity, "verified", Boolean.FALSE);
                }
            }
        } catch (Exception e) {
            String id = "unknown";
            try {
                Object maybeId = readField(entity, "id");
                if (maybeId != null) id = String.valueOf(maybeId);
            } catch (Exception ignored) {}
            logger.error("Unexpected error during subscriber validation for {}: {}", id, e.getMessage(), e);
            try {
                writeField(entity, "verified", Boolean.FALSE);
            } catch (Exception ignored) {}
        }

        return entity;
    }

    /**
     * Read a declared field value using reflection. Works with private/protected fields.
     */
    private Object readField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        Field field = null;
        while (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            logger.debug("Unable to access field '{}' on {}: {}", fieldName, target.getClass().getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Write a declared field value using reflection. Works with private/protected fields.
     */
    private boolean writeField(Object target, String fieldName, Object value) {
        if (target == null) return false;
        Class<?> cls = target.getClass();
        Field field = null;
        while (cls != null) {
            try {
                field = cls.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        if (field == null) return false;
        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            logger.debug("Unable to set field '{}' on {}: {}", fieldName, target.getClass().getName(), e.getMessage());
            return false;
        }
    }
}