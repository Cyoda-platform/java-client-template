package com.java_template.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import com.java_template.common.repository.CrudRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;

import java.util.Objects;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class EntityServiceImpl implements EntityService {

    private static final String UPDATE_TRANSITION = "UPDATE";
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int FIRST_PAGE = 1;

    // private static final Logger logger =
    // LoggerFactory.getLogger(EntityServiceImpl.class);

    private final CrudRepository repository;
    private final ObjectMapper objectMapper;

    public EntityServiceImpl(
            final CrudRepository repository,
            final ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<ObjectNode> getItem(@NotNull final UUID technicalId) {
        return repository.findById(technicalId).thenApply(resultNode -> {
            final ObjectNode dataNode = resultNode.path("data").deepCopy();
            final var idNode = resultNode.at("/meta/id");
            if (!idNode.isMissingNode()) {
                dataNode.put("technicalId", idNode.asText());
            }
            return dataNode;
        });
    }

    private ArrayNode enhanceWithTechId(@NotNull final ArrayNode arrayNode) {
        return Streams.stream(arrayNode)
                .filter(Objects::nonNull)
                .map(this::enhanceWithTechId)
                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll);
    }

    private ObjectNode enhanceWithTechId(@NotNull final JsonNode originalNode) {
        final var copyNode = originalNode.path("data").deepCopy();
        if (copyNode instanceof MissingNode) {
            return (ObjectNode) originalNode;
        }
        final var idNode = originalNode.at("/meta/id");
        if (!idNode.isMissingNode()) {
            ((ObjectNode) copyNode).put("technicalId", idNode.asText());
        }
        return (ObjectNode) copyNode;
    }

    @Override
    public CompletableFuture<ArrayNode> getItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @Nullable final Integer pageSize,
            @Nullable final Integer pageNumber,
            @Nullable final Date pointTime
    ) {
        return repository.findAll(
                modelName,
                modelVersion,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
                pageNumber != null ? pageNumber : FIRST_PAGE,
                pointTime
        ).thenApply(this::enhanceWithTechId);
    }

    @Override
    public CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object condition
    ) {
        return repository.findAllByCriteria(
                modelName,
                modelVersion,
                objectMapper.convertValue(condition, GroupCondition.class),
                1,
                1,
                false
        ).thenApply(items -> {
            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }

            final var firstItem = items.get(0);
            final var dataNode = firstItem.path("data");

            if (!dataNode.isObject()) {
                return Optional.empty();
            }

            final ObjectNode data = enhanceWithTechId(dataNode);
            return Optional.of(data);
        });
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object condition,
            @NotNull final boolean inMemory
    ) {
        return repository.findAllByCriteria(
                modelName,
                modelVersion,
                objectMapper.convertValue(condition, GroupCondition.class),
                DEFAULT_PAGE_SIZE,
                FIRST_PAGE,
                inMemory
        ).thenApply(items -> Streams.stream(items)
                .filter(Objects::nonNull)
                .map(item -> inMemory ? item.deepCopy() : enhanceWithTechId(item))
                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll)
        );
    }

    @Override
    public CompletableFuture<UUID> addItem(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object entity
    ) {
        return repository.save(modelName, modelVersion, (JsonNode) entity)
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds)
                .thenApply(List::getFirst);
    }

    @Override
    public CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object entity
    ) {
        return repository.save(modelName, modelVersion, objectMapper.valueToTree(entity))
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(objectMapper::valueToTree);

    }

    @Override
    public CompletableFuture<List<UUID>> addItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object entities
    ) {
        return repository.saveAll(modelName, modelVersion, objectMapper.valueToTree(entities))
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds);
    }

    @Override
    public CompletableFuture<ObjectNode> addItemsAndReturnTransactionInfo(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion,
            @NotNull final Object entities
    ) {
        return repository.saveAll(
                        modelName,
                        modelVersion,
                        objectMapper.valueToTree(entities)
                ).thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(objectMapper::valueToTree);
    }

    @Override
    public CompletableFuture<UUID> updateItem(@NotNull final UUID entityId, @NotNull final Object entity) {
        return repository.update(entityId, objectMapper.valueToTree(entity), UPDATE_TRANSITION)
                .thenApply(EntityTransactionResponse::getTransactionInfo)
                .thenApply(EntityTransactionInfo::getEntityIds)
                .thenApply(List::getFirst);
    }

    @Override
    public CompletableFuture<List<UUID>> updateItems(@NotNull final Object entities) {
        return repository.updateAll(objectMapper.convertValue(entities, new TypeReference<>() {}), UPDATE_TRANSITION)
                .thenApply(transactionResponses -> transactionResponses.stream()
                        .map(EntityTransactionResponse::getTransactionInfo)
                        .map(EntityTransactionInfo::getEntityIds)
                        .flatMap(Collection::stream)
                        .toList()
                );
    }

    @Override
    public CompletableFuture<UUID> deleteItem(@NotNull final UUID entityId) {
        return repository.deleteById(entityId).thenApply(EntityDeleteResponse::getEntityId);
    }

    @Override
    public CompletableFuture<Integer> deleteItems(
            @NotNull final String modelName,
            @NotNull final Integer modelVersion
    ) {
        return repository.deleteAll(modelName, modelVersion);
    }
}