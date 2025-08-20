package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;

@Component
public class EnrichActivityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichActivityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichActivityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EnrichActivity for request: {}", request.getId());

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
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode metadata = activity.getMetadata() != null ? (ObjectNode) activity.getMetadata() : mapper.createObjectNode();
            // Example enrichment: add a synthetic geo or user-agent derived field
            if (!metadata.has("enriched")) {
                ObjectNode enriched = mapper.createObjectNode();
                enriched.put("enrichedAt", Instant.now().toString());
                enriched.put("sourceHint", activity.getSource() == null ? "unknown" : activity.getSource());
                metadata.set("enriched", enriched);
                activity.setMetadata(metadata);
            }
            logger.info("Enriched activity {}", activity.getActivityId());
        } catch (Exception ex) {
            logger.error("Error enriching activity", ex);
            activity.setFailureReason("enrich error: " + ex.getMessage());
        }

        return activity;
    }
}
