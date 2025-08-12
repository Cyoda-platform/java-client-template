package com.java_template.prototype.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.prototype.workflow.WorkflowOrchestrator;
import com.java_template.prototype.workflow.WorkflowOrchestratorFactory;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import io.cloudevents.v1.proto.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of EntityService for prototype/testing purposes.
 * Stores entities in a local ConcurrentHashMap and triggers workflow orchestrators
 * when entities are saved or updated.
 */
@Service
@Profile("prototype")
public class InMemoryEntityService implements EntityService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryEntityService.class);

    private final ObjectMapper objectMapper;
    private final WorkflowOrchestratorFactory orchestratorFactory;

    // Storage: entityModel -> entityVersion -> technicalId -> entity data
    private final Map<String, Map<String, Map<UUID, ObjectNode>>> storage = new ConcurrentHashMap<>();

    public InMemoryEntityService(@Lazy WorkflowOrchestratorFactory orchestratorFactory) {
        this.orchestratorFactory = orchestratorFactory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        logger.info("Initialized InMemoryEntityService with workflow orchestrator factory");
    }

    @Override
    public CompletableFuture<ObjectNode> getItem(String entityModel, String entityVersion, UUID technicalId) {
        logger.debug("Getting item: model={}, version={}, id={}", entityModel, entityVersion, technicalId);

        ObjectNode entity = getEntityFromStorage(entityModel, entityVersion, technicalId);
        if (entity == null) {
            return CompletableFuture.failedFuture(
                    new NoSuchElementException("Entity not found: " + technicalId)
            );
        }

        // Add technicalId to the response
        ObjectNode result = entity.deepCopy();
        result.put("technicalId", technicalId.toString());

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<ObjectNode> getItemWithMetaFields(String entityModel, String entityVersion, UUID technicalId) {
        // For prototype, we'll return the same as getItem but with additional meta fields
        return getItem(entityModel, entityVersion, technicalId)
                .thenApply(entity -> {
                    ObjectNode result = JsonNodeFactory.instance.objectNode();
                    result.set("data", entity);

                    ObjectNode meta = JsonNodeFactory.instance.objectNode();
                    meta.put("id", technicalId.toString());
                    meta.put("entityModel", entityModel);
                    meta.put("entityVersion", entityVersion);
                    result.set("meta", meta);

                    return result;
                });
    }

    @Override
    public CompletableFuture<ArrayNode> getItems(String entityModel, String entityVersion) {
        logger.debug("Getting all items: model={}, version={}", entityModel, entityVersion);

        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        Map<UUID, ObjectNode> entities = getEntitiesFromStorage(entityModel, entityVersion);

        for (Map.Entry<UUID, ObjectNode> entry : entities.entrySet()) {
            ObjectNode entity = entry.getValue().deepCopy();
            entity.put("technicalId", entry.getKey().toString());
            result.add(entity);
        }

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsWithMetaFields(String entityModel, String entityVersion) {
        return getItems(entityModel, entityVersion)
                .thenApply(items -> {
                    ArrayNode result = JsonNodeFactory.instance.arrayNode();
                    for (JsonNode item : items) {
                        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
                        wrapper.set("data", item);

                        ObjectNode meta = JsonNodeFactory.instance.objectNode();
                        meta.put("id", item.get("technicalId").asText());
                        meta.put("entityModel", entityModel);
                        meta.put("entityVersion", entityVersion);
                        wrapper.set("meta", meta);

                        result.add(wrapper);
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(String entityModel, String entityVersion, Object condition) {
        // For prototype, we'll do a simple implementation
        // In a real scenario, you'd parse the condition and filter accordingly
        logger.debug("Getting first item by condition: model={}, version={}, condition={}",
                entityModel, entityVersion, condition);

        return getItems(entityModel, entityVersion)
                .thenApply(items -> {
                    if (items.size() > 0) {
                        return Optional.of((ObjectNode) items.get(0));
                    }
                    return Optional.empty();
                });
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition) {
        // For prototype, return all items (condition filtering not implemented)
        logger.debug("Getting items by condition: model={}, version={}, condition={}",
                entityModel, entityVersion, condition);
        return getItems(entityModel, entityVersion);
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition, boolean inMemory) {
        // For prototype, same as above regardless of inMemory flag
        return getItemsByCondition(entityModel, entityVersion, condition);
    }

    @Override
    public CompletableFuture<UUID> addItem(String entityModel, String entityVersion, Object entity) {
        logger.debug("Adding item: model={}, version={}", entityModel, entityVersion);

        // Validate entity if it implements CyodaEntity
        if (entity instanceof CyodaEntity) {
            CyodaEntity cyodaEntity = (CyodaEntity) entity;
            if (!cyodaEntity.isValid()) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Entity validation failed: entity is not valid")
                );
            }
        }

        UUID technicalId = UUID.randomUUID();
        ObjectNode entityNode = convertToObjectNode(entity);

        storeEntity(entityModel, entityVersion, technicalId, entityNode);

        // Trigger workflow orchestrator
        triggerWorkflowOrchestrator(entityModel, entityVersion, technicalId, entityNode, "state_initial");

        return CompletableFuture.completedFuture(technicalId);
    }

    @Override
    public CompletableFuture<ArrayNode> addItemAndReturnTransactionInfo(String entityModel, String entityVersion, Object entity) {
        return addItem(entityModel, entityVersion, entity)
                .thenApply(technicalId -> {
                    ArrayNode result = JsonNodeFactory.instance.arrayNode();
                    ObjectNode transactionInfo = JsonNodeFactory.instance.objectNode();

                    ArrayNode entityIds = JsonNodeFactory.instance.arrayNode();
                    entityIds.add(technicalId.toString());
                    transactionInfo.set("entityIds", entityIds);
                    transactionInfo.put("transactionId", UUID.randomUUID().toString());

                    result.add(transactionInfo);
                    return result;
                });
    }

    @Override
    public CompletableFuture<List<UUID>> addItems(String entityModel, String entityVersion, Object entities) {
        logger.debug("Adding multiple items: model={}, version={}", entityModel, entityVersion);

        List<UUID> technicalIds = new ArrayList<>();

        if (entities instanceof Collection) {
            Collection<?> entityCollection = (Collection<?>) entities;
            for (Object entity : entityCollection) {
                UUID technicalId = UUID.randomUUID();
                ObjectNode entityNode = convertToObjectNode(entity);

                storeEntity(entityModel, entityVersion, technicalId, entityNode);
                technicalIds.add(technicalId);

                // Trigger workflow orchestrator for each entity
                triggerWorkflowOrchestrator(entityModel, entityVersion, technicalId, entityNode, "state_initial");
            }
        } else {
            // Single entity passed as Object
            UUID technicalId = UUID.randomUUID();
            ObjectNode entityNode = convertToObjectNode(entities);

            storeEntity(entityModel, entityVersion, technicalId, entityNode);
            technicalIds.add(technicalId);

            triggerWorkflowOrchestrator(entityModel, entityVersion, technicalId, entityNode, "state_initial");
        }

        return CompletableFuture.completedFuture(technicalIds);
    }

    @Override
    public CompletableFuture<ArrayNode> addItemsAndReturnTransactionInfo(String entityModel, String entityVersion, Object entities) {
        return addItems(entityModel, entityVersion, entities)
                .thenApply(technicalIds -> {
                    ArrayNode result = JsonNodeFactory.instance.arrayNode();
                    ObjectNode transactionInfo = JsonNodeFactory.instance.objectNode();

                    ArrayNode entityIds = JsonNodeFactory.instance.arrayNode();
                    for (UUID id : technicalIds) {
                        entityIds.add(id.toString());
                    }
                    transactionInfo.set("entityIds", entityIds);
                    transactionInfo.put("transactionId", UUID.randomUUID().toString());

                    result.add(transactionInfo);
                    return result;
                });
    }

    @Override
    public CompletableFuture<UUID> updateItem(String entityModel, String entityVersion, UUID technicalId, Object entity) {
        logger.debug("Updating item: model={}, version={}, id={}", entityModel, entityVersion, technicalId);

        ObjectNode existingEntity = getEntityFromStorage(entityModel, entityVersion, technicalId);
        if (existingEntity == null) {
            return CompletableFuture.failedFuture(
                    new NoSuchElementException("Entity not found for update: " + technicalId)
            );
        }

        // Validate entity if it implements CyodaEntity
        if (entity instanceof CyodaEntity) {
            CyodaEntity cyodaEntity = (CyodaEntity) entity;
            if (!cyodaEntity.isValid()) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Entity validation failed: entity is not valid")
                );
            }
        }

        ObjectNode entityNode = convertToObjectNode(entity);
        storeEntity(entityModel, entityVersion, technicalId, entityNode);

        // Trigger workflow orchestrator with update transition
        triggerWorkflowOrchestrator(entityModel, entityVersion, technicalId, entityNode, "entity_updated");

        return CompletableFuture.completedFuture(technicalId);
    }

    @Override
    public CompletableFuture<UUID> deleteItem(String entityModel, String entityVersion, UUID technicalId) {
        logger.debug("Deleting item: model={}, version={}, id={}", entityModel, entityVersion, technicalId);

        ObjectNode entity = removeEntityFromStorage(entityModel, entityVersion, technicalId);
        if (entity == null) {
            return CompletableFuture.failedFuture(
                    new NoSuchElementException("Entity not found for deletion: " + technicalId)
            );
        }

        return CompletableFuture.completedFuture(technicalId);
    }

    @Override
    public CompletableFuture<ArrayNode> deleteItems(String entityModel, String entityVersion) {
        logger.debug("Deleting all items: model={}, version={}", entityModel, entityVersion);

        Map<UUID, ObjectNode> entities = getEntitiesFromStorage(entityModel, entityVersion);
        ArrayNode result = JsonNodeFactory.instance.arrayNode();

        for (UUID technicalId : entities.keySet()) {
            removeEntityFromStorage(entityModel, entityVersion, technicalId);
            ObjectNode deletedInfo = JsonNodeFactory.instance.objectNode();
            deletedInfo.put("id", technicalId.toString());
            result.add(deletedInfo);
        }

        return CompletableFuture.completedFuture(result);
    }

    // Helper methods

    private ObjectNode convertToObjectNode(Object entity) {
        if (entity instanceof ObjectNode) {
            return (ObjectNode) entity;
        } else if (entity instanceof JsonNode) {
            return (ObjectNode) entity;
        } else {
            return convertDeclaredFieldsToObjectNode(entity);
        }
    }

    /**
     * Converts only the fields declared on the concrete class of the given entity (excluding superclass fields)
     * into an ObjectNode. Static, transient, and synthetic fields are ignored. If a field has @JsonProperty
     * with a non-empty value, that value is used as the property name.
     */
    private ObjectNode convertDeclaredFieldsToObjectNode(Object entity) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        Class<?> clazz = entity.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                continue;
            }
            String propName = field.getName();
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty != null && jsonProperty.value() != null && !jsonProperty.value().isEmpty()) {
                propName = jsonProperty.value();
            }
            try {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (value == null) {
                    node.putNull(propName);
                } else {
                    JsonNode valueNode = objectMapper.valueToTree(value);
                    node.set(propName, valueNode);
                }
            } catch (IllegalAccessException e) {
                // Skip fields we cannot access in prototype mode
                continue;
            }
        }
        return node;
    }

    private void storeEntity(String entityModel, String entityVersion, UUID technicalId, ObjectNode entity) {
        storage.computeIfAbsent(entityModel, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(entityVersion, k -> new ConcurrentHashMap<>())
                .put(technicalId, entity);

        logger.debug("Stored entity: model={}, version={}, id={}", entityModel, entityVersion, technicalId);
    }

    private ObjectNode getEntityFromStorage(String entityModel, String entityVersion, UUID technicalId) {
        return storage.getOrDefault(entityModel, Collections.emptyMap())
                .getOrDefault(entityVersion, Collections.emptyMap())
                .get(technicalId);
    }

    private Map<UUID, ObjectNode> getEntitiesFromStorage(String entityModel, String entityVersion) {
        return storage.getOrDefault(entityModel, Collections.emptyMap())
                .getOrDefault(entityVersion, Collections.emptyMap());
    }

    private ObjectNode removeEntityFromStorage(String entityModel, String entityVersion, UUID technicalId) {
        Map<String, Map<UUID, ObjectNode>> modelStorage = storage.get(entityModel);
        if (modelStorage == null) return null;

        Map<UUID, ObjectNode> versionStorage = modelStorage.get(entityVersion);
        if (versionStorage == null) return null;

        return versionStorage.remove(technicalId);
    }

    private void triggerWorkflowOrchestrator(String entityModel, String entityVersion, UUID technicalId, ObjectNode entityNode, String transition) {
        try {
            if (orchestratorFactory.hasOrchestrator(entityModel)) {
                WorkflowOrchestrator orchestrator = orchestratorFactory.getOrchestrator(entityModel);

                // Create single mutable request instances so updated payload propagates across processors/transitions
                final EntityProcessorCalculationRequest processorRequest = createMockProcessorRequestWithData(technicalId.toString(), entityNode);
                final EntityCriteriaCalculationRequest criteriaRequest = createMockCriteriaRequestWithData(technicalId.toString(), entityNode);

                CyodaEventContext<EntityProcessorCalculationRequest> processorContext = new CyodaEventContext<>() {
                    @Override
                    public CloudEvent getCloudEvent() { return CloudEvent.getDefaultInstance(); }
                    @Override
                    public EntityProcessorCalculationRequest getEvent() { return processorRequest; }
                };
                CyodaEventContext<EntityCriteriaCalculationRequest> criteriaContext = new CyodaEventContext<>() {
                    @Override
                    public CloudEvent getCloudEvent() { return CloudEvent.getDefaultInstance(); }
                    @Override
                    public EntityCriteriaCalculationRequest getEvent() { return criteriaRequest; }
                };

                String nextTransition = orchestrator.run(
                        technicalId.toString(),
                        processorContext,
                        criteriaContext,
                        transition,
                        updatedData -> {
                            // Persist updated data after each transition and propagate to contexts
                            if (updatedData != null) {
                                storeEntity(entityModel, entityVersion, technicalId, updatedData);
                                try { processorRequest.getPayload().setData(updatedData); } catch (Exception ignored) {}
                                try { criteriaRequest.getPayload().setData(updatedData); } catch (Exception ignored) {}
                            }
                        }
                );
                logger.info("Workflow orchestrator for {} completed. Next transition: {}",
                        entityModel, nextTransition);
            } else {
                logger.debug("No workflow orchestrator found for entity model: {}", entityModel);
            }
        } catch (Exception e) {
            logger.error("Error running workflow orchestrator for entity model: " + entityModel, e);
        }
    }

    private CyodaEntity convertToCyodaEntity(ObjectNode entityNode) {
        // For prototype purposes, create a simple CyodaEntity implementation
        return new CyodaEntity() {
            @Override
            public OperationSpecification getModelKey() {
                ModelSpec modelSpec = new ModelSpec();
                modelSpec.setName(entityNode.path("entityModel").asText("Unknown"));
                modelSpec.setVersion(1);
                return new OperationSpecification.Entity(modelSpec, entityNode.path("entityModel").asText("Unknown"));
            }

            @Override
            public boolean isValid() {
                return true; // For prototype purposes, always return true
            }
        };
    }

    /**
     * Creates a mock processor context for workflow orchestrator execution.
     * This context includes the actual entity data in the payload so processors can extract it.
     */
    public CyodaEventContext<EntityProcessorCalculationRequest> createMockProcessorContext(String technicalId, CyodaEntity entity) {
        return new CyodaEventContext<EntityProcessorCalculationRequest>() {
            @Override
            public CloudEvent getCloudEvent() {
                return CloudEvent.getDefaultInstance();
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return createMockProcessorRequest(technicalId, entity);
            }
        };
    }

    /**
     * Creates a mock processor context using ObjectNode data directly.
     * This avoids serialization issues with CyodaEntity conversion.
     */
    private CyodaEventContext<EntityProcessorCalculationRequest> createMockProcessorContextWithData(String technicalId, ObjectNode entityData) {
        return new CyodaEventContext<EntityProcessorCalculationRequest>() {
            @Override
            public CloudEvent getCloudEvent() {
                return CloudEvent.getDefaultInstance();
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return createMockProcessorRequestWithData(technicalId, entityData);
            }
        };
    }

    /**
     * Creates a mock criteria context for workflow orchestrator execution.
     * This context includes the actual entity data in the payload so criteria can extract it.
     */
    public CyodaEventContext<EntityCriteriaCalculationRequest> createMockCriteriaContext(String technicalId, CyodaEntity entity) {
        return new CyodaEventContext<EntityCriteriaCalculationRequest>() {
            @Override
            public CloudEvent getCloudEvent() {
                return CloudEvent.getDefaultInstance();
            }

            @Override
            public EntityCriteriaCalculationRequest getEvent() {
                return createMockCriteriaRequest(technicalId, entity);
            }
        };
    }

    /**
     * Creates a mock criteria context using ObjectNode data directly.
     * This avoids serialization issues with CyodaEntity conversion.
     */
    private CyodaEventContext<EntityCriteriaCalculationRequest> createMockCriteriaContextWithData(String technicalId, ObjectNode entityData) {
        return new CyodaEventContext<EntityCriteriaCalculationRequest>() {
            @Override
            public CloudEvent getCloudEvent() {
                return CloudEvent.getDefaultInstance();
            }

            @Override
            public EntityCriteriaCalculationRequest getEvent() {
                return createMockCriteriaRequestWithData(technicalId, entityData);
            }
        };
    }

    /**
     * Creates a mock EntityProcessorCalculationRequest with the entity data in the payload.
     */
    private EntityProcessorCalculationRequest createMockProcessorRequest(String technicalId, CyodaEntity entity) {
        // Convert the CyodaEntity back to ObjectNode for the payload
        ObjectNode entityData = convertCyodaEntityToObjectNode(entity);

        // Create DataPayload with the entity data
        DataPayload payload = new DataPayload();
        payload.setType("entity");
        payload.setData(entityData);

        // Create the mock request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(technicalId);
        request.setProcessorId(UUID.randomUUID().toString());
        request.setProcessorName("MockProcessor");
        request.setTransactionId(UUID.randomUUID().toString());
        request.setPayload(payload);

        return request;
    }

    /**
     * Creates a mock EntityCriteriaCalculationRequest with the entity data in the payload.
     */
    private EntityCriteriaCalculationRequest createMockCriteriaRequest(String technicalId, CyodaEntity entity) {
        // Convert the CyodaEntity back to ObjectNode for the payload
        ObjectNode entityData = convertCyodaEntityToObjectNode(entity);

        // Create DataPayload with the entity data
        DataPayload payload = new DataPayload();
        payload.setType("entity");
        payload.setData(entityData);

        // Create the mock request
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(technicalId);
        request.setCriteriaId(UUID.randomUUID().toString());
        request.setCriteriaName("MockCriterion");
        request.setTarget(EntityCriteriaCalculationRequest.Target.WORKFLOW);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setPayload(payload);

        return request;
    }

    /**
     * Creates a mock EntityProcessorCalculationRequest using ObjectNode data directly.
     */
    private EntityProcessorCalculationRequest createMockProcessorRequestWithData(String technicalId, ObjectNode entityData) {
        // Create DataPayload with the entity data
        DataPayload payload = new DataPayload();
        payload.setType("entity");
        payload.setData(entityData);

        // Create the mock request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(technicalId);
        request.setProcessorId(UUID.randomUUID().toString());
        request.setProcessorName("MockProcessor");
        request.setTransactionId(UUID.randomUUID().toString());
        request.setPayload(payload);

        return request;
    }

    /**
     * Creates a mock EntityCriteriaCalculationRequest using ObjectNode data directly.
     */
    private EntityCriteriaCalculationRequest createMockCriteriaRequestWithData(String technicalId, ObjectNode entityData) {
        // Create DataPayload with the entity data
        DataPayload payload = new DataPayload();
        payload.setType("entity");
        payload.setData(entityData);

        // Create the mock request
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId(technicalId);
        request.setCriteriaId(UUID.randomUUID().toString());
        request.setCriteriaName("MockCriterion");
        request.setTarget(EntityCriteriaCalculationRequest.Target.WORKFLOW);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setPayload(payload);

        return request;
    }

    /**
     * Converts a CyodaEntity to ObjectNode for use in mock requests.
     */
    private ObjectNode convertCyodaEntityToObjectNode(CyodaEntity entity) {
        // Since we created the CyodaEntity from an ObjectNode in convertToCyodaEntity,
        // we need to convert it back. For prototype purposes, we'll use the ObjectMapper.
        return objectMapper.valueToTree(entity);
    }
}
