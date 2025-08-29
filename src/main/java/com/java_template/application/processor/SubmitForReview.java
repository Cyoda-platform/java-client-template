package com.java_template.application.processor;

import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.audit.version_1.Audit;
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
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class SubmitForReview implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitForReview.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SubmitForReview(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Post for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Post.class)
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

    private boolean isValidEntity(Post entity) {
        return entity != null && entity.isValid();
    }

    private Post processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Post> context) {
        Post entity = context.entity();

        // Business validations specific to submit_for_review
        if (entity.getTitle() == null || entity.getTitle().isBlank()) {
            logger.error("Post {} missing title - cannot submit for review", entity.getId());
            throw new RuntimeException("Post missing title");
        }
        if (entity.getSlug() == null || entity.getSlug().isBlank()) {
            logger.error("Post {} missing slug - cannot submit for review", entity.getId());
            throw new RuntimeException("Post missing slug");
        }
        if (entity.getCurrent_version_id() == null || entity.getCurrent_version_id().isBlank()) {
            logger.error("Post {} missing current_version_id - cannot submit for review", entity.getId());
            throw new RuntimeException("Post missing current_version_id");
        }

        // Normalize tags: trim, lowercase, remove blanks and duplicates
        List<String> tags = entity.getTags();
        if (tags != null) {
            List<String> normalized = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
            entity.setTags(normalized);
        }

        // Append audit record for the transition
        try {
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("submit_for_review");
            audit.setActorId(entity.getAuthor_id() != null && !entity.getAuthor_id().isBlank() ? entity.getAuthor_id() : "system");
            audit.setEntityRef(entity.getId() + ":Post");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("previous_status", entity.getStatus());
            metadata.put("current_version_id", entity.getCurrent_version_id());
            audit.setMetadata(metadata);
            audit.setTimestamp(Instant.now().toString());

            // Persist audit entry (allowed: operate on other entities)
            entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, audit).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while appending audit for Post {}: {}", entity.getId(), ie.getMessage(), ie);
            throw new RuntimeException("Interrupted while appending audit");
        } catch (ExecutionException ee) {
            logger.error("Failed to append audit for Post {}: {}", entity.getId(), ee.getMessage(), ee);
            throw new RuntimeException("Failed to append audit: " + ee.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while appending audit for Post {}: {}", entity.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Unexpected error while appending audit: " + ex.getMessage());
        }

        // Transition post status to in_review
        entity.setStatus("in_review");

        // Do not call entityService.updateItem for the triggering entity - Cyoda will persist the changed entity automatically.
        logger.info("Post {} submitted for review by {} - status set to in_review", entity.getId(), entity.getAuthor_id());

        return entity;
    }
}