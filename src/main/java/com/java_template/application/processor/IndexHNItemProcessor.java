package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.util.ArrayList;
import java.util.List;

/**
 * IndexHNItemProcessor - Indexes item for search functionality
 */
@Component
public class IndexHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IndexHNItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Indexing HNItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItem entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main indexing logic processing method
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Indexing HNItem: {} of type: {}", entity.getId(), entity.getType());

        // Update parent's children index if this item has a parent
        if (entity.getParent() != null) {
            updateParentChildrenIndex(entity);
        }

        // For poll items, update related pollopt items
        if ("poll".equals(entity.getType()) && entity.getParts() != null && !entity.getParts().isEmpty()) {
            updatePolloptItems(entity);
        }

        // For pollopt items, ensure they reference the correct poll
        if ("pollopt".equals(entity.getType()) && entity.getPoll() != null) {
            updatePollReference(entity);
        }

        logger.info("HNItem {} indexed successfully", entity.getId());
        return entityWithMetadata;
    }

    /**
     * Updates parent item's children list to include this item
     */
    private void updateParentChildrenIndex(HNItem childItem) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(childItem.getParent()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItem>> parentItems = entityService.search(modelSpec, condition, HNItem.class);
            
            for (EntityWithMetadata<HNItem> parentItemWithMetadata : parentItems) {
                HNItem parentItem = parentItemWithMetadata.entity();
                
                // Initialize kids list if null
                if (parentItem.getKids() == null) {
                    parentItem.setKids(new ArrayList<>());
                }
                
                // Add child ID if not already present
                if (!parentItem.getKids().contains(childItem.getId())) {
                    parentItem.getKids().add(childItem.getId());
                    
                    // Update descendants count
                    if (parentItem.getDescendants() == null) {
                        parentItem.setDescendants(1);
                    } else {
                        parentItem.setDescendants(parentItem.getDescendants() + 1);
                    }
                    
                    // Save the updated parent
                    entityService.update(parentItemWithMetadata.metadata().getId(), parentItem, null);
                    logger.debug("Updated parent item {} with child {}", parentItem.getId(), childItem.getId());
                }
            }
        } catch (Exception e) {
            logger.warn("Error updating parent children index for item {}: {}", childItem.getId(), e.getMessage());
        }
    }

    /**
     * Updates pollopt items to reference this poll
     */
    private void updatePolloptItems(HNItem pollItem) {
        try {
            for (Integer polloptId : pollItem.getParts()) {
                ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
                
                SimpleCondition simpleCondition = new SimpleCondition()
                        .withJsonPath("$.id")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(polloptId));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(simpleCondition));

                List<EntityWithMetadata<HNItem>> polloptItems = entityService.search(modelSpec, condition, HNItem.class);
                
                for (EntityWithMetadata<HNItem> polloptItemWithMetadata : polloptItems) {
                    HNItem polloptItem = polloptItemWithMetadata.entity();
                    
                    // Set poll reference if not already set
                    if (polloptItem.getPoll() == null || !polloptItem.getPoll().equals(pollItem.getId())) {
                        polloptItem.setPoll(pollItem.getId());
                        entityService.update(polloptItemWithMetadata.metadata().getId(), polloptItem, null);
                        logger.debug("Updated pollopt item {} with poll reference {}", polloptItem.getId(), pollItem.getId());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error updating pollopt items for poll {}: {}", pollItem.getId(), e.getMessage());
        }
    }

    /**
     * Ensures poll reference is correctly set for pollopt items
     */
    private void updatePollReference(HNItem polloptItem) {
        // This is mainly for validation - the poll reference should already be set
        logger.debug("Pollopt item {} references poll {}", polloptItem.getId(), polloptItem.getPoll());
    }
}
