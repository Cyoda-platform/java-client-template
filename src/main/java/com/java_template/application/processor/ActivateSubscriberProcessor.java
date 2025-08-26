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

import java.lang.reflect.Method;

@Component
public class ActivateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateSubscriberProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        String subscriberId = "unknown";
        try {
            Method getSubscriberId = entity.getClass().getMethod("getSubscriberId");
            Object idObj = getSubscriberId.invoke(entity);
            if (idObj != null) {
                subscriberId = idObj.toString();
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.debug("Unable to obtain subscriberId via reflection", e);
        }

        // Check current status via reflection; if ACTIVE, return early
        try {
            Method getStatus = entity.getClass().getMethod("getStatus");
            Object statusObj = getStatus.invoke(entity);
            String status = statusObj != null ? statusObj.toString() : null;
            if (status != null && status.equalsIgnoreCase("ACTIVE")) {
                logger.info("Subscriber {} is already ACTIVE", subscriberId);
                return entity;
            }
        } catch (NoSuchMethodException ignored) {
            // No getStatus() method - cannot determine status, proceed to attempt activation
        } catch (Exception e) {
            logger.debug("Unable to read status via reflection", e);
        }

        // Activation rule: move subscriber into ACTIVE state.
        // Activation assumes entity has passed validation (contact and filters validated by prior processor).
        try {
            Method setStatus = entity.getClass().getMethod("setStatus", String.class);
            setStatus.invoke(entity, "ACTIVE");
        } catch (NoSuchMethodException ignored) {
            // If setter doesn't exist, skip but continue execution
        } catch (Exception e) {
            logger.debug("Unable to set status via reflection", e);
        }
        logger.info("Subscriber {} activated", subscriberId);

        // Ensure a sensible default format if none provided, using reflection to avoid compile-time dependency on accessors
        try {
            Method getFormat = entity.getClass().getMethod("getFormat");
            Object fmtObj = getFormat.invoke(entity);
            String format = fmtObj != null ? fmtObj.toString() : null;
            boolean blank = (format == null) || format.isBlank();
            if (blank) {
                try {
                    Method setFormat = entity.getClass().getMethod("setFormat", String.class);
                    setFormat.invoke(entity, "summary");
                    logger.debug("Defaulted format to 'summary' for subscriber {}", subscriberId);
                } catch (NoSuchMethodException ignored) {
                    // No setter available; nothing to do
                }
            }
        } catch (NoSuchMethodException ignored) {
            // No getFormat() method - nothing to do
        } catch (Exception e) {
            logger.debug("Unable to handle format via reflection", e);
        }

        return entity;
    }
}