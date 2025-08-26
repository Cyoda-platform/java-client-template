package com.java_template.application.processor;

import com.java_template.application.entity.comment.version_1.Comment;
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

import java.time.Instant;

@Component
public class StoreCommentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreCommentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StoreCommentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Comment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Comment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Comment entity) {
        return entity != null && entity.isValid();
    }

    private Comment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Comment> context) {
        Comment entity = context.entity();

        try {
            // Ensure fetchedAt is present: if missing, set to now (ISO-8601)
            if (entity.getFetchedAt() == null || entity.getFetchedAt().isBlank()) {
                entity.setFetchedAt(Instant.now().toString());
            }

            // Normalize email to lowercase and trim
            if (entity.getEmail() != null) {
                String e = entity.getEmail().trim();
                if (!e.isBlank()) {
                    entity.setEmail(e.toLowerCase());
                }
            }

            // Trim name and body to remove leading/trailing whitespace
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
            if (entity.getBody() != null) {
                entity.setBody(entity.getBody().trim());
            }

            // Ensure source has a default if missing (do not invent complex values)
            if (entity.getSource() == null || entity.getSource().isBlank()) {
                entity.setSource("unknown");
            }

            // Note: Comment entity does not have a status field. Per rules, do not invent properties.
            // The entity will be persisted automatically by Cyoda after processing.
        } catch (Exception ex) {
            logger.error("Error processing Comment entity: {}", ex.getMessage(), ex);
            // Do not throw; leave entity as-is so serializer can handle completion.
        }

        return entity;
    }
}