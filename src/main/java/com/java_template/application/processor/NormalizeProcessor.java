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
import java.util.Map;

@Component
public class NormalizeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Normalizing Activity for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Activity.class)
            .validate(this::isValidEntity, "Invalid activity state for normalization")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Activity activity) {
        return activity != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();
        try {
            // Normalize payload keys: lowercase string keys, trim string values when possible
            Object payloadObj = activity.getPayload();
            if (payloadObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) payloadObj;
                // naive normalization: lowercase keys
                for (String key : payload.keySet().toArray(new String[0])) {
                    Object val = payload.remove(key);
                    String normalizedKey = key == null ? null : key.trim().toLowerCase();
                    if (normalizedKey != null) {
                        payload.put(normalizedKey, val instanceof String ? ((String) val).trim() : val);
                    }
                }
                activity.setPayload(payload);
            }

            // Ensure dedupe key exists and normalized
            if (activity.getDedupeKey() == null || activity.getDedupeKey().isEmpty()) {
                String dedupe = activity.getActivityId() + "_" + activity.getUserId();
                activity.setDedupeKey(dedupe);
            }

            activity.setIngestionStatus("DEDUPE");
            activity.setNormalizedAt(Instant.now().toString());

        } catch (Exception ex) {
            logger.error("Error normalizing activity {}: {}", activity == null ? "<null>" : activity.getActivityId(), ex.getMessage(), ex);
            if (activity != null) {
                activity.setIngestionStatus("FAILED");
            }
        }
        return activity;
    }
}
