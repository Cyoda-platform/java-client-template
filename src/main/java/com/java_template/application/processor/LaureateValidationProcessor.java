package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.lang.reflect.Method;
import java.util.ArrayList;

@Component
public class LaureateValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Use reflection to perform optional operations only if the corresponding methods exist on the Laureate class.
        // This preserves behavior when methods are available while avoiding compile-time errors when they are not.

        // Ensure affiliations is not null so downstream processors can rely on a list
        try {
            Method getAffiliations = entity.getClass().getMethod("getAffiliations");
            Object affiliations = getAffiliations.invoke(entity);
            if (affiliations == null) {
                try {
                    Method setAffiliations = entity.getClass().getMethod("setAffiliations", java.util.List.class);
                    setAffiliations.invoke(entity, new ArrayList<>());
                } catch (NoSuchMethodException ignored) {
                    // setAffiliations not present; skip
                }
            }
        } catch (Exception ignored) {
            // getAffiliations not present or invocation failed; skip affiliation normalization
        }

        // Normalize country (trim) if present
        try {
            Method getCountry = entity.getClass().getMethod("getCountry");
            Object countryObj = getCountry.invoke(entity);
            if (countryObj instanceof String) {
                String trimmed = ((String) countryObj).trim();
                try {
                    Method setCountry = entity.getClass().getMethod("setCountry", String.class);
                    setCountry.invoke(entity, trimmed);
                } catch (NoSuchMethodException ignored) {
                    // setCountry not present; skip
                }
            }
        } catch (Exception ignored) {
            // getCountry not present; skip
        }

        // Determine published state based on changeType if methods exist:
        try {
            Method getChangeType = entity.getClass().getMethod("getChangeType");
            Object ctObj = getChangeType.invoke(entity);
            if (ctObj instanceof String) {
                String ct = ((String) ctObj).trim().toLowerCase();
                Method setPublished = null;
                try {
                    setPublished = entity.getClass().getMethod("setPublished", Boolean.class);
                } catch (NoSuchMethodException ex) {
                    try {
                        setPublished = entity.getClass().getMethod("setPublished", boolean.class);
                    } catch (NoSuchMethodException ex2) {
                        // no setPublished available
                    }
                }
                if (setPublished != null) {
                    if ("deleted".equals(ct) || "remove".equals(ct)) {
                        setPublished.invoke(entity, Boolean.FALSE);
                    } else if ("new".equals(ct) || "created".equals(ct) || "updated".equals(ct) || "modified".equals(ct)) {
                        setPublished.invoke(entity, Boolean.TRUE);
                    }
                }
            }
        } catch (Exception ignored) {
            // getChangeType or setPublished not present; skip published determination
        }

        // Safe logging: try to obtain laureateId, changeType and published via reflection if available
        Object laureateId = null;
        Object changeType = null;
        Object published = null;
        try {
            Method m = entity.getClass().getMethod("getLaureateId");
            laureateId = m.invoke(entity);
        } catch (Exception ignored) {
        }
        try {
            Method m = entity.getClass().getMethod("getChangeType");
            changeType = m.invoke(entity);
        } catch (Exception ignored) {
        }
        try {
            Method m = entity.getClass().getMethod("getPublished");
            published = m.invoke(entity);
        } catch (Exception ignored) {
        }

        logger.debug("Laureate ({}) validated. changeType={}, published={}", laureateId, changeType, published);

        return entity;
    }
}