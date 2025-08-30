package com.java_template.application.processor;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

@Component
public class AddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Cart.class)
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

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart entity = context.entity();

        // Use reflection to avoid direct compile-time dependency on Lombok-generated getters/setters
        // This ensures the processor compiles even if annotation processing for Lombok is not available.
        try {
            // Ensure lines list exists
            Object linesObj = safeInvoke(entity, "getLines");
            List<?> lines;
            if (linesObj == null) {
                lines = new ArrayList<>();
                safeInvokeSetter(entity, "setLines", List.class, lines);
            } else if (linesObj instanceof List) {
                lines = (List<?>) linesObj;
            } else {
                // unexpected type, create new list and set
                lines = new ArrayList<>();
                safeInvokeSetter(entity, "setLines", List.class, lines);
            }

            // Recalculate totals and total items
            int totalItems = 0;
            double grandTotal = 0.0;

            if (lines != null) {
                for (Object line : lines) {
                    if (line == null) continue;
                    // Prefer calling line.isValid() if present
                    Boolean lineValid = (Boolean) safeInvoke(line, "isValid");
                    if (lineValid != null && !lineValid) continue;

                    Number qtyNum = (Number) safeInvoke(line, "getQty");
                    Number priceNum = (Number) safeInvoke(line, "getPrice");

                    int qty = qtyNum != null ? qtyNum.intValue() : 0;
                    double price = priceNum != null ? priceNum.doubleValue() : 0.0;

                    if (qty <= 0 || price < 0.0) {
                        // skip invalid numeric values
                        continue;
                    }

                    totalItems += qty;
                    grandTotal += price * qty;
                }
            }

            // Set totals via reflection
            safeInvokeSetter(entity, "setTotalItems", Integer.class, Integer.valueOf(totalItems));
            safeInvokeSetter(entity, "setGrandTotal", Double.class, Double.valueOf(grandTotal));

            // Status transition: if NEW (or missing) and now has items, move to ACTIVE
            String status = (String) safeInvoke(entity, "getStatus");
            if (status == null || status.isBlank()) {
                // set to NEW by default if setter exists
                safeInvokeSetter(entity, "setStatus", String.class, "NEW");
                status = "NEW";
            }
            if ("NEW".equalsIgnoreCase(status) && totalItems > 0) {
                safeInvokeSetter(entity, "setStatus", String.class, "ACTIVE");
            }

            // Update updatedAt timestamp
            safeInvokeSetter(entity, "setUpdatedAt", String.class, Instant.now().toString());

        } catch (Exception ex) {
            // Log and return entity unchanged if unexpected error occurs
            logger.error("Error processing AddItem logic for cart: {}", ex.getMessage(), ex);
        }

        return entity;
    }

    /**
     * Safely invoke a no-arg getter or method on target and return result.
     * Returns null if method not found or invocation fails.
     */
    private Object safeInvoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = findMethod(target.getClass(), methodName);
            if (m == null) return null;
            return m.invoke(target);
        } catch (Exception e) {
            logger.debug("safeInvoke failed for {}.{}(): {}", target.getClass().getSimpleName(), methodName, e.getMessage());
            return null;
        }
    }

    /**
     * Safely invoke a setter with a single parameter.
     * Returns true if invocation succeeded.
     */
    private boolean safeInvokeSetter(Object target, String setterName, Class<?> paramType, Object value) {
        if (target == null) return false;
        try {
            Method m = findMethod(target.getClass(), setterName, paramType);
            if (m == null) {
                // Try to find any setter with the name and compatible parameter
                Method candidate = findCompatibleSetter(target.getClass(), setterName, value);
                if (candidate == null) return false;
                candidate.invoke(target, value);
                return true;
            }
            m.invoke(target, value);
            return true;
        } catch (Exception e) {
            logger.debug("safeInvokeSetter failed for {}.{}({}): {}", target.getClass().getSimpleName(), setterName, paramType != null ? paramType.getSimpleName() : "?", e.getMessage());
            return false;
        }
    }

    private Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        try {
            return cls.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try declared methods (including inner classes)
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name)) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (paramTypes == null || paramTypes.length == 0 || pts.length == paramTypes.length) {
                        return m;
                    }
                }
            }
            return null;
        }
    }

    private Method findCompatibleSetter(Class<?> cls, String name, Object value) {
        if (value == null) {
            // when value is null, attempt to find any setter named 'name'
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    return m;
                }
            }
            return null;
        }
        Class<?> valClass = value.getClass();
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != 1) continue;
            Class<?> param = m.getParameterTypes()[0];
            if (param.isAssignableFrom(valClass)) return m;
            // allow some primitive-wrapper compatibility
            if (isPrimitiveWrapperAssignable(param, valClass)) return m;
        }
        return null;
    }

    private boolean isPrimitiveWrapperAssignable(Class<?> param, Class<?> valClass) {
        if (param.isPrimitive()) {
            if (param == int.class && Integer.class.isAssignableFrom(valClass)) return true;
            if (param == long.class && Long.class.isAssignableFrom(valClass)) return true;
            if (param == double.class && Double.class.isAssignableFrom(valClass)) return true;
            if (param == float.class && Float.class.isAssignableFrom(valClass)) return true;
            if (param == boolean.class && Boolean.class.isAssignableFrom(valClass)) return true;
            if (param == short.class && Short.class.isAssignableFrom(valClass)) return true;
            if (param == byte.class && Byte.class.isAssignableFrom(valClass)) return true;
            if (param == char.class && Character.class.isAssignableFrom(valClass)) return true;
        } else {
            // wrapper to primitive compatibility
            if (param == Integer.class && int.class.isAssignableFrom(valClass)) return true;
        }
        return false;
    }
}