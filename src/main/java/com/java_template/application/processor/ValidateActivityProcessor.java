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

import java.time.Instant;

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
        logger.info("Processing Activity for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Activity.class)
            .validate(this::isValidEntity, "Invalid activity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Activity activity) {
        // basic null checks
        return activity != null && activity.getActivityId() != null && activity.getUserId() != null && activity.getPayload() != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();
        try {
            // Basic schema presence validation
            if (activity.getActivityId() == null || activity.getUserId() == null || activity.getTimestamp() == null || activity.getPayload() == null) {
                // Mark as failed ingestion
                activity.setIngestionStatus("FAILED");
                logger.warn("Activity {} failed validation - missing required fields", activity.getActivityId());
                return activity;
            }

            // Compute dedupe key deterministically if missing
            if (activity.getDedupeKey() == null || activity.getDedupeKey().isEmpty()) {
                String dedupe = activity.getActivityId() + "_" + activity.getUserId();
                activity.setDedupeKey(dedupe);
            }

            // mark as validated and set normalized timestamp placeholder
            activity.setIngestionStatus("VALIDATED");
            activity.setNormalizedAt(Instant.now().toString());

        } catch (Exception ex) {
            logger.error("Error validating activity {}: {}", activity == null ? "<null>" : activity.getActivityId(), ex.getMessage(), ex);
            if (activity != null) {
                activity.setIngestionStatus("FAILED");
            }
        }
        return activity;
    }
}
