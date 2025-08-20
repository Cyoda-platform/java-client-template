package com.java_template.application.processor;

import com.java_template.application.entity.activity.version_1.Activity;
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

@Component
public class ValidateActivityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateActivityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateActivityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateActivity for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Activity.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Activity entity) {
        return entity != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();

        try {
            if (activity.getUserId() == null || activity.getUserId().trim().isEmpty()) {
                activity.setValid(false);
                activity.setFailureReason("userId is required");
                logger.warn("Activity {} validation failed: missing userId", activity.getActivityId());
                return activity;
            }

            if (activity.getTimestamp() == null || activity.getTimestamp().trim().isEmpty()) {
                activity.setValid(false);
                activity.setFailureReason("timestamp is required");
                logger.warn("Activity {} validation failed: missing timestamp", activity.getActivityId());
                return activity;
            }

            if (activity.getType() == null || activity.getType().trim().isEmpty()) {
                activity.setValid(false);
                activity.setFailureReason("type is required");
                logger.warn("Activity {} validation failed: missing type", activity.getActivityId());
                return activity;
            }

            activity.setValid(true);
            activity.setFailureReason(null);
            logger.info("Activity {} validation passed", activity.getActivityId());
        } catch (Exception ex) {
            logger.error("Unexpected error during ValidateActivityProcessor", ex);
            activity.setValid(false);
            activity.setFailureReason("validation error: " + ex.getMessage());
        }

        return activity;
    }
}
