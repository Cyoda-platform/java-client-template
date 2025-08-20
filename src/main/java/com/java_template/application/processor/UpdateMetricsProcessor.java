package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UpdateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // in-memory metrics store for demo purposes. In production use durable store.
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public UpdateMetricsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UpdateMetrics for request: {}", request.getId());

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
        return entity != null && entity.getType() != null;
    }

    private Activity processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Activity> context) {
        Activity activity = context.entity();

        try {
            String key = "type:" + activity.getType();
            counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
            // track per-user if present
            if (activity.getUserId() != null) {
                String userKey = "user:" + activity.getUserId();
                counters.computeIfAbsent(userKey, k -> new AtomicLong()).incrementAndGet();
            }
            logger.info("Updated metrics for activity {} type {}", activity.getActivityId(), activity.getType());
        } catch (Exception ex) {
            logger.error("Error updating metrics", ex);
            activity.setFailureReason("metrics error: " + ex.getMessage());
        }

        return activity;
    }
}
