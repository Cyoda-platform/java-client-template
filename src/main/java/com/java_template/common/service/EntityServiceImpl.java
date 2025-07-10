package com.java_template.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.auth.Authentication;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.repository.dto.Meta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class EntityServiceImpl implements EntityService {

    private static final int MAX_TOKEN_RETRY_ATTEMPTS = 3;

    private static final Logger logger = LoggerFactory.getLogger(EntityServiceImpl.class);

    private final CrudRepository repository;
    private final Authentication auth;

    @Autowired
    public EntityServiceImpl(CrudRepository repository, Authentication auth) {
        this.repository = repository;
        this.auth = auth;
    }

    private <T> CompletableFuture<T> executeWithTokenRetry(TokenRepositoryCall<T> repositoryCall) {
        return executeWithTokenRetryInternal(repositoryCall, 1);
    }

    /**
     * Executes repository method with automatic retry on 401 Unauthorized.
     * @param repositoryCall repository method to call (as functional interface)
     * @param <T> result type
     * @return result of repository call
     */
    private <T> CompletableFuture<T> executeWithTokenRetryInternal(TokenRepositoryCall<T> repositoryCall, int attempt) {
        String token = auth.getAccessToken().getTokenValue();
        return repositoryCall.call(token).handle((result, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (attempt < MAX_TOKEN_RETRY_ATTEMPTS &&
                    cause instanceof ResponseStatusException &&
                    ((ResponseStatusException) cause).getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                logger.warn("Request to Cyoda failed with 401 Unauthorized, invalidating token and retrying (attempt {} of {})...", attempt + 1, MAX_TOKEN_RETRY_ATTEMPTS);
                auth.invalidateTokens();
                return executeWithTokenRetryInternal(repositoryCall, attempt + 1);
            }
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(cause);
            return failed;
        }).thenCompose(f -> f);
    }

    // Functional interface for repository calls that require a token
    @FunctionalInterface
    public interface TokenRepositoryCall<T> {
        CompletableFuture<T> call(String token);
    }

    @Override
    public CompletableFuture<ObjectNode> getItem(String entityModel, String entityVersion, UUID technicalId) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findById(meta, technicalId).thenApply(resultNode -> {
                ObjectNode dataNode = (ObjectNode) resultNode.path("data").deepCopy();
                JsonNode idNode = resultNode.at("/meta/id");
                if (!idNode.isMissingNode()) {
                    dataNode.put("technicalId", idNode.asText());
                }
                return dataNode;
            });
        });
    }

    @Override
    public CompletableFuture<ObjectNode> getItemWithMetaFields(String entityModel, String entityVersion, UUID technicalId) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findById(meta, technicalId);
        });
    }

    @Override
    public CompletableFuture<ArrayNode> getItems(String entityModel, String entityVersion) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findAll(meta).thenApply(resultArray -> {
                ArrayNode simplifiedArray = JsonNodeFactory.instance.arrayNode();
                for (JsonNode item : resultArray) {
                    ObjectNode data = (ObjectNode) item.path("data").deepCopy();
                    JsonNode technicalId = item.at("/meta/id");
                    data.set("technicalId", technicalId);
                    simplifiedArray.add(data);
                }
                return simplifiedArray;
            });
        });
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsWithMetaFields(String entityModel, String entityVersion) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findAll(meta);
        });
    }

    @Override
    public CompletableFuture<Optional<ObjectNode>> getFirstItemByCondition(String entityModel, String entityVersion, Object condition) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findAllByCriteria(meta, condition).thenApply(items -> {
                if (items == null || items.isEmpty()) {
                    return Optional.empty();
                }
                JsonNode firstItem = items.get(0);
                JsonNode dataNode = firstItem.path("data");

                if (!dataNode.isObject()) {
                    return Optional.empty();
                }
                ObjectNode data = (ObjectNode) dataNode.deepCopy();
                JsonNode technicalId = firstItem.at("/meta/id");
                data.set("technicalId", technicalId);

                return Optional.of(data);
            });
        });
    }

    @Override
    public CompletableFuture<ArrayNode> getItemsByCondition(String entityModel, String entityVersion, Object condition) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.findAllByCriteria(meta, condition).thenApply(items -> {
                ArrayNode simplifiedArray = JsonNodeFactory.instance.arrayNode();
                for (JsonNode item : items) {
                    ObjectNode data = (ObjectNode) item.path("data").deepCopy();
                    JsonNode technicalId = item.at("/meta/id");
                    data.set("technicalId", technicalId);
                    simplifiedArray.add(data);
                }
                return simplifiedArray;
            });
        });
    }

    @Override
    public CompletableFuture<UUID> addItem(String entityModel, String entityVersion, Object entity) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.save(meta, entity)
                    .thenApply(resultArray ->{
                        JsonNode first = resultArray.get(0);
                        return UUID.fromString(first.get("entityIds").get(0).asText());
                    });
        });
    }

    @Override
    public CompletableFuture<ArrayNode> addItemAndReturnTransactionInfo(String entityModel, String entityVersion, Object entity) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(auth.getAccessToken().getTokenValue(), entityModel, entityVersion);
            return repository.save(meta, entity);
        });
    }

    @Override
    public CompletableFuture<List<UUID>> addItems(String entityModel, String entityVersion, Object entities) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.save(meta, entities)
                    .thenApply(resultArray -> {
                        JsonNode first = resultArray.get(0);
                        ArrayNode entityIdsNode = (ArrayNode) first.get("entityIds");
                        List<UUID> ids = new ArrayList<>();
                        if (entityIdsNode != null) {
                            for (JsonNode idNode : entityIdsNode) {
                                ids.add(UUID.fromString(idNode.asText()));
                            }
                        }
                        return ids;
                    });
        });
    }

    @Override
    public CompletableFuture<ArrayNode> addItemsAndReturnTransactionInfo(String entityModel, String entityVersion, Object entities) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.saveAll(meta, entities);
        });
    }

    @Override
    public CompletableFuture<UUID> updateItem(String entityModel, String entityVersion, UUID technicalId, Object entity) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.update(meta, technicalId, entity)
                    .thenApply(resultNode -> UUID.fromString(resultNode.get("entityIds").get(0).asText()));
        });
    }

    @Override
    public CompletableFuture<UUID> deleteItem(String entityModel, String entityVersion, UUID technicalId) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.deleteById(meta, technicalId)
                    .thenApply(resultNode -> UUID.fromString(resultNode.get("id").asText()));
        });
    }

    @Override
    public CompletableFuture<ArrayNode> deleteItems(String entityModel, String entityVersion) {
        return executeWithTokenRetry(token -> {
            Meta meta = repository.getMeta(token, entityModel, entityVersion);
            return repository.deleteAll(meta);
        });
    }
}

