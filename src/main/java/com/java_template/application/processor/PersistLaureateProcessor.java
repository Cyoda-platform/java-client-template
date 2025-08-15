package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public PersistLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistLaureateProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for persisting")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "PERSISTING".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        int created = 0;
        int updated = 0;

        try {
            if (job.getResultSummary() == null) {
                logger.info("Job {} nothing to persist", job.getTechnicalId());
                job.setStatus("COMPLETED");
                return job;
            }
            JsonNode rs = objectMapper.readTree(job.getResultSummary());
            JsonNode toPersist = rs.get("toPersist");
            if (toPersist != null && toPersist.isArray()) {
                // Use entityService.getItems to simulate checking existing laureates
                CompletableFuture<java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>> fut = entityService.getItems(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION));
                java.util.ArrayList<JsonNode> existingList = fut.get();
                Map<String, JsonNode> existingByNatural = new HashMap<>();
                if (existingList != null) {
                    for (JsonNode n : existingList) {
                        String key = computeNaturalKeyFromJson(n);
                        if (key != null) existingByNatural.put(key, n);
                    }
                }

                for (JsonNode p : toPersist) {
                    Laureate l = objectMapper.treeToValue(p, Laureate.class);
                    String key = computeNaturalKey(l);
                    JsonNode existing = existingByNatural.get(key);
                    if (existing == null) {
                        // create via entityService.addItem
                        CompletableFuture<java.util.UUID> idf = entityService.addItem(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), objectMapper.convertValue(l, ObjectNode.class));
                        idf.get();
                        created++;
                        logger.info("Persisted new laureate naturalKey={}", key);
                    } else {
                        int existingVersion = existing.has("version") ? existing.get("version").asInt(0) : 0;
                        if (l.getVersion() != null && l.getVersion() > existingVersion) {
                            // update other entity via entityService.updateItem if necessary
                            // NOTE: PersistLaureate should not update Job entity but may update Laureate entity using entityService
                            // find technicalId in existing and perform update
                            String techId = existing.has("laureateId") ? existing.get("laureateId").asText() : null;
                            if (techId != null) {
                                CompletableFuture<java.util.UUID> upd = entityService.updateItem(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), java.util.UUID.fromString(techId), objectMapper.convertValue(l, ObjectNode.class));
                                upd.get();
                                updated++;
                                logger.info("Updated laureate naturalKey={} to version={}", key, l.getVersion());
                            }
                        } else {
                            logger.info("Skipped persist for {} due to version check", key);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Persist step failed for job {}: {}", job.getTechnicalId(), e.getMessage());
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("created", created);
        out.put("updated", updated);
        try {
            job.setResultSummary(objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            job.setResultSummary("{}");
        }
        job.setStatus("COMPLETED");
        logger.info("Job {} persist complete created={}, updated={}", job.getTechnicalId(), created, updated);
        return job;
    }

    private String computeNaturalKey(Laureate l) {
        if (l == null) return null;
        return (l.getFullName() + "|" + l.getYear() + "|" + l.getCategory()).toLowerCase();
    }

    private String computeNaturalKeyFromJson(JsonNode n) {
        if (n == null) return null;
        try {
            String full = n.has("fullName") ? n.get("fullName").asText() : null;
            int year = n.has("year") ? n.get("year").asInt() : 0;
            String cat = n.has("category") ? n.get("category").asText() : null;
            if (full == null || cat == null) return null;
            return (full + "|" + year + "|" + cat).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
