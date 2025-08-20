package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Component
public class PersistActivitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistActivitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistActivitiesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistActivities for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.getSummary() != null && entity.getSummary().isArray();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        try {
            ArrayNode activities = (ArrayNode) job.getSummary();
            Set<String> seenActivityIds = new HashSet<>();
            for (Iterator<JsonNode> it = activities.elements(); it.hasNext(); ) {
                JsonNode n = it.next();
                String activityId = n.has("activityId") && !n.get("activityId").isNull() ? n.get("activityId").asText() : null;
                // dedupe by activityId or by full JSON string
                String dedupeKey = activityId != null ? activityId : n.toString();
                if (seenActivityIds.contains(dedupeKey)) {
                    continue;
                }
                seenActivityIds.add(dedupeKey);

                // create Activity entity object to persist
                Activity a = new Activity();
                if (activityId != null) a.setActivityId(activityId);
                if (n.has("userId")) a.setUserId(n.get("userId").asText());
                if (n.has("timestamp")) a.setTimestamp(n.get("timestamp").asText());
                if (n.has("type")) a.setType(n.get("type").asText());
                if (n.has("source")) a.setSource(n.get("source").asText());
                a.setPersistedAt(Instant.now().toString());
                a.setProcessed(false);
                a.setValid(null);

                // Attach as created items in job.summary for downstream: wrap technicalId placeholder
                ObjectNode created = a.toObjectNode();
                // Note: actual persistence is performed by Cyoda; we just prepare payload
                // Append to job.summary.created (create or reuse array)
                // For simplicity, append to existing array as object with activityId
                ObjectNode marker = created.objectNode();
                if (a.getActivityId() != null) marker.put("activityId", a.getActivityId());
                marker.put("persistedAt", a.getPersistedAt());
                activities.add(marker);
            }

            logger.info("Persisted {} new activities for job {}", seenActivityIds.size(), job.getJobId());
            job.setFailureReason(null);
        } catch (Exception ex) {
            logger.error("Error persisting activities", ex);
            job.setFailureReason("persist error: " + ex.getMessage());
        }

        return job;
    }
}
