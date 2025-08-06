package com.java_template.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.CrudRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
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
        return repository.findById(technicalId)
            .thenApply(resultNode -> {
                final ObjectNode dataNode = resultNode.path("data").deepCopy();
                final var idNode = resultNode.at("/meta/id");
                if (!idNode.isMissingNode()) {
                    dataNode.put("technicalId", idNode.asText());
                }
                return dataNode;
            });
    }

    private ArrayNode enhanceWithTechId(final ArrayNode arrayNode) {
        final var simplifiedArray = JsonNodeFactory.instance.arrayNode();
        for (final var item : arrayNode) {
            final ObjectNode data = item.path("data").deepCopy();
            final var technicalId = item.at("/meta/id");
            data.set("technicalId", technicalId);
            simplifiedArray.add(data);
        }
        return simplifiedArray;
    }

    @Override
    public CompletableFuture<ArrayNode> getItems(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @Nullable final Integer pageSize,
        @Nullable final Integer pageNumber,
        @Nullable final Date pointTime
    ) {
        return repository.findAll(
            entityModel,
            Integer.parseInt(entityVersion),
            pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
            pageNumber != null ? pageNumber : FIRST_PAGE,
            pointTime
        ).thenApply(this::enhanceWithTechId);
    }

    @Override
    public CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @NotNull final Object condition
    ) {
        return repository.findAllByCriteria(
            entityModel,
            Integer.parseInt(entityVersion),
            condition,
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
            final ObjectNode data = dataNode.deepCopy();
            final var technicalId = firstItem.at("/meta/id");
            data.set("technicalId", technicalId);

            return Optional.of(data);
        });
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(
        String entityModel,
        String entityVersion,
        Object condition
    ) {
        return getItemsByCondition(entityModel, entityModel, condition, false);
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(
        String entityModel,
        String entityVersion,
        Object condition,
        boolean inMemory
    ) {
        return repository.findAllByCriteria(
            entityModel,
            Integer.parseInt(entityVersion),
            condition,
            DEFAULT_PAGE_SIZE,
            FIRST_PAGE,
            inMemory
        ).thenApply(items -> {
            final var simplifiedArray = JsonNodeFactory.instance.arrayNode();
            for (final var item : items) {
                final ObjectNode data = inMemory ? item.deepCopy() : item.path("data").deepCopy();
                if (!inMemory) {
                    // Regular search returns entities with meta structure
                    final var technicalId = item.at("/meta/id");
                    data.set("technicalId", technicalId);
                }
                simplifiedArray.add(data);
            }
            return simplifiedArray;
        });
    }

    @Override
    public CompletableFuture<UUID> addItem(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @NotNull final Object entity
    ) {
        return repository.save(entityModel, Integer.parseInt(entityVersion), (JsonNode) entity)
            .thenApply(EntityTransactionResponse::getTransactionInfo)
            .thenApply(EntityTransactionInfo::getEntityIds)
            .thenApply(List::getFirst);
    }

    @Override
    public CompletableFuture<ObjectNode> addItemAndReturnTransactionInfo(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @NotNull final Object entity
    ) {
        return repository.save(entityModel, Integer.parseInt(entityVersion), (JsonNode) entity)
            .thenApply(EntityTransactionResponse::getTransactionInfo)
            .thenApply(objectMapper::valueToTree);

    }

    @Override
    public CompletableFuture<List<UUID>> addItems(String entityModel, String entityVersion, Object entities) {
        return repository.saveAll(entityModel, Integer.parseInt(entityVersion), (JsonNode) entities)
            .thenApply(EntityTransactionResponse::getTransactionInfo)
            .thenApply(EntityTransactionInfo::getEntityIds);
    }

    @Override
    public CompletableFuture<ObjectNode> addItemsAndReturnTransactionInfo(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @NotNull final Object entities
    ) {
        return repository.saveAll(
                entityModel,
                Integer.parseInt(entityVersion),
                (JsonNode) entities
            )
            .thenApply(EntityTransactionResponse::getTransactionInfo)
            .thenApply(objectMapper::valueToTree);
    }

    @Override
    public CompletableFuture<UUID> updateItem(
        @NotNull final String entityModel,
        @NotNull final String entityVersion,
        @NotNull final UUID technicalId,
        @NotNull final Object entity
    ) {
        return repository.update(entityModel, Integer.parseInt(entityVersion), technicalId, (JsonNode) entity)
            .thenApply(result -> result.getTransactionInfo().getEntityIds().getFirst());

    }

    @Override
    public CompletableFuture<UUID> deleteItem(@NotNull final UUID entityId) {
        return repository.deleteById(entityId).thenApply(EntityDeleteResponse::getEntityId);

    }

    @Override
    public CompletableFuture<List<UUID>> deleteItems(
        @NotNull final String entityModel,
        @NotNull final String entityVersion
    ) {
        return repository.deleteAll(entityModel, Integer.parseInt(entityVersion))
            .thenApply(EntityDeleteAllResponse::getEntityIds);
    }
}
