package com.java_template.application.processor;

import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Component
public class FinalizeVersion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeVersion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeVersion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PostVersion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PostVersion.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PostVersion entity) {
        return entity != null && entity.isValid();
    }

    private PostVersion processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PostVersion> context) {
        PostVersion entity = context.entity();

        // FinalizeVersion processor:
        // - Ensure normalizedText exists by deriving from contentRich if missing
        // - Chunk normalizedText into chunksMeta if chunksMeta is empty or missing
        // - Do not perform any external persistence here; Cyoda will persist the entity automatically

        String content = entity.getContentRich();
        String normalized = entity.getNormalizedText();

        if ((normalized == null || normalized.isBlank()) && content != null && !content.isBlank()) {
            // Very simple HTML strip and whitespace normalization
            normalized = content.replaceAll("<[^>]*>", ""); // remove tags
            normalized = normalized.replaceAll("\\s+", " ").trim(); // collapse whitespace
            entity.setNormalizedText(normalized);
            logger.debug("PostVersion {}: normalized text computed (length={})", entity.getVersionId(), normalized.length());
        }

        // Ensure chunksMeta is present. If already present and non-empty, we leave it as-is.
        List<Map<String, Object>> chunks = entity.getChunksMeta();
        if (chunks == null || chunks.isEmpty()) {
            List<Map<String, Object>> computedChunks = new ArrayList<>();
            if (normalized != null && !normalized.isBlank()) {
                final int CHUNK_SIZE = 1000; // reasonable default chunk size for downstream embedding
                int len = normalized.length();
                int index = 0;
                for (int start = 0; start < len; start += CHUNK_SIZE) {
                    int end = Math.min(start + CHUNK_SIZE, len);
                    String chunkText = normalized.substring(start, end);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("index", index);
                    meta.put("start", start);
                    meta.put("end", end);
                    meta.put("text", chunkText);
                    computedChunks.add(meta);
                    index++;
                }
            }
            // If normalized is empty, computedChunks will be empty (explicitly set)
            entity.setChunksMeta(computedChunks);
            logger.debug("PostVersion {}: chunks_meta computed (count={})", entity.getVersionId(), computedChunks.size());
        }

        // Note: embeddingsRef should be handled by enqueue_embeddings processor (async job).
        // FinalizeVersion's responsibility is to prepare the version (normalizedText + chunksMeta).
        return entity;
    }
}