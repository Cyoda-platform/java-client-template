package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.hnitemsearch.version_1.HNItemSearch;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ExecuteSearchProcessor - Executes search query against HNItem entities
 */
@Component
public class ExecuteSearchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteSearchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExecuteSearchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Executing search for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItemSearch.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItemSearch entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItemSearch> entityWithMetadata) {
        HNItemSearch entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main search execution logic processing method
     */
    private EntityWithMetadata<HNItemSearch> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItemSearch> context) {

        EntityWithMetadata<HNItemSearch> entityWithMetadata = context.entityResponse();
        HNItemSearch searchEntity = entityWithMetadata.entity();

        logger.debug("Executing search: {} of type: {}", searchEntity.getSearchId(), searchEntity.getSearchType());

        long startTime = System.currentTimeMillis();
        List<EntityWithMetadata<HNItem>> results = new ArrayList<>();

        try {
            // Execute search based on search type
            switch (searchEntity.getSearchType().toLowerCase()) {
                case "text":
                    results = searchByText(searchEntity);
                    break;
                case "author":
                    results = searchByAuthor(searchEntity);
                    break;
                case "type":
                    results = searchByType(searchEntity);
                    break;
                case "hierarchical":
                    results = searchWithParentHierarchy(searchEntity);
                    break;
                default:
                    logger.warn("Unknown search type: {}", searchEntity.getSearchType());
                    results = new ArrayList<>();
            }

            // Include parent hierarchy if requested
            if (Boolean.TRUE.equals(searchEntity.getIncludeParents())) {
                results = enrichWithParentHierarchy(results);
            }

            // Apply max results limit
            if (searchEntity.getMaxResults() != null && searchEntity.getMaxResults() > 0) {
                results = results.subList(0, Math.min(results.size(), searchEntity.getMaxResults()));
            }

            // Set search results metadata
            searchEntity.setResultCount(results.size());
            searchEntity.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            logger.info("Search {} completed with {} results in {}ms", 
                       searchEntity.getSearchId(), results.size(), searchEntity.getExecutionTimeMs());

        } catch (Exception e) {
            logger.error("Error executing search {}: {}", searchEntity.getSearchId(), e.getMessage());
            searchEntity.setResultCount(0);
            searchEntity.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            throw new RuntimeException("Search execution failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Search HNItems by text content
     */
    private List<EntityWithMetadata<HNItem>> searchByText(HNItemSearch searchEntity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            List<SimpleCondition> conditions = new ArrayList<>();

            // Search in title
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.title")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(searchEntity.getQuery())));

            // Search in text content
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.text")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(searchEntity.getQuery())));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.OR)
                    .withConditions(conditions);

            return entityService.search(modelSpec, condition, HNItem.class);
        } catch (Exception e) {
            logger.error("Error in text search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Search HNItems by author
     */
    private List<EntityWithMetadata<HNItem>> searchByAuthor(HNItemSearch searchEntity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.by")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(searchEntity.getQuery()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            return entityService.search(modelSpec, condition, HNItem.class);
        } catch (Exception e) {
            logger.error("Error in author search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Search HNItems by type
     */
    private List<EntityWithMetadata<HNItem>> searchByType(HNItemSearch searchEntity) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.type")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(searchEntity.getQuery()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            return entityService.search(modelSpec, condition, HNItem.class);
        } catch (Exception e) {
            logger.error("Error in type search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Search with parent hierarchy support
     */
    private List<EntityWithMetadata<HNItem>> searchWithParentHierarchy(HNItemSearch searchEntity) {
        // For hierarchical search, first do a text search and then include parent chain
        List<EntityWithMetadata<HNItem>> results = searchByText(searchEntity);
        return enrichWithParentHierarchy(results);
    }

    /**
     * Enrich results with parent hierarchy
     */
    private List<EntityWithMetadata<HNItem>> enrichWithParentHierarchy(List<EntityWithMetadata<HNItem>> results) {
        List<EntityWithMetadata<HNItem>> enrichedResults = new ArrayList<>(results);
        
        for (EntityWithMetadata<HNItem> itemWithMetadata : results) {
            HNItem item = itemWithMetadata.entity();
            if (item.getParent() != null) {
                // Find and add parent items
                List<EntityWithMetadata<HNItem>> parents = findParentChain(item.getParent());
                enrichedResults.addAll(parents);
            }
        }
        
        return enrichedResults;
    }

    /**
     * Find parent chain for an item
     */
    private List<EntityWithMetadata<HNItem>> findParentChain(Integer parentId) {
        List<EntityWithMetadata<HNItem>> parentChain = new ArrayList<>();
        
        try {
            ModelSpec modelSpec = new ModelSpec().withName(HNItem.ENTITY_NAME).withVersion(HNItem.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.id")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(parentId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<HNItem>> parents = entityService.search(modelSpec, condition, HNItem.class);
            
            for (EntityWithMetadata<HNItem> parentWithMetadata : parents) {
                parentChain.add(parentWithMetadata);
                
                // Recursively find grandparents
                HNItem parent = parentWithMetadata.entity();
                if (parent.getParent() != null) {
                    parentChain.addAll(findParentChain(parent.getParent()));
                }
            }
        } catch (Exception e) {
            logger.warn("Error finding parent chain for ID {}: {}", parentId, e.getMessage());
        }
        
        return parentChain;
    }
}
