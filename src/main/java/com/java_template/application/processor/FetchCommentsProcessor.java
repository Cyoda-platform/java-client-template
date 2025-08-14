package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.comment.version_1.Comment;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class FetchCommentsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCommentsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    @Autowired
    public FetchCommentsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getPostId() != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            logger.info("Fetching comments for postId: {}", job.getPostId());
            URL url = new URL("https://jsonplaceholder.typicode.com/comments?postId=" + job.getPostId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.error("Failed to fetch comments, HTTP response code: {}", responseCode);
                job.setStatus("FAILED");
                return job;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Deserialize JSON response into Comment list
            List<Comment> comments = objectMapper.readValue(response.toString(),
                objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, Comment.class));

            // Persist comments in database
            for (Comment comment : comments) {
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Comment.ENTITY_NAME,
                    String.valueOf(Comment.ENTITY_VERSION),
                    comment
                );
                idFuture.whenComplete((id, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to persist comment: postId={}, commentId={}", comment.getPostId(), comment.getCommentId(), ex);
                    } else {
                        logger.info("Persisted comment: postId={}, commentId={}, id={}", comment.getPostId(), comment.getCommentId(), id);
                    }
                });
            }

            // Update job status to IN_PROGRESS to indicate fetch success
            job.setStatus("IN_PROGRESS");
        } catch (Exception e) {
            logger.error("Exception during fetching comments", e);
            job.setStatus("FAILED");
        }
        return job;
    }
}
