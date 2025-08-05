package com.java_template.common.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.grpc.client_v2.CloudEventBuilder;
import com.java_template.common.grpc.client_v2.CloudEventParser;
import com.java_template.common.repository.dto.Meta;
import com.java_template.common.util.HttpUtils;
import io.cloudevents.v1.proto.CloudEvent;
import java.util.function.Function;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.search.EntityGetRequest;
import org.cyoda.cloud.api.event.search.EntityResponse;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.java_template.common.config.Config.CYODA_API_URL;

@Repository
public class CyodaRepository implements CrudRepository {
    private final Logger logger = LoggerFactory.getLogger(CyodaRepository.class);
    private final HttpUtils httpUtils;
    private final ObjectMapper objectMapper;
    private final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub;
    private final CloudEventBuilder cloudEventBuilder;
    private final CloudEventParser cloudEventParser;

    private final String FORMAT = "JSON"; // or "XML"

    public CyodaRepository(
            final HttpUtils httpUtils,
            final ObjectMapper objectMapper,
            final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub,
            final CloudEventBuilder cloudEventBuilder,
            final CloudEventParser cloudEventParser
    ) {
        this.objectMapper = objectMapper;
        this.httpUtils = httpUtils;
        this.cloudEventsServiceBlockingStub = cloudEventsServiceBlockingStub;
        this.cloudEventBuilder = cloudEventBuilder;
        this.cloudEventParser = cloudEventParser;
    }

    @Override
    public CompletableFuture<ObjectNode> findById(final UUID id) {
        return getById(id);
    }

    @Override
    public CompletableFuture<ArrayNode> findAllByCriteria(Meta meta, Object criteria) {
        return findAllByCondition(meta, criteria);
    }

    @Override
    public CompletableFuture<ArrayNode> findAllByCriteria(Meta meta, Object criteria, boolean inMemory) {
        if (inMemory) {
            return findAllByConditionInMemory(meta, criteria);
        } else {
            return findAllByCondition(meta, criteria);
        }
    }

    @Override
    public CompletableFuture<ArrayNode> save(Meta meta, Object entity) {
        return saveNewEntities(meta, entity);
    }

    @Override
    public CompletableFuture<ArrayNode> saveAll(Meta meta, Object entities) {
        return saveNewEntities(meta, entities);
    }

    @Override
    public CompletableFuture<ObjectNode> update(Meta meta, UUID id, Object entity) {
        return updateEntity(meta, id, entity);
    }

    @Override
    public CompletableFuture<ObjectNode> updateAll(Meta meta, List<Object> entities) {
        return null;
    }

    @Override
    public Meta getMeta(String token, String entityModel, String entityVersion) {
        return new Meta(token, entityModel, entityVersion);
    }

    @Override
    public CompletableFuture<ObjectNode> count(Meta meta) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> deleteById(Meta meta, UUID id) {
        return deleteEntity(meta, id);
    }

    @Override
    public CompletableFuture<ObjectNode> delete(Meta meta, Object entity) {
        return null;
    }

    @Override
    public CompletableFuture<ArrayNode> deleteAll(Meta meta) {
        return deleteAllByModel(meta);
    }

    @Override
    public CompletableFuture<ObjectNode> deleteAllEntities(Meta meta, List<Object> entities) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> deleteAllByKey(Meta meta, List<Object> keys) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> deleteByKey(Meta meta, Object key) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> existsByKey(Meta meta, Object key) {
        return null;
    }

    @Override
    public CompletableFuture<ArrayNode> findAll(Meta meta) {
        return getAllEntities(meta);
    }

    @Override
    public CompletableFuture<ObjectNode> findAllByKey(Meta meta, List<Object> keys) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> findByKey(Meta meta, Object key) {
        return null;
    }

    private CompletableFuture<ArrayNode> getAllEntities(Meta meta) {
        String path = String.format("entity/%s/%s", meta.getEntityModel(), meta.getEntityVersion());
        return httpUtils.sendGetRequest(meta.getToken(), CYODA_API_URL, path)
                .thenApply(response -> {
                    JsonNode jsonNode = response.get("json");
                    if (jsonNode != null && jsonNode.isArray()) {
                        return (ArrayNode) jsonNode;
                    } else {
                        logger.warn("Expected an ArrayNode under 'json', but got: {}", jsonNode);
                        return JsonNodeFactory.instance.arrayNode();
                    }
                });
    }

    private <RESPONSE_TYPE extends BaseEvent> CompletableFuture<RESPONSE_TYPE> sendAndGet(
            final Function<CloudEvent, CloudEvent> apiCall,
            final BaseEvent baseEvent,
            final Class<RESPONSE_TYPE> responseType
    ) {
        try {
            final CloudEvent requestEvent = cloudEventBuilder.buildEvent(baseEvent);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return apiCall.apply(requestEvent);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }).thenApply(responseEvent ->
                    cloudEventParser.parseCloudEvent(responseEvent, responseType).orElse(null)
            );
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<ObjectNode> getById(final UUID entityId) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityGetRequest().withId(UUID.randomUUID().toString()).withEntityId(entityId),
                EntityResponse.class
        ).thenApplyAsync(EntityResponse::getPayload)
                .thenApplyAsync(DataPayload::getData)
                .thenApplyAsync(it -> (ObjectNode) it);

//        String path = String.format("entity/%s", id);
//
//        return httpUtils.sendGetRequest(meta.getToken(), CYODA_API_URL, path)
//                .thenApply(response -> (ObjectNode) response.get("json"));
    }

    private CompletableFuture<ArrayNode> saveNewEntities(Meta meta, Object data) {
        String path = String.format("entity/%s/%s/%s", FORMAT, meta.getEntityModel(), meta.getEntityVersion());

        return httpUtils.sendPostRequest(meta.getToken(), CYODA_API_URL, path, data)
                .thenApply(response -> {
                    if (response != null) {
                        JsonNode jsonNode = response.get("json");

                        if (jsonNode != null && jsonNode.isArray()) {
                            logger.info("Successfully saved new entities. Response: {}", response);
                            return (ArrayNode) jsonNode;
                        } else {
                            logger.error("Response does not contain a valid 'json' array. Response: {}", response);
                            throw new RuntimeException("Response does not contain a valid 'json' array");
                        }
                    } else {
                        logger.error("Failed to save new entity. Response is null");
                        throw new RuntimeException("Failed to save new entity: Response is null");
                    }
                });
    }

    private CompletableFuture<ObjectNode> updateEntity(Meta meta, UUID id, Object entity) {
        String path = String.format("entity/%s/%s/%s", FORMAT, id, meta.getUpdateTransition());
        return httpUtils.sendPutRequest(meta.getToken(), CYODA_API_URL, path, entity)
                .thenApply(response -> (ObjectNode) response.get("json"));
    }

    private CompletableFuture<ObjectNode> deleteEntity(Meta meta, UUID id) {
        String path = String.format("entity/%s", id);

        return httpUtils.sendDeleteRequest(meta.getToken(), CYODA_API_URL, path)
                .thenApply(response -> (ObjectNode) response.get("json"));
    }

    private CompletableFuture<ArrayNode> deleteAllByModel(Meta meta) {
        String path = String.format("entity/%s/%s", meta.getEntityModel(), meta.getEntityVersion());

        return httpUtils.sendDeleteRequest(meta.getToken(), CYODA_API_URL, path)
                .thenApply(response -> (ArrayNode) response.get("json"));
    }

    private CompletableFuture<ArrayNode> findAllByCondition(Meta meta, Object condition) {
        return searchEntities(meta, condition)
                .thenApply(resp -> {
                    if (resp.get("json").get("page").get("totalElements").asInt() == 0) {
                        return objectMapper.createArrayNode();
                    }
                    return (ArrayNode) resp.get("json").get("_embedded").get("objectNodes");
                })
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    if (cause instanceof ResponseStatusException rsEx &&
                            rsEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        logger.warn("Model not found, returning empty array: {}", rsEx.getReason());
                        return objectMapper.createArrayNode();
                    }
                    throw new CompletionException("Unhandled error", cause);
                });
    }

    private CompletableFuture<ArrayNode> findAllByConditionInMemory(Meta meta, Object condition) {
        return searchEntitiesInMemory(meta, condition)
                .thenApply(response -> {
                    JsonNode jsonNode = response.get("json");
                    if (jsonNode != null && jsonNode.isArray()) {
                        ArrayNode results = objectMapper.createArrayNode();
                        for (JsonNode item : jsonNode) {
                            if (item.has("data")) {
                                results.add(item.get("data"));
                            }
                        }
                        return results;
                    } else {
                        logger.warn("Expected an ArrayNode under 'json' for in-memory search, but got: {}", jsonNode);
                        return objectMapper.createArrayNode();
                    }
                })
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    if (cause instanceof ResponseStatusException rsEx &&
                            rsEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        logger.warn(
                                "Model not found for in-memory search, returning empty array: {}",
                                rsEx.getReason()
                        );
                        return objectMapper.createArrayNode();
                    }
                    throw new CompletionException("Unhandled error in in-memory search", cause);
                });
    }

    private CompletableFuture<ObjectNode> searchEntities(Meta meta, Object condition) {
        return searchEntities(meta, condition, 100, 0);
    }

    private CompletableFuture<ObjectNode> searchEntities(Meta meta, Object condition, int pageSize, int pageNumber) {
        return createSnapshotSearch(meta.getToken(), meta.getEntityModel(), meta.getEntityVersion(), condition)
                .thenCompose(snapshotId -> {
                    if (snapshotId == null) {
                        logger.error("Snapshot ID not found in response");
                        return CompletableFuture.completedFuture(objectMapper.createObjectNode());
                    }
                    try {
                        return waitForSearchCompletion(meta.getToken(), snapshotId, 10_000, 500)
                                .thenCompose(statusResponse -> getSearchResult(
                                        meta.getToken(),
                                        snapshotId,
                                        pageSize,
                                        pageNumber
                                ));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private CompletableFuture<ObjectNode> searchEntitiesInMemory(Meta meta, Object condition) {
        return searchEntitiesInMemory(meta, condition, 1000, 60000);
    }

    private CompletableFuture<ObjectNode> searchEntitiesInMemory(
            Meta meta,
            Object condition,
            int limit,
            int timeoutMillis
    ) {
        String searchPath = String.format("search/%s/%s", meta.getEntityModel(), meta.getEntityVersion());

        // Create search request with parameters
        ObjectNode searchRequest = objectMapper.createObjectNode();
        try {
            // Convert condition to JsonNode if it's not already
            JsonNode conditionNode;
            if (condition instanceof JsonNode) {
                conditionNode = (JsonNode) condition;
            } else {
                conditionNode = objectMapper.valueToTree(condition);
            }

            // Copy the condition structure
            searchRequest.setAll((ObjectNode) conditionNode);

            // Add query parameters for in-memory search
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", String.valueOf(limit));
            queryParams.put("timeoutMillis", String.valueOf(timeoutMillis));

            logger.info(
                    "Performing in-memory search for entity: {}/{} with condition: {}",
                    meta.getEntityModel(), meta.getEntityVersion(), searchRequest
            );

            return httpUtils.sendPostRequest(meta.getToken(), CYODA_API_URL, searchPath, searchRequest, queryParams);

        } catch (Exception e) {
            logger.error("Error preparing in-memory search request", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> createSnapshotSearch(
            String token,
            String entityModel,
            String entityVersion,
            Object condition
    ) {
        String searchPath = String.format("search/snapshot/%s/%s", entityModel, entityVersion);
        return httpUtils.sendPostRequest(token, CYODA_API_URL, searchPath, condition)
                .thenApply(response -> response.get("json").asText());
    }

    private CompletableFuture<ObjectNode> waitForSearchCompletion(
            String token,
            String snapshotId,
            long timeoutMillis,
            long intervalMillis
    ) throws IOException {
        long startTime = System.currentTimeMillis();
        return pollSnapshotStatus(token, snapshotId, startTime, timeoutMillis, intervalMillis);
    }

    private CompletableFuture<ObjectNode> pollSnapshotStatus(
            String token,
            String snapshotId,
            long startTime,
            long timeoutMillis,
            long intervalMillis
    ) throws IOException {
        return getSnapshotStatus(token, snapshotId).thenCompose(statusResponse -> {
            String status = statusResponse.get("json").get("snapshotStatus").asText();

            if ("SUCCESSFUL".equals(status)) {
                return CompletableFuture.completedFuture(statusResponse);
            } else if (!"RUNNING".equals(status)) {
                return CompletableFuture.failedFuture(new RuntimeException("Snapshot search failed: " + statusResponse));
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > timeoutMillis) {
                return CompletableFuture.failedFuture(new TimeoutException("Timeout exceeded after " + timeoutMillis + " ms"));
            }

            return CompletableFuture.runAsync(
                            () -> {},
                            CompletableFuture.delayedExecutor(intervalMillis, TimeUnit.MILLISECONDS)
                    )
                    .thenCompose(ignored -> {
                        try {
                            return pollSnapshotStatus(token, snapshotId, startTime, timeoutMillis, intervalMillis);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }

    private CompletableFuture<ObjectNode> getSnapshotStatus(String token, String snapshotId) {
        String path = String.format("search/snapshot/%s/status", snapshotId);
        return httpUtils.sendGetRequest(token, CYODA_API_URL, path);
    }

    private CompletableFuture<ObjectNode> getSearchResult(
            String token,
            String snapshotId,
            int pageSize,
            int pageNumber
    ) {
        String path = String.format("search/snapshot/%s", snapshotId);
        Map<String, String> params = Map.of(
                "pageSize",
                String.valueOf(pageSize),
                "pageNumber",
                String.valueOf(pageNumber)
        );

        return httpUtils.sendGetRequest(token, CYODA_API_URL, path, params)
                .thenApply(response -> {
                    if (response != null) {
                        return response;
                    } else {
                        throw new RuntimeException("Get search result failed: response is null");
                    }
                });
    }
}
