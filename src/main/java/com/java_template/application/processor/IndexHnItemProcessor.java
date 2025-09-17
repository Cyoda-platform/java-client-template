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
 * IndexHnItemProcessor - Indexes items for search and builds hierarchical relationships
 * 
 * This processor is triggered by the index_item transition from validated to indexed.
 * It performs:
 * - Creating search index entries
 * - Building hierarchical relationships
 * - Updating search metadata
 * - Preparing item for search operations
 */
@Component
public class IndexHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IndexHnItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HnItem indexing for request: {}", request.getId());

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
     * Main business logic for indexing HN items
     */
    private EntityWithMetadata<HnItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HnItem> context) {

        EntityWithMetadata<HnItem> entityWithMetadata = context.entityResponse();
        HnItem entity = entityWithMetadata.entity();

        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Indexing HnItem: ID={}, Type={}, State={}", entity.getId(), entity.getType(), currentState);

        // Perform indexing operations
        createSearchIndexEntries(entity);
        buildHierarchicalRelationships(entity);
        updateSearchMetadata(entity);

        logger.info("HnItem {} indexed successfully", entity.getId());

        return entityWithMetadata;
    }

    /**
     * Creates search index entries for the item
     */
    private void createSearchIndexEntries(HnItem entity) {
        logger.debug("Creating search index entries for HnItem {}", entity.getId());
        
        // In a real implementation, this would create search index entries
        // For now, we'll log the indexing operation
        
        String indexableContent = buildIndexableContent(entity);
        logger.debug("Indexable content for item {}: {}", entity.getId(), 
                    indexableContent.length() > 100 ? indexableContent.substring(0, 100) + "..." : indexableContent);
    }

    /**
     * Builds hierarchical relationships with parent and child items
     */
    private void buildHierarchicalRelationships(HnItem entity) {
        logger.debug("Building hierarchical relationships for HnItem {}", entity.getId());
        
        // Update parent relationships
        updateParentRelationships(entity);
        
        // Update child relationships
        updateChildRelationships(entity);
        
        // Update poll relationships
        updatePollRelationships(entity);
    }

    /**
     * Updates search metadata for the item
     */
    private void updateSearchMetadata(HnItem entity) {
        logger.debug("Updating search metadata for HnItem {}", entity.getId());
        
        // In a real implementation, this would update search metadata
        // For now, we'll log the metadata update
        
        int hierarchyDepth = calculateHierarchyDepth(entity);
        logger.debug("Hierarchy depth for item {}: {}", entity.getId(), hierarchyDepth);
    }

    /**
     * Builds indexable content from the item
     */
    private String buildIndexableContent(HnItem entity) {
        StringBuilder content = new StringBuilder();
        
        if (entity.getTitle() != null) {
            content.append(entity.getTitle()).append(" ");
        }
        
        if (entity.getText() != null) {
            content.append(entity.getText()).append(" ");
        }
        
        if (entity.getBy() != null) {
            content.append("by:").append(entity.getBy()).append(" ");
        }
        
        content.append("type:").append(entity.getType()).append(" ");
        
        if (entity.getUrl() != null) {
            content.append("url:").append(entity.getUrl()).append(" ");
        }
        
        return content.toString().trim();
    }

    /**
     * Updates parent relationships for comments
     */
    private void updateParentRelationships(HnItem entity) {
        if (entity.getParent() != null) {
            logger.debug("Updating parent relationship for item {}: parent={}", entity.getId(), entity.getParent());
            
            // Find parent item and potentially update its kids list
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getParent()));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<HnItem>> parentItems = entityService.search(modelSpec, groupCondition, HnItem.class);
            
            if (!parentItems.isEmpty()) {
                EntityWithMetadata<HnItem> parentItemWithMetadata = parentItems.get(0);
                HnItem parentItem = parentItemWithMetadata.entity();
                
                // Update parent's kids list if this item is not already included
                if (parentItem.getKids() != null && !parentItem.getKids().contains(entity.getId())) {
                    parentItem.getKids().add(entity.getId());
                    
                    // Update parent item without transition (loop back to same state)
                    entityService.update(parentItemWithMetadata.metadata().getId(), parentItem, null);
                    logger.debug("Updated parent item {} kids list", parentItem.getId());
                }
            }
        }
    }

    /**
     * Updates child relationships
     */
    private void updateChildRelationships(HnItem entity) {
        if (entity.getKids() != null && !entity.getKids().isEmpty()) {
            logger.debug("Processing {} child relationships for item {}", entity.getKids().size(), entity.getId());
            
            // In a real implementation, this might verify child items exist
            // and update their parent references if needed
        }
    }

    /**
     * Updates poll-related relationships
     */
    private void updatePollRelationships(HnItem entity) {
        if ("poll".equals(entity.getType()) && entity.getParts() != null) {
            logger.debug("Processing poll parts for poll {}: {} parts", entity.getId(), entity.getParts().size());
            
            // Update poll options to reference this poll
            for (Long partId : entity.getParts()) {
                updatePollOptionReference(entity.getId(), partId);
            }
        }
    }

    /**
     * Updates a poll option to reference the correct poll
     */
    private void updatePollOptionReference(Long pollId, Long partId) {
        ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$.id")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(partId));

        GroupCondition groupCondition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));

        List<EntityWithMetadata<HnItem>> pollOptItems = entityService.search(modelSpec, groupCondition, HnItem.class);
        
        if (!pollOptItems.isEmpty()) {
            EntityWithMetadata<HnItem> pollOptWithMetadata = pollOptItems.get(0);
            HnItem pollOpt = pollOptWithMetadata.entity();
            
            if ("pollopt".equals(pollOpt.getType()) && !pollId.equals(pollOpt.getPoll())) {
                pollOpt.setPoll(pollId);
                
                // Update poll option without transition (loop back to same state)
                entityService.update(pollOptWithMetadata.metadata().getId(), pollOpt, null);
                logger.debug("Updated poll option {} to reference poll {}", partId, pollId);
            }
        }
    }

    /**
     * Calculates the hierarchy depth of an item
     */
    private int calculateHierarchyDepth(HnItem entity) {
        int depth = 0;
        Long currentParent = entity.getParent();
        
        // Prevent infinite loops with a maximum depth
        int maxDepth = 100;
        
        while (currentParent != null && depth < maxDepth) {
            depth++;
            
            // Find parent item
            ModelSpec modelSpec = new ModelSpec().withName(HnItem.ENTITY_NAME).withVersion(HnItem.ENTITY_VERSION);
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(currentParent));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<HnItem>> parentItems = entityService.search(modelSpec, groupCondition, HnItem.class);
            
            if (parentItems.isEmpty()) {
                break;
            }
            
            currentParent = parentItems.get(0).entity().getParent();
        }
        
        return depth;
    }
}
