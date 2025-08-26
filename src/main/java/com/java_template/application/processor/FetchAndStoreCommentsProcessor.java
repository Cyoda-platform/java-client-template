package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Component
public class FetchAndStoreCommentsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndStoreCommentsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String DEFAULT_SOURCE = "https://jsonplaceholder.typicode.com/comments";

    public FetchAndStoreCommentsProcessor(SerializerFactory serializerFactory,
                                          EntityService entityService,
                                          ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CommentAnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entity();

        // Business logic:
        // 1. Fetch comments from external API using job.postId as query parameter
        // 2. For each fetched comment, create a Comment entity and persist it via EntityService.addItem
        // 3. Update the job status to ANALYZING (the job entity will be persisted by Cyoda automatically)

        String postId = job.getPostId();
        if (postId == null || postId.isBlank()) {
            logger.error("Job {} has empty postId", job.getId());
            job.setStatus("FAILED");
            return job;
        }

        // Encode postId to be safe in URL
        String encodedPostId = URLEncoder.encode(postId, StandardCharsets.UTF_8);
        String url = DEFAULT_SOURCE + "?postId=" + encodedPostId;
        int persistedCount = 0;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "FetchAndStoreCommentsProcessor/1.0")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                logger.error("Failed to fetch comments for postId {}. HTTP status: {}", postId, statusCode);
                job.setStatus("FAILED");
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            if (!(root instanceof ArrayNode)) {
                logger.warn("Unexpected response structure when fetching comments for postId {}: not an array", postId);
                job.setStatus("FAILED");
                return job;
            }

            ArrayNode commentsArray = (ArrayNode) root;
            for (JsonNode n : commentsArray) {
                try {
                    Comment comment = new Comment();
                    // Map fields from external JSON to our Comment entity
                    if (n.has("id") && !n.get("id").isNull()) {
                        JsonNode idNode = n.get("id");
                        if (idNode.canConvertToInt()) {
                            comment.setId(idNode.intValue());
                        } else {
                            // If id isn't an int, attempt coercion; otherwise leave null
                            try {
                                comment.setId(Integer.valueOf(idNode.asText()));
                            } catch (Exception ex) {
                                comment.setId(null);
                            }
                        }
                    } else {
                        comment.setId(null);
                    }

                    if (n.has("name") && !n.get("name").isNull()) {
                        comment.setName(n.get("name").asText());
                    }
                    if (n.has("email") && !n.get("email").isNull()) {
                        comment.setEmail(n.get("email").asText());
                    }
                    if (n.has("body") && !n.get("body").isNull()) {
                        comment.setBody(n.get("body").asText());
                    }
                    comment.setFetchedAt(Instant.now().toString());
                    comment.setSource(DEFAULT_SOURCE);
                    // Store the job.postId as the foreign key reference (serialized UUID string expected by Comment.postId)
                    comment.setPostId(postId);

                    if (!comment.isValid()) {
                        logger.warn("Skipping invalid comment fetched for postId {}: {}", postId, n.toString());
                        continue;
                    }

                    CompletableFuture<UUID> addFuture = entityService.addItem(
                        Comment.ENTITY_NAME,
                        String.valueOf(Comment.ENTITY_VERSION),
                        comment
                    );
                    // Use a bounded wait to avoid hanging the processor indefinitely
                    try {
                        addFuture.get(30, TimeUnit.SECONDS);
                        persistedCount++;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Thread interrupted while persisting comment for postId {}: {}", postId, ie.getMessage(), ie);
                        job.setStatus("FAILED");
                        return job;
                    } catch (Exception ex) {
                        logger.error("Failed to persist comment for postId {}: {}", postId, ex.getMessage(), ex);
                        // On error persisting comments, mark job as FAILED and stop processing
                        job.setStatus("FAILED");
                        return job;
                    }

                } catch (Exception ex) {
                    logger.warn("Error processing individual comment node for postId {}: {}", postId, ex.getMessage(), ex);
                    // continue with next comment
                }
            }

            logger.info("Fetched {} comments for postId {}, persisted {}", commentsArray.size(), postId, persistedCount);

            // After storing comments, move job to ANALYZING stage
            job.setStatus("ANALYZING");
            return job;

        } catch (Exception ex) {
            logger.error("Exception while fetching/storing comments for postId {}: {}", postId, ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}