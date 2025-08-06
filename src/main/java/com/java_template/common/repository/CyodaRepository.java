package com.java_template.common.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.grpc.client_v2.CloudEventBuilder;
import com.java_template.common.grpc.client_v2.CloudEventParser;
import com.java_template.common.repository.dto.Meta;
import io.cloudevents.v1.proto.CloudEvent;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.function.Function;
import java.util.stream.Stream;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.DataFormat;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityCreatePayload;
import org.cyoda.cloud.api.event.entity.EntityCreateRequest;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllRequest;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteRequest;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.cyoda.cloud.api.event.entity.EntityUpdatePayload;
import org.cyoda.cloud.api.event.entity.EntityUpdateRequest;
import org.cyoda.cloud.api.event.search.EntityGetAllRequest;
import org.cyoda.cloud.api.event.search.EntityGetRequest;
import org.cyoda.cloud.api.event.search.EntityResponse;
import org.cyoda.cloud.api.event.search.EntitySearchRequest;
import org.cyoda.cloud.api.event.search.EntitySnapshotSearchRequest;
import org.cyoda.cloud.api.event.search.EntitySnapshotSearchResponse;
import org.cyoda.cloud.api.event.search.SearchSnapshotStatus;
import org.cyoda.cloud.api.event.search.SnapshotGetRequest;
import org.cyoda.cloud.api.event.search.SnapshotGetStatusRequest;
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

@Repository
public class CyodaRepository implements CrudRepository {
    private final Logger logger = LoggerFactory.getLogger(CyodaRepository.class);
    private final ObjectMapper objectMapper;
    private final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub;
    private final CloudEventBuilder cloudEventBuilder;
    private final CloudEventParser cloudEventParser;

    private final String FORMAT = "JSON"; // or "XML"

    public CyodaRepository(
            final ObjectMapper objectMapper,
            final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub,
            final CloudEventBuilder cloudEventBuilder,
            final CloudEventParser cloudEventParser) {
        this.objectMapper = objectMapper;
        this.cloudEventsServiceBlockingStub = cloudEventsServiceBlockingStub;
        this.cloudEventBuilder = cloudEventBuilder;
        this.cloudEventParser = cloudEventParser;
    }

    @Override
    public CompletableFuture<ObjectNode> updateAll(Meta meta, List<Object> entities) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> count(Meta meta) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> delete(Meta meta, Object entity) {
        return null;
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
    public CompletableFuture<ObjectNode> findById(final UUID id) {
        return getById(id);
    }

    @Override
    public CompletableFuture<ArrayNode> findAllByCriteria(
            @NotNull final String modelName,
            final int modelVersion,
            @NotNull final Object criteria,
            final int pageSize,
            final int pageNumber,
            final boolean inMemory) {
        return inMemory
                ? findAllByConditionInMemory(modelName, modelVersion, pageNumber, criteria)
                : findAllByCondition(modelName, modelVersion, pageSize, pageNumber, criteria);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> save(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final JsonNode entity) {
        return saveNewEntities(entityModel, entityVersion, entity);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final JsonNode entities) {
        return saveNewEntities(entityModel, entityVersion, entities);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> update(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final UUID id,
            @NotNull final JsonNode entity) {
        return updateEntity(entityModel, entityVersion, id, entity);
    }

    @Override
    public Meta getMeta(String token, String entityModel, String entityVersion) {
        return new Meta(token, entityModel, entityVersion);
    }

    @Override
    public CompletableFuture<EntityDeleteResponse> deleteById(@NotNull final UUID id) {
        return deleteEntity(id);
    }

    @Override
    public CompletableFuture<EntityDeleteAllResponse> deleteAll(
            @NotNull final String modelName,
            final int modelVersion) {
        return deleteAllByModel(modelName, modelVersion);
    }

    @Override
    public CompletableFuture<ArrayNode> findAll(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime) {
        return getAllEntities(modelName, modelVersion, pageSize, pageNumber, pointInTime);
    }

    @Override
    public CompletableFuture<ObjectNode> findAllByKey(Meta meta, List<Object> keys) {
        return null;
    }

    @Override
    public CompletableFuture<ObjectNode> findByKey(Meta meta, Object key) {
        return null;
    }

    private CompletableFuture<ArrayNode> getAllEntities(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityGetAllRequest().withId(UUID.randomUUID().toString())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                        .withPageSize(pageSize)
                        .withPageNumber(pageNumber)
                        .withPointInTime(pointInTime),
                EntityResponse.class).thenApply(
                        events -> events.map(EntityResponse::getPayload)
                                .map(DataPayload::getData)
                                .map(it -> (ObjectNode) it)
                                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll));
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> CompletableFuture<Stream<RESPONSE_PAYLOAD_TYPE>> sendAndGetCollection(
            final Function<CloudEvent, Iterator<CloudEvent>> apiCall,
            final BaseEvent baseEvent,
            final Class<RESPONSE_PAYLOAD_TYPE> responsePayloadClass) {
        try {
            final var requestEvent = cloudEventBuilder.buildEvent(baseEvent);
            return CompletableFuture.supplyAsync(() -> requestAndGetOrThrow(apiCall, requestEvent))
                    .thenApply(response -> processCollection(Streams.stream(response), responsePayloadClass));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> Stream<RESPONSE_PAYLOAD_TYPE> processCollection(
            final Stream<CloudEvent> stream,
            final Class<RESPONSE_PAYLOAD_TYPE> payloadType) {
        return stream.filter(Objects::nonNull)
                .map(elm -> cloudEventParser.parseCloudEvent(elm, payloadType))
                .map(this::getOrNull)
                .filter(Objects::nonNull);
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> RESPONSE_PAYLOAD_TYPE getOrNull(
            final Optional<RESPONSE_PAYLOAD_TYPE> event) {
        return event.orElse(null);
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> CompletableFuture<RESPONSE_PAYLOAD_TYPE> sendAndGet(
            final Function<CloudEvent, CloudEvent> apiCall,
            final BaseEvent baseEvent,
            final Class<RESPONSE_PAYLOAD_TYPE> responsePayloadType) {
        try {
            final CloudEvent requestEvent = cloudEventBuilder.buildEvent(baseEvent);
            return CompletableFuture.supplyAsync(() -> requestAndGetOrThrow(apiCall, requestEvent))
                    .thenApply(response -> cloudEventParser.parseCloudEvent(response, responsePayloadType))
                    .thenApply(this::getOrNull);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private <RESPONSE_PAYLOAD_TYPE> RESPONSE_PAYLOAD_TYPE requestAndGetOrThrow(
            final Function<CloudEvent, RESPONSE_PAYLOAD_TYPE> apiCall,
            final CloudEvent requestEvent) {
        try {
            return apiCall.apply(requestEvent);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private CompletableFuture<ObjectNode> getById(final UUID entityId) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityGetRequest().withId(UUID.randomUUID().toString()).withEntityId(entityId),
                EntityResponse.class).thenApply(EntityResponse::getPayload).thenApply(DataPayload::getData)
                .thenApply(it -> (ObjectNode) it);
    }

    private CompletableFuture<EntityTransactionResponse> saveNewEntities(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final JsonNode entity) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityCreateRequest().withId(generateEventId())
                        .withDataFormat(DataFormat.JSON)
                        .withPayload(
                                new EntityCreatePayload()
                                        .withModel(new ModelSpec().withName(entityModel).withVersion(entityVersion))
                                        .withData(entity)),
                EntityTransactionResponse.class);

        // .thenApply(resp -> resp.getTransactionInfo().getEntityIds());

        // String path = String.format("entity/%s/%s/%s", FORMAT, meta.getEntityModel(),
        // meta.getEntityVersion());
        //
        // return httpUtils.sendPostRequest(meta.getToken(), CYODA_API_URL, path,
        // data).thenApply(response -> {
        // if (response != null) {
        // JsonNode jsonNode = response.get("json");
        //
        // if (jsonNode != null && jsonNode.isArray()) {
        // logger.info("Successfully saved new entities. Response: {}", response);
        // return (ArrayNode) jsonNode;
        // } else {
        // logger.error("Response does not contain a valid 'json' array. Response: {}",
        // response);
        // throw new RuntimeException("Response does not contain a valid 'json' array");
        // }
        // } else {
        // logger.error("Failed to save new entity. Response is null");
        // throw new RuntimeException("Failed to save new entity: Response is null");
        // }
        // });
    }

    private CompletableFuture<EntityTransactionResponse> updateEntity(
            @NotNull final String entityModel,
            final int entityVersion,
            @NotNull final UUID id,
            @NotNull final JsonNode entity) {
        return sendAndGet(cloudEventsServiceBlockingStub::entityManage,
                new EntityUpdateRequest().withId(generateEventId()).withDataFormat(DataFormat.JSON).withPayload(
                        new EntityUpdatePayload().withEntityId(id).withData(entity).withTransition("update")),
                EntityTransactionResponse.class);

        // String path = String.format("entity/%s/%s/%s", FORMAT, id,
        // meta.getUpdateTransition());
        // return httpUtils.sendPutRequest(meta.getToken(), CYODA_API_URL, path, entity)
        // .thenApply(response -> (ObjectNode) response.get("json"));
    }

    private CompletableFuture<EntityDeleteResponse> deleteEntity(@NotNull final UUID id) {
        return sendAndGet(cloudEventsServiceBlockingStub::entityManage,
                new EntityDeleteRequest().withId(generateEventId()).withEntityId(id), EntityDeleteResponse.class);

        // String path = String.format("entity/%s", id);
        //
        // return httpUtils.sendDeleteRequest(meta.getToken(), CYODA_API_URL, path)
        // .thenApply(response -> (ObjectNode) response.get("json"));
    }

    private CompletableFuture<EntityDeleteAllResponse> deleteAllByModel(
            @NotNull final String modelName,
            final int modelVersion) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityDeleteAllRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion)),
                EntityDeleteAllResponse.class);
        // String path = String.format("entity/%s/%s", meta.getEntityModel(),
        // meta.getEntityVersion());
        //
        // return httpUtils.sendDeleteRequest(meta.getToken(), CYODA_API_URL, path)
        // .thenApply(response -> (ArrayNode) response.get("json"));
    }

    private static final int SNAPSHOT_CREATION_AWAIT_LIMIT_MS = 10_000;
    private static final int SNAPSHOT_CREATION_POLL_INTERVAL_MS = 500;

    private CompletableFuture<ArrayNode> findAllByCondition(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            final Object condition) {
        return createSnapshotSearch(modelName, modelVersion, (GroupCondition) condition).thenCompose(snapshotId -> {
            if (snapshotId == null) {
                logger.error("Snapshot ID not found in response");
                return CompletableFuture.completedFuture(null);
            }
            try {
                return waitForSearchCompletion(
                        snapshotId,
                        SNAPSHOT_CREATION_AWAIT_LIMIT_MS,
                        SNAPSHOT_CREATION_POLL_INTERVAL_MS).thenApply(it -> snapshotId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).thenCompose(snapshotId -> getSearchResult(snapshotId, pageSize, pageNumber)).exceptionally(ex -> {
            final var cause = ex instanceof CompletionException ? ex.getCause() : ex;
            if (cause instanceof ResponseStatusException rsEx && rsEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.warn("Model not found, returning empty array: {}", rsEx.getReason());
                return objectMapper.createArrayNode();
            }
            throw new CompletionException("Unhandled error", cause);
        });
    }

    private CompletableFuture<ArrayNode> findAllByConditionInMemory(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final Object condition) {
        return searchEntitiesInMemory(
                modelName,
                modelVersion,
                pageSize,
                (GroupCondition) condition).thenApply(response -> {
                    final var jsonNode = response.get("json");
                    if (jsonNode != null && jsonNode.isArray()) {
                        final var results = objectMapper.createArrayNode();
                        for (final var item : jsonNode) {
                            if (item.has("data")) {
                                results.add(item.get("data"));
                            }
                        }
                        return results;
                    } else {
                        logger.warn("Expected an ArrayNode under 'json' for in-memory search, but got: {}", jsonNode);
                        return objectMapper.createArrayNode();
                    }
                }).exceptionally(ex -> {
                    final var cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    // if (cause instanceof ResponseStatusException rsEx && rsEx.getStatusCode() ==
                    // HttpStatus.NOT_FOUND) {
                    if (isNotFound()) {
                        logger.warn("Model not found for in-memory search, returning empty array");
                        return objectMapper.createArrayNode();
                    }
                    throw new CompletionException("Unhandled error in in-memory search", cause);
                });
    }

    private boolean isNotFound() {
        return false;
    }

    private String generateEventId() {
        return UUID.randomUUID().toString();
    }

    private CompletableFuture<ArrayNode> searchEntitiesInMemory(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final GroupCondition condition) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entitySearchCollection,
                new EntitySearchRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                        .withLimit(pageSize)
                        .withCondition(condition),
                EntityResponse.class).thenApply(
                        events -> events.map(EntityResponse::getPayload)
                                .map(DataPayload::getData)
                                .map(it -> (ObjectNode) it)
                                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll));
        // String searchPath = String.format("search/%s/%s", meta.getEntityModel(),
        // meta.getEntityVersion());
        //
        // // Create search request with parameters
        // ObjectNode searchRequest = objectMapper.createObjectNode();
        // try {
        // // Convert condition to JsonNode if it's not already
        // JsonNode conditionNode;
        // if (condition instanceof JsonNode) {
        // conditionNode = (JsonNode) condition;
        // } else {
        // conditionNode = objectMapper.valueToTree(condition);
        // }
        //
        // // Copy the condition structure
        // searchRequest.setAll((ObjectNode) conditionNode);
        //
        // // Add query parameters for in-memory search
        // Map<String, String> queryParams = new HashMap<>();
        // queryParams.put("limit", String.valueOf(1000));
        // queryParams.put("timeoutMillis", String.valueOf(60000));
        //
        // logger.info(
        // "Performing in-memory search for entity: {}/{} with condition: {}",
        // meta.getEntityModel(),
        // meta.getEntityVersion(),
        // searchRequest
        // );
        //
        // return httpUtils.sendPostRequest(meta.getToken(), CYODA_API_URL, searchPath,
        // searchRequest, queryParams);
        //
        // } catch (Exception e) {
        // logger.error("Error preparing in-memory search request", e);
        // return CompletableFuture.failedFuture(e);
        // }
    }

    private CompletableFuture<UUID> createSnapshotSearch(
            final String entityModel,
            final int entityVersion,
            final GroupCondition condition) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entitySearch,
                new EntitySnapshotSearchRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(entityModel).withVersion(entityVersion))
                        .withCondition(condition),
                EntitySnapshotSearchResponse.class).thenApply(EntitySnapshotSearchResponse::getStatus)
                .thenApply(SearchSnapshotStatus::getSnapshotId);

        // String searchPath = String.format("search/snapshot/%s/%s", entityModel,
        // entityVersion);
        // return httpUtils.sendPostRequest(token, CYODA_API_URL, searchPath, condition)
        // .thenApply(response -> response.get("json").asText());
    }

    private CompletableFuture<SearchSnapshotStatus.Status> waitForSearchCompletion(
            @NotNull final UUID snapshotId,
            final long awaitLimitMillis,
            final long intervalMillis) throws IOException {
        final var startTime = System.currentTimeMillis();
        return pollSnapshotStatus(snapshotId, startTime, awaitLimitMillis, intervalMillis);
    }

    private CompletableFuture<SearchSnapshotStatus.Status> pollSnapshotStatus(
            @NotNull final UUID snapshotId,
            final long startTime,
            final long awaitLimitMillis,
            final long intervalMillis) throws IOException {
        return getSnapshotStatus(snapshotId).thenCompose(snapshotStatus -> {
            if (SearchSnapshotStatus.Status.SUCCESSFUL.equals(snapshotStatus)) {
                return CompletableFuture.completedFuture(snapshotStatus);
            } else if (!SearchSnapshotStatus.Status.RUNNING.equals(snapshotStatus)) {
                return CompletableFuture
                        .failedFuture(new RuntimeException("Snapshot search failed: " + snapshotStatus));
            }

            final var elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > awaitLimitMillis) {
                return CompletableFuture
                        .failedFuture(new TimeoutException("Timeout exceeded after " + awaitLimitMillis + " ms"));
            }

            return CompletableFuture.runAsync(
                    () -> {
                    },
                    CompletableFuture.delayedExecutor(intervalMillis, TimeUnit.MILLISECONDS)).thenCompose(ignored -> {
                        try {
                            return pollSnapshotStatus(snapshotId, startTime, awaitLimitMillis, intervalMillis);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        });
    }

    private CompletableFuture<SearchSnapshotStatus.Status> getSnapshotStatus(final UUID snapshotId) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entitySearch,
                new SnapshotGetStatusRequest().withId(generateEventId()).withSnapshotId(snapshotId),
                EntitySnapshotSearchResponse.class).thenApply(EntitySnapshotSearchResponse::getStatus)
                .thenApply(SearchSnapshotStatus::getStatus);
        // String path = String.format("search/snapshot/%s/status", snapshotId);
        // return httpUtils.sendGetRequest(token, CYODA_API_URL, path);
    }

    private CompletableFuture<ArrayNode> getSearchResult(
            @NotNull final UUID snapshotId,
            final int pageSize,
            final int pageNumber) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entitySearchCollection,
                new SnapshotGetRequest().withId(generateEventId())
                        .withSnapshotId(snapshotId)
                        .withPageSize(pageSize)
                        .withPageNumber(pageNumber),
                EntityResponse.class).thenApply(
                        events -> events.map(EntityResponse::getPayload)
                                .map(DataPayload::getData)
                                .map(it -> (ObjectNode) it)
                                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll));

        // String path = String.format("search/snapshot/%s", snapshotId);
        // Map<String, String> params = Map.of(
        // "pageSize",
        // String.valueOf(pageSize),
        // "pageNumber",
        // String.valueOf(pageNumber)
        // );
        //
        // return httpUtils.sendGetRequest(token, CYODA_API_URL, path,
        // params).thenApply(response -> {
        // if (response != null) {
        // return response;
        // } else {
        // throw new RuntimeException("Get search result failed: response is null");
        // }
        // });
    }
}
