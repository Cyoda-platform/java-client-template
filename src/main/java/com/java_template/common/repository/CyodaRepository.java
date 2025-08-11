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
import java.util.stream.Collectors;
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
import org.cyoda.cloud.api.event.entity.EntityUpdateCollectionRequest;
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
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Repository
public class CyodaRepository implements CrudRepository {
    private final static String UPDATE_TRANSITION = "update";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ObjectMapper objectMapper;
    private final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub;
    private final CloudEventBuilder cloudEventBuilder;
    private final CloudEventParser cloudEventParser;

    private final String FORMAT = "JSON"; // or "XML"

    public CyodaRepository(
            final ObjectMapper objectMapper,
            final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub,
            final CloudEventBuilder cloudEventBuilder,
            final CloudEventParser cloudEventParser
    ) {
        this.objectMapper = objectMapper;
        this.cloudEventsServiceBlockingStub = cloudEventsServiceBlockingStub;
        this.cloudEventBuilder = cloudEventBuilder;
        this.cloudEventParser = cloudEventParser;
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
            @NotNull final GroupCondition condition,
            final int pageSize,
            final int pageNumber,
            final boolean inMemory
    ) {
        return inMemory
                ? findAllByConditionInMemory(modelName, modelVersion, pageSize, condition)
                : findAllByCondition(modelName, modelVersion, pageSize, pageNumber, condition);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> save(
            @NotNull final String modelName,
            final int modelVersion,
            @NotNull final JsonNode entity
    ) {
        return saveNewEntities(modelName, modelVersion, entity);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull final String modelName,
            final int modelVersion,
            @NotNull final JsonNode entities
    ) {
        return saveNewEntities(modelName, modelVersion, entities);
    }

    @Override
    public CompletableFuture<EntityTransactionResponse> update(
            @NotNull final UUID id,
            @NotNull final JsonNode entity
    ) {
        return updateEntity(id, entity);
    }

    @Override
    public CompletableFuture<List<EntityTransactionResponse>> updateAll(@NotNull final Collection<Object> entities) {
        return updateEntities(entities);
    }

    @Override
    public CompletableFuture<EntityDeleteResponse> deleteById(@NotNull final UUID id) {
        return deleteEntity(id);
    }

    @Override
    public CompletableFuture<Integer> deleteAll(
            @NotNull final String modelName,
            final int modelVersion
    ) {
        return deleteAllByModel(modelName, modelVersion);
    }

    @Override
    public CompletableFuture<ArrayNode> findAll(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime
    ) {
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
            @Nullable final Date pointInTime
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityGetAllRequest().withId(UUID.randomUUID().toString())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                        .withPageSize(pageSize)
                        .withPageNumber(pageNumber)
                        .withPointInTime(pointInTime),
                EntityResponse.class
        ).thenApply(entities -> entities.map(EntityResponse::getPayload)
                .map(DataPayload::getData)
                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll)
        );
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> CompletableFuture<Stream<RESPONSE_PAYLOAD_TYPE>> sendAndGetCollection(
            final Function<CloudEvent, Iterator<CloudEvent>> apiCall,
            final BaseEvent baseEvent,
            final Class<RESPONSE_PAYLOAD_TYPE> responsePayloadClass
    ) {
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
            final Class<RESPONSE_PAYLOAD_TYPE> payloadType
    ) {
        return stream.filter(Objects::nonNull)
                .map(elm -> cloudEventParser.parseCloudEvent(elm, payloadType))
                .map(this::getOrNull)
                .filter(Objects::nonNull);
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> RESPONSE_PAYLOAD_TYPE getOrNull(
            final Optional<RESPONSE_PAYLOAD_TYPE> event
    ) {
        return event.orElse(null);
    }

    private <RESPONSE_PAYLOAD_TYPE extends BaseEvent> CompletableFuture<RESPONSE_PAYLOAD_TYPE> sendAndGet(
            final Function<CloudEvent, CloudEvent> apiCall,
            final BaseEvent baseEvent,
            final Class<RESPONSE_PAYLOAD_TYPE> responsePayloadType
    ) {
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
            final CloudEvent requestEvent
    ) {
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
                EntityResponse.class
        ).thenApply(EntityResponse::getPayload)
                .thenApply(DataPayload::getData)
                .thenApply(it -> (ObjectNode) it);
    }

    private CompletableFuture<EntityTransactionResponse> saveNewEntities(
            @NotNull final String modelName,
            final int modelVersion,
            @NotNull final JsonNode entity
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityCreateRequest().withId(generateEventId())
                        .withDataFormat(DataFormat.JSON)
                        .withPayload(
                                new EntityCreatePayload()
                                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                                        .withData(entity)),
                EntityTransactionResponse.class
        );
    }

    private CompletableFuture<EntityTransactionResponse> updateEntity(
            @NotNull final UUID id,
            @NotNull final JsonNode entity
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityUpdateRequest().withId(generateEventId()).withDataFormat(DataFormat.JSON).withPayload(
                        new EntityUpdatePayload().withEntityId(id).withData(entity).withTransition(UPDATE_TRANSITION)),
                EntityTransactionResponse.class
        );
    }

    private CompletableFuture<List<EntityTransactionResponse>> updateEntities(
            @NotNull final Collection<Object> entities
    ) {
        final var entitiesByIds = entities.stream()
                .map(objectMapper::valueToTree)
                .map(entity -> ((JsonNode) entity))
                .collect(Collectors.toMap(
                                entity -> UUID.fromString(entity.get("id").asText()),
                                entity -> entity
                        )
                );

        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityUpdateCollectionRequest().withId(generateEventId())
                        .withDataFormat(DataFormat.JSON)
                        .withPayloads(entitiesByIds.entrySet()
                                .stream()
                                .map(entity -> new EntityUpdatePayload().withTransition(UPDATE_TRANSITION)
                                        .withEntityId(entity.getKey())
                                        .withData(entity.getValue()))
                                .toList()),
                EntityTransactionResponse.class
        ).thenApply(Stream::toList);
    }

    private CompletableFuture<EntityDeleteResponse> deleteEntity(@NotNull final UUID id) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityDeleteRequest().withId(generateEventId()).withEntityId(id),
                EntityDeleteResponse.class
        );
    }

    private CompletableFuture<Integer> deleteAllByModel(
            @NotNull final String modelName,
            final int modelVersion
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityDeleteAllRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion)),
                EntityDeleteAllResponse.class
        ).thenApply(events -> events.map(EntityDeleteAllResponse::getNumDeleted).reduce(0, Integer::sum));
    }

    private static final int SNAPSHOT_CREATION_AWAIT_LIMIT_MS = 10_000;
    private static final int SNAPSHOT_CREATION_POLL_INTERVAL_MS = 500;

    private CompletableFuture<ArrayNode> findAllByCondition(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            final int pageNumber,
            @NotNull final GroupCondition condition
    ) {
        return createSnapshotSearch(modelName, modelVersion, condition).thenCompose(snapshotInfo -> {
            if (snapshotInfo.getSnapshotId() == null) {
                logger.error("Snapshot ID not found in response");
                return CompletableFuture.completedFuture(null);
            }

            // NOTE: To avoid redundant polling, if snapshot is already done
            if (SearchSnapshotStatus.Status.SUCCESSFUL.equals(snapshotInfo.getStatus())) {
                return CompletableFuture.completedFuture(snapshotInfo.getSnapshotId());
            }

            try {
                return waitForSearchCompletion(
                        snapshotInfo.getSnapshotId(),
                        SNAPSHOT_CREATION_AWAIT_LIMIT_MS,
                        SNAPSHOT_CREATION_POLL_INTERVAL_MS
                ).thenApply(it -> snapshotInfo.getSnapshotId());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).thenCompose(snapshotId -> getSearchResult(snapshotId, pageSize, pageNumber)).exceptionally(ex -> {
            final var cause = ex instanceof CompletionException ? ex.getCause() : ex;
            if (isNotFound(ex)) {
                logger.warn("Model not found, returning empty array");
                return objectMapper.createArrayNode();
            }
            throw new CompletionException("Unhandled error", cause);
        });
    }

    private CompletableFuture<ArrayNode> findAllByConditionInMemory(
            @NotNull final String modelName,
            final int modelVersion,
            final int pageSize,
            @NotNull final GroupCondition condition
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entitySearchCollection,
                new EntitySearchRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                        .withLimit(pageSize)
                        .withCondition(condition),
                EntityResponse.class
        ).thenApply(entities -> entities.map(EntityResponse::getPayload)
                .map(DataPayload::getData)
                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll)
        ).exceptionally(ex -> {
            final var cause = ex instanceof CompletionException ? ex.getCause() : ex;
            if (isNotFound(ex)) {
                logger.warn("Model not found for in-memory search, returning empty array");
                return objectMapper.createArrayNode();
            }
            throw new CompletionException("Unhandled error in in-memory search", cause);
        });
    }

    private boolean isNotFound(final Throwable exception) {
        return false;
    }

    private String generateEventId() {
        return UUID.randomUUID().toString();
    }

    private CompletableFuture<SearchSnapshotStatus> createSnapshotSearch(
            final String modelName,
            final int modelVersion,
            final GroupCondition condition
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entitySearch,
                new EntitySnapshotSearchRequest().withId(generateEventId())
                        .withModel(new ModelSpec().withName(modelName).withVersion(modelVersion))
                        .withCondition(condition),
                EntitySnapshotSearchResponse.class
        ).thenApply(EntitySnapshotSearchResponse::getStatus);
    }

    private CompletableFuture<SearchSnapshotStatus.Status> waitForSearchCompletion(
            @NotNull final UUID snapshotId,
            final long awaitLimitMillis,
            final long intervalMillis
    ) throws IOException {
        final var startTime = System.currentTimeMillis();
        return pollSnapshotStatus(snapshotId, startTime, awaitLimitMillis, intervalMillis);
    }

    private CompletableFuture<SearchSnapshotStatus.Status> pollSnapshotStatus(
            @NotNull final UUID snapshotId,
            final long startTime,
            final long awaitLimitMillis,
            final long intervalMillis
    ) throws IOException {
        logger.debug("Polling snapshot: {}", snapshotId);
        return getSnapshotStatus(snapshotId).thenCompose(snapshotStatus -> {
            if (SearchSnapshotStatus.Status.SUCCESSFUL.equals(snapshotStatus)) {
                logger.debug("Snapshot is ready!");
                return CompletableFuture.completedFuture(snapshotStatus);
            } else if (!SearchSnapshotStatus.Status.RUNNING.equals(snapshotStatus)) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Snapshot search failed: " + snapshotStatus));
            }

            final var elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > awaitLimitMillis) {
                return CompletableFuture.failedFuture(
                        new TimeoutException("Timeout exceeded after " + awaitLimitMillis + " ms"));
            }

            return CompletableFuture.runAsync(
                    () -> {
                    },
                    CompletableFuture.delayedExecutor(intervalMillis, TimeUnit.MILLISECONDS)
            ).thenCompose(ignored -> {
                try {
                    return pollSnapshotStatus(snapshotId, startTime, awaitLimitMillis, intervalMillis);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private CompletableFuture<SearchSnapshotStatus.Status> getSnapshotStatus(@NotNull final UUID snapshotId) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entitySearch,
                new SnapshotGetStatusRequest().withId(generateEventId()).withSnapshotId(snapshotId),
                EntitySnapshotSearchResponse.class
        ).thenApply(EntitySnapshotSearchResponse::getStatus)
                .thenApply(SearchSnapshotStatus::getStatus);
    }

    private CompletableFuture<ArrayNode> getSearchResult(
            @NotNull final UUID snapshotId,
            final int pageSize,
            final int pageNumber
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entitySearchCollection,
                new SnapshotGetRequest().withId(generateEventId())
                        .withSnapshotId(snapshotId)
                        .withPageSize(pageSize)
                        .withPageNumber(pageNumber),
                EntityResponse.class
        ).thenApply(entities -> entities.map(EntityResponse::getPayload)
                .map(DataPayload::getData)
                .collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll));
    }
}