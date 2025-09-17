package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HnItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ValidateHnItemProcessor - Performs comprehensive validation and data enrichment
 * 
 * This processor is triggered by the validate_item transition from created to validated.
 * It performs:
 * - Comprehensive validation of all fields
 * - Parent relationship validation
 * - Poll relationship validation
 * - Data enrichment and metadata updates
 */
@Component
public class ValidateHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateHnItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HnItem validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HnItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HnItem entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HnItem> entityWithMetadata) {
        HnItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for validating and enriching HN items
     */
    private EntityWithMetadata<HnItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HnItem> context) {

        EntityWithMetadata<HnItem> entityWithMetadata = context.entityResponse();
        HnItem entity = entityWithMetadata.entity();

        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Validating HnItem: ID={}, Type={}, State={}", entity.getId(), entity.getType(), currentState);

        // Perform comprehensive validation
        validateParentRelationships(entity);
        validatePollRelationships(entity);
        validateBusinessRules(entity);
        enrichWithMetadata(entity);

        logger.info("HnItem {} validated successfully", entity.getId());

        return entityWithMetadata;
    }

    /**
     * Validates parent-child relationships for comments
     */
    private void validateParentRelationships(HnItem entity) {
        if (entity.getParent() != null) {
            // For comments, validate that parent exists
            if ("comment".equals(entity.getType())) {
                logger.debug("Validating parent relationship for comment {}: parent={}", entity.getId(), entity.getParent());
                
                // Search for parent item
                ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.id")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(entity.getParent()));

                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(condition));

                List<EntityWithMetadata<HnItem>> parentItems = entityService.search(modelSpec, groupCondition, HnItem.class);
                
                if (parentItems.isEmpty()) {
                    logger.warn("Parent item {} not found for comment {}", entity.getParent(), entity.getId());
                    // Note: We don't throw exception as parent might be created later
                }
            }
        }
    }

    /**
     * Validates poll-option relationships
     */
    private void validatePollRelationships(HnItem entity) {
        // For poll options, validate that poll exists
        if ("pollopt".equals(entity.getType()) && entity.getPoll() != null) {
            logger.debug("Validating poll relationship for pollopt {}: poll={}", entity.getId(), entity.getPoll());
            
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getPoll()));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<HnItem>> pollItems = entityService.search(modelSpec, groupCondition, HnItem.class);
            
            if (pollItems.isEmpty()) {
                logger.warn("Poll item {} not found for poll option {}", entity.getPoll(), entity.getId());
            } else {
                HnItem pollItem = pollItems.get(0).entity();
                if (!"poll".equals(pollItem.getType())) {
                    logger.warn("Referenced item {} is not a poll for poll option {}", entity.getPoll(), entity.getId());
                }
            }
        }
    }

    /**
     * Validates business rules specific to different item types
     */
    private void validateBusinessRules(HnItem entity) {
        String type = entity.getType();
        
        switch (type) {
            case "story":
                validateStoryRules(entity);
                break;
            case "comment":
                validateCommentRules(entity);
                break;
            case "job":
                validateJobRules(entity);
                break;
            case "poll":
                validatePollRules(entity);
                break;
            case "pollopt":
                validatePollOptRules(entity);
                break;
        }
    }

    private void validateStoryRules(HnItem entity) {
        // Stories should have title or url
        if ((entity.getTitle() == null || entity.getTitle().trim().isEmpty()) && 
            (entity.getUrl() == null || entity.getUrl().trim().isEmpty())) {
            logger.warn("Story {} has neither title nor URL", entity.getId());
        }
    }

    private void validateCommentRules(HnItem entity) {
        // Comments should have text and parent
        if (entity.getText() == null || entity.getText().trim().isEmpty()) {
            logger.warn("Comment {} has no text", entity.getId());
        }
        if (entity.getParent() == null) {
            logger.warn("Comment {} has no parent", entity.getId());
        }
    }

    private void validateJobRules(HnItem entity) {
        // Jobs should have title
        if (entity.getTitle() == null || entity.getTitle().trim().isEmpty()) {
            logger.warn("Job {} has no title", entity.getId());
        }
    }

    private void validatePollRules(HnItem entity) {
        // Polls should have title and parts
        if (entity.getTitle() == null || entity.getTitle().trim().isEmpty()) {
            logger.warn("Poll {} has no title", entity.getId());
        }
        if (entity.getParts() == null || entity.getParts().isEmpty()) {
            logger.warn("Poll {} has no parts", entity.getId());
        }
    }

    private void validatePollOptRules(HnItem entity) {
        // Poll options should have text and poll reference
        if (entity.getText() == null || entity.getText().trim().isEmpty()) {
            logger.warn("Poll option {} has no text", entity.getId());
        }
        if (entity.getPoll() == null) {
            logger.warn("Poll option {} has no poll reference", entity.getId());
        }
    }

    /**
     * Enriches the entity with additional metadata
     */
    private void enrichWithMetadata(HnItem entity) {
        // Add any additional metadata or computed fields
        logger.debug("Enriching HnItem {} with metadata", entity.getId());
        
        // Example: Could add computed fields, normalize data, etc.
        // For now, just log the enrichment
    }
}
