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
import java.util.UUID;

@Component
public class IndexedForPatternProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexedForPatternProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexedForPatternProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Indexing Activity for patterns for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Activity.class)
            .validate(this::isValidEntity, "Invalid activity for indexing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Activity activity) {
        return activity != null && activity.getDedupeKey() != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();
        try {
            // Simulate indexing: set status to PROCESSED and add an index id
            activity.setIngestionStatus("PROCESSED");
            // store a synthetic index id in payload for observability
            Object payload = activity.getPayload();
            if (payload instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) payload;
                map.put("indexedId", UUID.randomUUID().toString());
                activity.setPayload(map);
            }
        } catch (Exception ex) {
            logger.error("Error indexing activity {}: {}", activity == null ? "<null>" : activity.getActivityId(), ex.getMessage(), ex);
            if (activity != null) {
                activity.setIngestionStatus("FAILED");
            }
        }
        return activity;
    }
}
