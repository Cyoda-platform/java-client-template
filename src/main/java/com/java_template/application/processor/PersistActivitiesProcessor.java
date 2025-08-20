package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.activity.version_1.Activity;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PersistActivitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistActivitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistActivitiesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        return entity != null && entity.getSummary() != null && entity.getSummary().isObject();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        try {
            job.setStatus("PERSISTING");
            ObjectNode summary = (ObjectNode) job.getSummary();
            ArrayNode items = summary.has("items") && summary.get("items").isArray() ? (ArrayNode) summary.get("items") : objectMapper.createArrayNode();

            Set<String> seenActivityIds = new HashSet<>();
            int persisted = 0;
            int duplicates = 0;

            for (Iterator<JsonNode> it = items.elements(); it.hasNext(); ) {
                JsonNode n = it.next();
                String activityId = n.has("activityId") && !n.get("activityId").isNull() ? n.get("activityId").asText() : null;
                String dedupeKey = activityId != null ? activityId : n.toString();
                if (seenActivityIds.contains(dedupeKey)) {
                    duplicates++;
                    continue;
                }
                seenActivityIds.add(dedupeKey);

                // check existing via entityService by searching for activityId
                boolean exists = false;
                if (activityId != null) {
                    try {
                        SearchConditionRequest condition = SearchConditionRequest.group("AND", Condition.of("$.activityId", "EQUALS", activityId));
                        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION), condition, true);
                        com.fasterxml.jackson.databind.node.ArrayNode existing = itemsFuture.get();
                        if (existing != null && existing.size() > 0) exists = true;
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.warn("Error checking existing activity for dedupe: {}", ex.getMessage());
                    }
                }

                if (exists) {
                    duplicates++;
                    continue;
                }

                Activity a = new Activity();
                if (activityId != null) a.setActivityId(activityId);
                if (n.has("userId")) a.setUserId(n.get("userId").asText());
                if (n.has("timestamp")) a.setTimestamp(n.get("timestamp").asText());
                if (n.has("type")) a.setType(n.get("type").asText());
                if (n.has("source")) a.setSource(n.get("source").asText());
                a.setPersistedAt(Instant.now().toString());
                a.setProcessed(false);
                a.setValid(null);

                // Persist via entityService.addItem (idempotent by business id if supplied)
                try {
                    CompletableFuture<java.util.UUID> addF = entityService.addItem(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION), a);
                    java.util.UUID techId = addF.get();
                    ObjectNode marker = objectMapper.createObjectNode();
                    marker.put("activityId", a.getActivityId() == null ? "" : a.getActivityId());
                    marker.put("technicalId", techId.toString());
                    marker.put("persistedAt", a.getPersistedAt());
                    // append to summary.created array
                    if (!summary.has("created")) summary.set("created", objectMapper.createArrayNode());
                    ((ArrayNode) summary.get("created")).add(marker);
                    persisted++;
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Error persisting activity via EntityService", ex);
                }
            }

            summary.put("activitiesPersisted", persisted);
            summary.put("duplicates", duplicates);
            job.setSummary(summary);
            job.setStatus(persisted > 0 ? "ANALYSIS_TRIGGERED" : "COMPLETED");
            job.setFailureReason(null);
            logger.info("Persisted {} activities (duplicates={}) for job {}", persisted, duplicates, job.getJobId());

        } catch (Exception ex) {
            logger.error("Error persisting activities", ex);
            job.setFailureReason("persist error: " + ex.getMessage());
            job.setStatus("FAILED");
        }

        return job;
    }
}
