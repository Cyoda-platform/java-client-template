package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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
    private final HttpClient httpClient;

    public ContactVerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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

        try {
            // Use reflection to access fields to avoid relying on generated accessors at compile-time.
            String contactType = (String) getFieldValue(entity, "contactType");
            Object contactDetails = getFieldValue(entity, "contactDetails");
            Boolean verified = Boolean.FALSE;

            String subscriberId = safeString(getFieldValue(entity, "id"));

            if (contactType != null) {
                String ct = contactType.trim().toLowerCase();

                if ("webhook".equals(ct)) {
                    // Attempt HTTP GET to webhook URL; success if 2xx
                    String url = extractUrlFromContactDetails(contactDetails);
                    if (url != null && !url.isBlank()) {
                        try {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build();
                            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                            int status = resp.statusCode();
                            verified = status >= 200 && status < 300;
                            logger.info("Webhook verification for subscriber {} returned status {}", subscriberId, status);
                        } catch (Exception e) {
                            verified = Boolean.FALSE;
                            logger.warn("Failed to verify webhook for subscriber {} : {}", subscriberId, e.getMessage());
                        }
                    } else {
                        verified = Boolean.FALSE;
                        logger.warn("Webhook contactDetails.url missing for subscriber {}", subscriberId);
                    }
                } else if ("email".equals(ct)) {
                    // Basic heuristic: if contactDetails.url contains '@' or starts with mailto:
                    String url = extractUrlFromContactDetails(contactDetails);
                    if (url != null && !url.isBlank()) {
                        String trimmed = url.trim();
                        if (trimmed.startsWith("mailto:")) {
                            String addr = trimmed.substring(Math.min(7, trimmed.length()));
                            verified = addr.contains("@");
                        } else {
                            verified = trimmed.contains("@");
                        }
                        logger.info("Email verification heuristic for subscriber {} result={}", subscriberId, verified);
                    } else {
                        verified = Boolean.FALSE;
                        logger.warn("Email contactDetails.url missing for subscriber {}", subscriberId);
                    }
                } else {
                    // Other contact types: cannot actively verify -> mark as false
                    verified = Boolean.FALSE;
                    logger.info("Unsupported contactType '{}' for subscriber {}, leaving verified=false", contactType, subscriberId);
                }
            } else {
                logger.warn("contactType is null for subscriber {}", subscriberId);
            }

            // Persist verification flag back into the entity using reflection so persistence picks it up.
            setFieldValue(entity, "verified", verified);
        } catch (Exception ex) {
            logger.error("Unexpected error during contact verification for subscriber {}: {}", entity != null ? safeString(getFieldValue(entity, "id")) : "unknown", ex.getMessage(), ex);
            if (entity != null) {
                try {
                    setFieldValue(entity, "verified", Boolean.FALSE);
                } catch (Exception ignore) {
                    logger.warn("Failed to set verified=false for subscriber after exception");
                }
            }
        }

        return entity;
    }

    // Helper to safely extract 'url' from contactDetails object via reflection
    private String extractUrlFromContactDetails(Object contactDetails) {
        if (contactDetails == null) return null;
        try {
            Object urlObj = getFieldValue(contactDetails, "url");
            return urlObj != null ? String.valueOf(urlObj) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Reflection helpers
    private Object getFieldValue(Object target, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        if (target == null) return null;
        Field f = findField(target.getClass(), fieldName);
        if (f == null) throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + target.getClass());
        f.setAccessible(true);
        return f.get(target);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        if (target == null) return;
        Field f = findField(target.getClass(), fieldName);
        if (f == null) throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + target.getClass());
        f.setAccessible(true);
        // attempt simple type conversion for primitives/boxed types
        Class<?> type = f.getType();
        if (value == null) {
            f.set(target, null);
            return;
        }
        if (type.isPrimitive()) {
            // handle common primitive types
            if (type == boolean.class && value instanceof Boolean) {
                f.setBoolean(target, (Boolean) value);
                return;
            }
            // add other primitives if needed
        } else {
            // boxed types or object types
            if (!type.isAssignableFrom(value.getClass())) {
                // try simple conversions
                if (type == Boolean.class && value instanceof Boolean) {
                    f.set(target, value);
                    return;
                }
                if (type == String.class) {
                    f.set(target, String.valueOf(value));
                    return;
                }
            } else {
                f.set(target, value);
                return;
            }
            // fallback attempt
            f.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private String safeString(Object o) {
        return o != null ? String.valueOf(o) : "null";
    }
}