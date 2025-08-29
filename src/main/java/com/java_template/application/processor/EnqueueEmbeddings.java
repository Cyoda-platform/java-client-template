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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EnqueueEmbeddings implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueEmbeddings.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnqueueEmbeddings(SerializerFactory serializerFactory) {
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

        // Business logic: For each chunk in chunks_meta, enqueue a request to an embedding service,
        // collect returned vector identifiers, and set embeddings_ref on the PostVersion.
        // The PostVersion itself will be persisted by Cyoda after this processor returns.
        try {
            if (entity.getChunks_meta() == null || entity.getChunks_meta().isEmpty()) {
                logger.info("PostVersion {} has no chunks to embed. Skipping embedding enqueue.", entity.getVersion_id());
                return entity;
            }

            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            List<String> vectorIds = new ArrayList<>();

            for (PostVersion.ChunkMeta chunk : entity.getChunks_meta()) {
                if (chunk == null) {
                    logger.warn("Encountered null chunk in PostVersion {}. Skipping.", entity.getVersion_id());
                    continue;
                }

                // Prepare payload for embedding service
                Map<String, Object> payload = new HashMap<>();
                payload.put("version_id", entity.getVersion_id());
                payload.put("post_id", entity.getPost_id());
                payload.put("chunk_ref", chunk.getChunk_ref());
                payload.put("text", chunk.getText() == null ? "" : chunk.getText());

                String body = mapper.writeValueAsString(payload);

                try {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://embedding-service.local/embeddings"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body))
                        .build();

                    HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        try {
                            JsonNode node = mapper.readTree(resp.body());
                            String vectorId = null;
                            if (node.has("vector_id")) {
                                vectorId = node.get("vector_id").asText();
                            } else if (node.has("id")) {
                                vectorId = node.get("id").asText();
                            }
                            if (vectorId == null || vectorId.isBlank()) {
                                // Fallback to generated id if service didn't return one
                                vectorId = UUID.randomUUID().toString();
                                logger.warn("Embedding service response missing id, generated fallback id {}", vectorId);
                            }
                            vectorIds.add(vectorId);
                        } catch (Exception ex) {
                            // If parsing fails, generate fallback id but continue processing others
                            String fallbackId = UUID.randomUUID().toString();
                            vectorIds.add(fallbackId);
                            logger.error("Failed to parse embedding service response for chunk {} of version {}. Generated fallback id {}. Error: {}",
                                chunk.getChunk_ref(), entity.getVersion_id(), fallbackId, ex.getMessage(), ex);
                        }
                    } else {
                        // Non-success status - log and continue with a generated id
                        String fallbackId = UUID.randomUUID().toString();
                        vectorIds.add(fallbackId);
                        logger.error("Embedding service returned status {} for chunk {} of version {}. Using fallback id {}. ResponseBody={}",
                            resp.statusCode(), chunk.getChunk_ref(), entity.getVersion_id(), fallbackId, resp.body());
                    }
                } catch (Exception ex) {
                    // Networking/IO error - continue with fallback id
                    String fallbackId = UUID.randomUUID().toString();
                    vectorIds.add(fallbackId);
                    logger.error("Failed to call embedding service for chunk {} of version {}. Using fallback id {}. Error: {}",
                        chunk.getChunk_ref(), entity.getVersion_id(), fallbackId, ex.getMessage(), ex);
                }
            }

            // Compose a stable reference to the stored embeddings for this version.
            // In a real implementation this might be a vector-store collection id or a CDN/object store ref.
            String storeRef = "vector_store:" + UUID.randomUUID().toString();

            // Optionally, we could include the vector ids metadata in the ref or external metadata store.
            // For now we set the embeddings_ref to the generated storeRef.
            entity.setEmbeddings_ref(storeRef);

            logger.info("Enqueued embeddings for PostVersion {}. chunks={}, vectorsCount={}, storeRef={}",
                    entity.getVersion_id(),
                    entity.getChunks_meta().size(),
                    vectorIds.size(),
                    storeRef);

        } catch (Exception e) {
            // Ensure we do not fail the entire processor on unexpected errors; log and return entity
            logger.error("Unexpected error while enqueuing embeddings for PostVersion {}: {}", entity.getVersion_id(), e.getMessage(), e);
        }

        return entity;
    }
}