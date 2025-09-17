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

import java.util.List;

/**
 * StoreHNItemProcessor - Persists HN item to storage
 */
@Component
public class StoreHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StoreHNItemProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Storing HNItem for request: {}", request.getId());

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
     * Main storage logic processing method
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Storing HNItem: {} of type: {}", entity.getId(), entity.getType());

        // Check if an item with the same HN ID already exists
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItem>> existingItems = entityService.search(modelSpec, condition, HNItem.class);
            
            if (!existingItems.isEmpty()) {
                // Update existing item - find the one that's not the current entity
                for (EntityWithMetadata<HNItem> existingItem : existingItems) {
                    if (!existingItem.metadata().getId().equals(entityWithMetadata.metadata().getId())) {
                        logger.info("Updating existing HNItem with ID: {} (technical ID: {})", 
                                   entity.getId(), existingItem.metadata().getId());
                        
                        // Update the existing item with new data
                        HNItem existingEntity = existingItem.entity();
                        updateExistingItem(existingEntity, entity);
                        
                        // Save the updated existing item
                        entityService.update(existingItem.metadata().getId(), existingEntity, null);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error checking for existing HNItem: {}", e.getMessage());
            // Continue with processing the current item
        }

        logger.info("HNItem {} stored successfully", entity.getId());
        return entityWithMetadata;
    }

    /**
     * Updates existing item with new data
     */
    private void updateExistingItem(HNItem existing, HNItem newItem) {
        // Update all fields from the new item
        existing.setBy(newItem.getBy());
        existing.setTime(newItem.getTime());
        existing.setTitle(newItem.getTitle());
        existing.setText(newItem.getText());
        existing.setUrl(newItem.getUrl());
        existing.setScore(newItem.getScore());
        existing.setParent(newItem.getParent());
        existing.setKids(newItem.getKids());
        existing.setDescendants(newItem.getDescendants());
        existing.setParts(newItem.getParts());
        existing.setPoll(newItem.getPoll());
        existing.setDeleted(newItem.getDeleted());
        existing.setDead(newItem.getDead());
    }
}
