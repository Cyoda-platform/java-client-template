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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NormalizeAndChunk implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeAndChunk.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Chunk size chosen to balance typical embedding/token limits and storage; adjust later if needed.
    private static final int DEFAULT_CHUNK_SIZE = 1000;

    public NormalizeAndChunk(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PostVersion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PostVersion.class)
            .validate(this::isValidEntity, "Invalid PostVersion state for normalization")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Custom validation for the incoming PostVersion prior to normalization.
     * We avoid using entity.isValid() here because that requires chunksMeta to be non-null,
     * which is the responsibility of this processor to create.
     */
    private boolean isValidEntity(PostVersion entity) {
        if (entity == null) return false;
        if (entity.getVersionId() == null || entity.getVersionId().isBlank()) return false;
        if (entity.getPostId() == null || entity.getPostId().isBlank()) return false;
        if (entity.getAuthorId() == null || entity.getAuthorId().isBlank()) return false;
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) return false;
        // contentRich may be empty at this stage, that's acceptable (we'll normalize to empty string)
        return true;
    }

    private PostVersion processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PostVersion> context) {
        PostVersion entity = context.entity();

        // Business logic: normalize rich text to plaintext and chunk into manageable pieces.
        // 1) Normalize: strip simple HTML tags, replace common entities, collapse whitespace.
        String contentRich = entity.getContentRich();
        String normalized = normalizePlaintext(contentRich);
        entity.setNormalizedText(normalized);

        // 2) Chunking: split normalized text into chunks of ~DEFAULT_CHUNK_SIZE characters.
        // Prefer splitting at whitespace to avoid cutting words when possible.
        List<Map<String, Object>> chunksMeta = new ArrayList<>();
        if (normalized != null && !normalized.isBlank()) {
            int len = normalized.length();
            int index = 0;
            int chunkIndex = 0;
            while (index < len) {
                int end = Math.min(index + DEFAULT_CHUNK_SIZE, len);
                // If we are not at the end, try to backtrack to the last whitespace to avoid splitting words
                if (end < len) {
                    int lastSpace = normalized.lastIndexOf(' ', end);
                    if (lastSpace > index) {
                        end = lastSpace;
                    }
                }
                // Fallback safety: ensure we always advance
                if (end == index) {
                    end = Math.min(index + DEFAULT_CHUNK_SIZE, len);
                }

                String chunkText = normalized.substring(index, end).trim();
                Map<String, Object> meta = new HashMap<>();
                meta.put("chunk_index", chunkIndex);
                meta.put("text", chunkText);
                meta.put("length", chunkText.length());
                // reference to parent version
                meta.put("version_id", entity.getVersionId());
                chunksMeta.add(meta);

                chunkIndex++;
                index = end;
                // Skip any leading whitespace for the next chunk
                while (index < len && Character.isWhitespace(normalized.charAt(index))) {
                    index++;
                }
            }
        }

        // Ensure chunksMeta is never null (PostVersion.isValid expects non-null)
        entity.setChunksMeta(chunksMeta);

        logger.debug("PostVersion {} normalized to {} chars and split into {} chunks",
                entity.getVersionId(),
                normalized == null ? 0 : normalized.length(),
                chunksMeta.size());

        return entity;
    }

    // Basic normalization: remove HTML tags, replace a few common entities, collapse whitespace.
    private String normalizePlaintext(String rich) {
        if (rich == null) return "";
        // Remove HTML tags (simple heuristic)
        String text = rich.replaceAll("(?s)<[^>]*>", " ");
        // Replace a few common HTML entities
        text = text.replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'");
        // Collapse whitespace
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}