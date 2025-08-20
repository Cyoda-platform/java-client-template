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
public class AnomalyDetectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AnomalyDetectionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnomalyDetection for request: {}", request.getId());

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
        return entity != null && entity.getUserId() != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();

        try {
            // Simple anomaly rule: if type == purchase and metadata contains highValue=true mark anomaly
            if ("purchase".equalsIgnoreCase(activity.getType())) {
                if (activity.getMetadata() != null && activity.getMetadata().has("highValue") && activity.getMetadata().get("highValue").asBoolean(false)) {
                    activity.setAnomalyFlag(true);
                    activity.setFailureReason(null);
                    logger.info("Activity {} marked as anomaly due to highValue", activity.getActivityId());
                    return activity;
                }
                // another simple heuristic: purchase by same user repeatedly within short timeframe could be anomalous
            }

            activity.setAnomalyFlag(false);
        } catch (Exception ex) {
            logger.error("Error during anomaly detection", ex);
            activity.setFailureReason("anomaly detection error: " + ex.getMessage());
        }

        return activity;
    }
}
