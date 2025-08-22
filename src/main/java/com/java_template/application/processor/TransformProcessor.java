package com.java_template.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.util.Map;
import java.util.HashMap;

@Component
public class TransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public TransformProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob entity = context.entity();
        try {
            Map<String, Object> originalPayload = entity.getHnPayload();
            if (originalPayload == null) {
                logger.warn("IngestJob {} has null hnPayload, marking as FAILED", entity.getTechnicalId());
                entity.setStatus("FAILED");
                entity.setErrorMessage("hn_payload missing");
                return entity;
            }

            // Convert incoming map to a typed HNItem for normalization/enrichment
            HNItem hnItem = objectMapper.convertValue(originalPayload, HNItem.class);

            // Preserve full raw JSON payload for fidelity
            try {
                String rawJson = objectMapper.writeValueAsString(originalPayload);
                hnItem.setRawJson(rawJson);
            } catch (JsonProcessingException e) {
                // If serialization fails, log but continue; keep rawJson null
                logger.warn("Failed to serialize raw hn_payload for job {}: {}", entity.getTechnicalId(), e.getMessage());
            }

            // Minimal normalization: ensure optional string fields are not null (avoid downstream NPEs)
            if (hnItem.getTitle() == null) hnItem.setTitle("");
            if (hnItem.getBy() == null) hnItem.setBy("");
            if (hnItem.getType() == null) hnItem.setType("");
            if (hnItem.getUrl() == null) hnItem.setUrl("");
            if (hnItem.getText() == null) hnItem.setText("");

            // Convert back to a Map representation for storage in the IngestJob.hnPayload
            @SuppressWarnings("unchecked")
            Map<String, Object> normalized = objectMapper.convertValue(hnItem, Map.class);
            if (normalized == null) {
                normalized = new HashMap<>();
            }

            // Ensure raw_json exists in the normalized map
            if (!normalized.containsKey("rawJson") && hnItem.getRawJson() != null) {
                normalized.put("raw_json", hnItem.getRawJson());
            } else if (!normalized.containsKey("raw_json") && hnItem.getRawJson() != null) {
                normalized.put("raw_json", hnItem.getRawJson());
            }

            // Update the job payload with normalized representation
            entity.setHnPayload(normalized);

            // Move job forward to next state for persistence stage
            entity.setStatus("PERSISTING");

            logger.info("IngestJob {} transformed successfully, moving to PERSISTING", entity.getTechnicalId());
            return entity;
        } catch (Exception ex) {
            logger.error("Error while transforming IngestJob {}: {}", entity != null ? entity.getTechnicalId() : "unknown", ex.getMessage(), ex);
            if (entity != null) {
                entity.setStatus("FAILED");
                entity.setErrorMessage("transform error: " + ex.getMessage());
            }
            return entity;
        }
    }
}