package com.java_template.application.processor;

import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class NormalizeAndChunk implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeAndChunk.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeAndChunk(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PostVersion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PostVersion.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Business logic: normalize rich content to plaintext and chunk into manageable pieces
        // Use only getters/setters available on the PostVersion entity.

        String rich = entity.getContent_rich();
        String normalized;

        if (rich == null || rich.isBlank()) {
            normalized = "";
            entity.setNormalized_text(normalized);
            entity.setChunks_meta(new ArrayList<>());
            logger.info("PostVersion {} contains no rich content; normalized_text set to empty and no chunks created.", entity.getVersion_id());
            return entity;
        }

        // Naive HTML tag removal and whitespace normalization.
        // Remove tags
        normalized = rich.replaceAll("(?s)<[^>]*>", " ");
        // Replace common HTML entity for non-breaking space
        normalized = normalized.replace("&nbsp;", " ");
        // Collapse whitespace
        normalized = normalized.replaceAll("\\s+", " ").trim();

        entity.setNormalized_text(normalized);

        // Chunking logic: split by words, accumulate up to maxChunkSize without breaking words.
        final int maxChunkSize = 1000;
        List<PostVersion.ChunkMeta> chunks = new ArrayList<>();
        if (normalized.isBlank()) {
            // No textual content after normalization
            entity.setChunks_meta(chunks);
            logger.info("PostVersion {} normalization resulted in empty text; no chunks created.", entity.getVersion_id());
            return entity;
        }

        String[] words = normalized.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() == 0) {
                // start new chunk
                if (w.length() <= maxChunkSize) {
                    current.append(w);
                } else {
                    // Word itself exceeds max size: split the word forcibly
                    int start = 0;
                    while (start < w.length()) {
                        int end = Math.min(start + maxChunkSize, w.length());
                        String part = w.substring(start, end);
                        PostVersion.ChunkMeta cm = new PostVersion.ChunkMeta();
                        cm.setChunk_ref(UUID.randomUUID().toString());
                        cm.setText(part);
                        chunks.add(cm);
                        start = end;
                    }
                }
            } else {
                // consider adding with a space
                if (current.length() + 1 + w.length() <= maxChunkSize) {
                    current.append(' ').append(w);
                } else {
                    // flush current chunk
                    PostVersion.ChunkMeta cm = new PostVersion.ChunkMeta();
                    cm.setChunk_ref(UUID.randomUUID().toString());
                    cm.setText(current.toString());
                    chunks.add(cm);
                    // start new chunk with word (or split if word too long)
                    current = new StringBuilder();
                    if (w.length() <= maxChunkSize) {
                        current.append(w);
                    } else {
                        int start = 0;
                        while (start < w.length()) {
                            int end = Math.min(start + maxChunkSize, w.length());
                            String part = w.substring(start, end);
                            PostVersion.ChunkMeta cm2 = new PostVersion.ChunkMeta();
                            cm2.setChunk_ref(UUID.randomUUID().toString());
                            cm2.setText(part);
                            chunks.add(cm2);
                            start = end;
                        }
                    }
                }
            }
        }

        // Add remaining current chunk
        if (current.length() > 0) {
            PostVersion.ChunkMeta cm = new PostVersion.ChunkMeta();
            cm.setChunk_ref(UUID.randomUUID().toString());
            cm.setText(current.toString());
            chunks.add(cm);
        }

        // Ensure at least one chunk exists if there is text
        if (chunks.isEmpty() && !normalized.isBlank()) {
            PostVersion.ChunkMeta cm = new PostVersion.ChunkMeta();
            cm.setChunk_ref(UUID.randomUUID().toString());
            cm.setText(normalized);
            chunks.add(cm);
        }

        entity.setChunks_meta(chunks);

        logger.info("PostVersion {} normalized and split into {} chunk(s).", entity.getVersion_id(), chunks.size());

        return entity;
    }
}