package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class CommentAnalysisRequestStartFetchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestStartFetchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CommentAnalysisRequestStartFetchingProcessor(SerializerFactory serializerFactory, 
                                                       EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisRequest start fetching for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysisRequest.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisRequest entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();

        try {
            // Call JSONPlaceholder API: GET /posts/{postId}/comments
            String apiUrl = "https://jsonplaceholder.typicode.com/posts/" + entity.getPostId() + "/comments";
            logger.info("Fetching comments from API: {}", apiUrl);
            
            String response = restTemplate.getForObject(apiUrl, String.class);
            JsonNode commentsArray = objectMapper.readTree(response);

            int commentCount = 0;
            
            // For each comment in API response
            if (commentsArray.isArray()) {
                for (JsonNode commentNode : commentsArray) {
                    // Create Comment entity
                    Comment comment = new Comment();
                    comment.setCommentId(commentNode.get("id").asLong());
                    comment.setPostId(commentNode.get("postId").asLong());
                    comment.setName(commentNode.get("name").asText());
                    comment.setEmail(commentNode.get("email").asText());
                    comment.setBody(commentNode.get("body").asText());
                    comment.setRequestId(entity.getRequestId());
                    comment.setFetchedAt(LocalDateTime.now());

                    // Save Comment entity with transition "fetch_comment"
                    entityService.save(comment);
                    commentCount++;
                }
            }

            logger.info("Successfully fetched {} comments for requestId: {}", commentCount, entity.getRequestId());
            
        } catch (Exception e) {
            logger.error("Failed to fetch comments from API for requestId: {}", entity.getRequestId(), e);
            throw new RuntimeException("Failed to fetch comments from API: " + e.getMessage(), e);
        }

        return entity;
    }
}
