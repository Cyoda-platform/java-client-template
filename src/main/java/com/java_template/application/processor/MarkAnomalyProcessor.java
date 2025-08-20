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
public class MarkAnomalyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkAnomalyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkAnomalyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkAnomaly for request: {}", request.getId());

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
            activity.setProcessed(true);
            activity.setFailureReason(null);
            logger.info("Marked activity {} as processed after anomaly flag", activity.getActivityId());
        } catch (Exception ex) {
            logger.error("Error marking anomaly processed", ex);
            activity.setFailureReason("mark anomaly error: " + ex.getMessage());
        }
        return activity;
    }
}
