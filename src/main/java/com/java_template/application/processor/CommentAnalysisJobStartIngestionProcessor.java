package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
import com.java_template.common.dto.EntityWithMetadata;
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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CommentAnalysisJobStartIngestionProcessor
 * 
 * Starts the process of ingesting comments from JSONPlaceholder API.
 * Fetches comments for the specified post ID and creates Comment entities.
 */
@Component
public class CommentAnalysisJobStartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisJobStartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CommentAnalysisJobStartIngestionProcessor(SerializerFactory serializerFactory, 
                                                   EntityService entityService,
                                                   RestTemplate restTemplate,
                                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting comment ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysisJob.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CommentAnalysisJob")
                .map(this::processIngestion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysisJob> entityWithMetadata) {
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return job != null && job.isValid() && technicalId != null;
    }

    private EntityWithMetadata<CommentAnalysisJob> processIngestion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysisJob> context) {

        EntityWithMetadata<CommentAnalysisJob> entityWithMetadata = context.entityResponse();
        CommentAnalysisJob job = entityWithMetadata.entity();
        UUID jobId = entityWithMetadata.metadata().getId();

        logger.info("Processing ingestion for job: {} with postId: {}", jobId, job.getPostId());

        try {
            // Call JSONPlaceholder API to get comments
            String apiUrl = "https://jsonplaceholder.typicode.com/posts/" + job.getPostId() + "/comments";
            logger.info("Calling API: {}", apiUrl);
            
            String response = restTemplate.getForObject(apiUrl, String.class);
            JsonNode commentsArray = objectMapper.readTree(response);
            
            int commentCount = 0;
            
            // Process each comment from the API response
            for (JsonNode commentNode : commentsArray) {
                Comment comment = new Comment();
                comment.setCommentId(commentNode.get("id").asLong());
                comment.setPostId(commentNode.get("postId").asLong());
                comment.setName(commentNode.get("name").asText());
                comment.setEmail(commentNode.get("email").asText());
                comment.setBody(commentNode.get("body").asText());
                comment.setJobId(jobId.toString());
                comment.setIngestedAt(LocalDateTime.now());
                comment.setWordCount(countWords(comment.getBody()));
                
                // Create Comment entity (will auto-transition to INGESTED state)
                entityService.create(comment);
                commentCount++;
                
                logger.debug("Created comment {} for job {}", comment.getCommentId(), jobId);
            }
            
            // Update job with total comments count
            job.setTotalComments(commentCount);
            logger.info("Successfully ingested {} comments for job {}", commentCount, jobId);
            
        } catch (Exception e) {
            logger.error("Error during comment ingestion for job {}: {}", jobId, e.getMessage(), e);
            job.setErrorMessage("Ingestion failed: " + e.getMessage());
        }

        return entityWithMetadata;
    }

    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
