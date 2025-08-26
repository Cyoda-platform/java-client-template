package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.subscriber.version_1.Subscriber.Filters;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.regex.Pattern;

@Component
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        if (entity == null) return false;
        try {
            // Prefer existing isValid method if present
            Method m = entity.getClass().getMethod("isValid");
            Object res = m.invoke(entity);
            return res instanceof Boolean && (Boolean) res;
        } catch (NoSuchMethodException nsme) {
            // Fallback: basic non-null check
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Validate contactEndpoint format: accept basic email or http(s) URL.
        boolean contactValid = false;
        Object endpointObj = getProperty(entity, "contactEndpoint");
        String endpoint = endpointObj != null ? endpointObj.toString() : null;
        if (endpoint != null) {
            endpoint = endpoint.trim();
            // simple email pattern
            Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
            if (emailPattern.matcher(endpoint).matches()) {
                contactValid = true;
                // normalize email to lower-case
                setProperty(entity, "contactEndpoint", endpoint.toLowerCase());
            } else {
                // try parse as URI with http/https
                try {
                    URI uri = new URI(endpoint);
                    String scheme = uri.getScheme();
                    if (scheme != null) {
                        String s = scheme.toLowerCase();
                        if (s.equals("http") || s.equals("https")) {
                            contactValid = true;
                            // keep original endpoint (trimmed)
                            setProperty(entity, "contactEndpoint", endpoint);
                        }
                    }
                } catch (Exception ignored) {
                    contactValid = false;
                }
            }
        }

        // If contact is invalid, mark validation failed and return.
        if (!contactValid) {
            setProperty(entity, "status", "VALIDATION_FAILED");
            return entity;
        }

        // Contact is valid -> normalize and set defaults where appropriate.

        // Set default format if not provided
        Object formatObj = getProperty(entity, "format");
        String format = formatObj != null ? formatObj.toString() : null;
        if (format == null || format.isBlank()) {
            setProperty(entity, "format", "summary");
        } else {
            setProperty(entity, "format", format.trim().toLowerCase());
        }

        // Normalize filters if present
        Object filtersObj = getProperty(entity, "filters");
        if (filtersObj != null) {
            Object catObj = getProperty(filtersObj, "category");
            if (catObj != null) {
                String cat = catObj.toString().trim();
                if (!cat.isEmpty()) setProperty(filtersObj, "category", cat.toLowerCase());
                else setProperty(filtersObj, "category", null);
            }
            Object countryObj = getProperty(filtersObj, "country");
            if (countryObj != null) {
                String country = countryObj.toString().trim();
                if (!country.isEmpty()) setProperty(filtersObj, "country", country.toUpperCase());
                else setProperty(filtersObj, "country", null);
            }
            // prizeYear is Integer - nothing to normalize
        }

        // Transition to ACTIVE if currently in initial registration states.
        Object currentStatusObj = getProperty(entity, "status");
        String currentStatus = currentStatusObj != null ? currentStatusObj.toString() : null;
        if (currentStatus == null || currentStatus.isBlank()) {
            setProperty(entity, "status", "ACTIVE");
        } else {
            String cs = currentStatus.trim();
            if (cs.equalsIgnoreCase("REGISTERED") || cs.equalsIgnoreCase("PENDING")) {
                setProperty(entity, "status", "ACTIVE");
            } else {
                // keep existing status but normalize to upper-case standard
                setProperty(entity, "status", cs.toUpperCase());
            }
        }

        return entity;
    }

    // Reflection helpers to safely get/set properties via getter/setter or direct field access.

    private static Object getProperty(Object obj, String name) {
        if (obj == null || name == null || name.isEmpty()) return null;
        Class<?> cls = obj.getClass();
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            try {
                Method m = cls.getMethod("get" + cap);
                return m.invoke(obj);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method m = cls.getMethod("is" + cap);
                return m.invoke(obj);
            } catch (NoSuchMethodException ignored) {
            }
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean setProperty(Object obj, String name, Object value) {
        if (obj == null || name == null || name.isEmpty()) return false;
        Class<?> cls = obj.getClass();
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            // Try setter methods
            for (Method m : cls.getMethods()) {
                if (m.getName().equals("set" + cap) && m.getParameterCount() == 1) {
                    Object arg = convertValue(value, m.getParameterTypes()[0]);
                    m.invoke(obj, arg);
                    return true;
                }
            }
            // Field fallback
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            Object arg = convertValue(value, f.getType());
            f.set(obj, arg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        String s = value.toString();
        try {
            if (targetType == String.class) return s;
            if (targetType == Integer.class || targetType == int.class) {
                if (value instanceof Number) return ((Number) value).intValue();
                return Integer.parseInt(s);
            }
            if (targetType == Long.class || targetType == long.class) {
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(s);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof Boolean) return value;
                return Boolean.parseBoolean(s);
            }
            if (targetType == Double.class || targetType == double.class) {
                if (value instanceof Number) return ((Number) value).doubleValue();
                return Double.parseDouble(s);
            }
        } catch (Exception ignored) {
        }
        return value;
    }
}