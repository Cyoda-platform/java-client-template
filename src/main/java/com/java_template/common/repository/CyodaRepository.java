package com.java_template.common.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.auth.Authentication;
import com.java_template.common.grpc.client.event_handling.CloudEventBuilder;
import com.java_template.common.grpc.client.event_handling.CloudEventParser;
import com.java_template.common.util.HttpUtils;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.*;
import org.cyoda.cloud.api.event.search.*;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.java_template.common.config.Config.CYODA_API_URL;
import static com.java_template.common.config.Config.GRPC_COMMUNICATION_DATA_FORMAT;


/**
 * ABOUTME: Concrete implementation of CrudRepository providing entity CRUD operations
 * through gRPC communication with the Cyoda platform backend services.
 */
@Repository
public class CyodaRepository implements CrudRepository {
    private static final int SNAPSHOT_CREATION_AWAIT_LIMIT_MS = 10_000;
    private static final int SNAPSHOT_CREATION_POLL_INTERVAL_MS = 500;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ObjectMapper objectMapper;
    private final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub;
    private final CloudEventBuilder cloudEventBuilder;
    private final CloudEventParser cloudEventParser;
    private final HttpUtils httpUtils;
    private final Authentication authentication;

    public CyodaRepository(
            final ObjectMapper objectMapper,
            final CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub,
            final CloudEventBuilder cloudEventBuilder,
            final CloudEventParser cloudEventParser,
            final HttpUtils httpUtils,
            final Authentication authentication
    ) {
        this.objectMapper = objectMapper;
        this.cloudEventsServiceBlockingStub = cloudEventsServiceBlockingStub;
        this.cloudEventBuilder = cloudEventBuilder;
        this.cloudEventParser = cloudEventParser;
        this.httpUtils = httpUtils;
        this.authentication = authentication;
    }

    @Override
    public CompletableFuture<DataPayload> findById(final UUID id) {
        return getById(id);
    }

    private CompletableFuture<DataPayload> getById(final UUID entityId) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityGetRequest().withId(UUID.randomUUID().toString()).withEntityId(entityId),
                EntityResponse.class
        ).thenApply(EntityResponse::getPayload);
    }

    @Override
    public CompletableFuture<List<DataPayload>> findAllByCriteria(
            @NotNull final ModelSpec modelSpec,
            @NotNull final GroupCondition condition,
            final int pageSize,
            final int pageNumber,
            final boolean inMemory
    ) {
        return inMemory
                ? findAllByConditionInMemory(modelSpec, pageSize, condition)
                : findAllByCondition(modelSpec, pageSize, pageNumber, condition);
    }

    private CompletableFuture<List<DataPayload>> findAllByCondition(
            @NotNull final ModelSpec modelSpec,
            final int pageSize,
            final int pageNumber,
            @NotNull final GroupCondition condition
    ) {
        return createSnapshotSearch(modelSpec, condition).thenComposeAsync(snapshotInfo -> {
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
                }).thenCompose(snapshotId -> getSearchResult(snapshotId, pageSize, pageNumber))
                .exceptionally(this::handleNotFoundOrThrow);
    }

    private CompletableFuture<List<DataPayload>> findAllByConditionInMemory(
            @NotNull final ModelSpec modelSpec,
            final int pageSize,
            @NotNull final GroupCondition condition
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entitySearchCollection,
                new EntitySearchRequest().withId(generateEventId())
                        .withModel(modelSpec)
                        .withLimit(pageSize)
                        .withCondition(condition),
                EntityResponse.class
        ).thenApply(entities -> entities.map(EntityResponse::getPayload).toList())
                .exceptionally(this::handleNotFoundOrThrow);
    }

    @Override
    public CompletableFuture<List<DataPayload>> findAll(
            @NotNull final ModelSpec modelSpec,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime
    ) {
        return getAllEntities(modelSpec, pageSize, pageNumber, pointInTime);
    }

    private CompletableFuture<List<DataPayload>> getAllEntities(
            @NotNull final ModelSpec modelSpec,
            final int pageSize,
            final int pageNumber,
            @Nullable final Date pointInTime
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityGetAllRequest().withId(UUID.randomUUID().toString())
                        .withModel(modelSpec)
                        .withPageSize(pageSize)
                        .withPageNumber(pageNumber)
                        .withPointInTime(pointInTime),
                EntityResponse.class
        ).thenApply(entities -> entities.map(EntityResponse::getPayload).toList());
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> save(
            @NotNull final ModelSpec modelSpec,
            @NotNull final ENTITY_TYPE entity
    ) {
        return saveNewEntities(modelSpec, entity);
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> saveAll(
            @NotNull final ModelSpec modelSpec,
            @NotNull final Collection<ENTITY_TYPE> entities
    ) {
        return saveNewEntities(modelSpec, entities);
    }

    @Override
    public CompletableFuture<EntityTransitionResponse> applyTransition(
            @NotNull final UUID entityId,
            @NotNull final String transitionName
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityTransitionRequest().withId(generateEventId())
                        .withEntityId(entityId)
                        .withTransition(transitionName),
                EntityTransitionResponse.class
        );
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<EntityTransactionResponse> update(
            @NotNull final UUID id,
            @NotNull final ENTITY_TYPE entity,
            @Nullable final String transition
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityUpdateRequest().withId(generateEventId())
                        .withDataFormat(GRPC_COMMUNICATION_DATA_FORMAT)
                        .withPayload(
                                new EntityUpdatePayload().withEntityId(id)
                                        .withData(objectMapper.valueToTree(entity))
                                        .withTransition(transition)
                        ),
                EntityTransactionResponse.class
        );
    }

    @Override
    public <ENTITY_TYPE> CompletableFuture<List<EntityTransactionResponse>> updateAll(
            @NotNull final Collection<ENTITY_TYPE> entities,
            @Nullable final String transition
    ) {
        final var entitiesByIds = entities.stream()
                .map(objectMapper::valueToTree)
                .map(entity -> (JsonNode) entity)
                .collect(Collectors.toMap(
                                entity -> UUID.fromString(entity.get("id").asText()),
                                entity -> entity
                        )
                );

        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityUpdateCollectionRequest().withId(generateEventId())
                        .withDataFormat(GRPC_COMMUNICATION_DATA_FORMAT)
                        .withPayloads(entitiesByIds.entrySet()
                                .stream()
                                .map(entity -> new EntityUpdatePayload().withTransition(transition)
                                        .withEntityId(entity.getKey())
                                        .withData(entity.getValue()))
                                .toList()),
                EntityTransactionResponse.class
        ).thenApply(Stream::toList);
    }

    @Override
    public CompletableFuture<EntityDeleteResponse> deleteById(@NotNull final UUID id) {
        return deleteEntity(id);
    }

    @Override
    public CompletableFuture<List<EntityDeleteAllResponse>> deleteAll(
            @NotNull final ModelSpec modelSpec
    ) {
        return deleteAllByModel(modelSpec);
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

    private <PAYLOAD_TYPE> CompletableFuture<EntityTransactionResponse> saveNewEntities(
            @NotNull final ModelSpec modelSpec,
            @NotNull final PAYLOAD_TYPE entities
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityCreateRequest().withId(generateEventId())
                        .withDataFormat(GRPC_COMMUNICATION_DATA_FORMAT)
                        .withPayload(new EntityCreatePayload().withData(objectMapper.valueToTree(entities))
                                .withModel(modelSpec)
                        ),
                EntityTransactionResponse.class
        );
    }

    private CompletableFuture<EntityDeleteResponse> deleteEntity(@NotNull final UUID id) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entityManage,
                new EntityDeleteRequest().withId(generateEventId()).withEntityId(id),
                EntityDeleteResponse.class
        );
    }

    private CompletableFuture<List<EntityDeleteAllResponse>> deleteAllByModel(
            @NotNull final ModelSpec modelSpec
    ) {
        return sendAndGetCollection(
                cloudEventsServiceBlockingStub::entityManageCollection,
                new EntityDeleteAllRequest().withId(generateEventId())
                        .withModel(modelSpec),
                EntityDeleteAllResponse.class
        ).thenApply(Stream::toList);
    }

    private String generateEventId() {
        return UUID.randomUUID().toString();
    }

    private CompletableFuture<SearchSnapshotStatus> createSnapshotSearch(
            final ModelSpec modelSpec,
            final GroupCondition condition
    ) {
        return sendAndGet(
                cloudEventsServiceBlockingStub::entitySearch,
                new EntitySnapshotSearchRequest().withId(generateEventId())
                        .withModel(modelSpec)
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
            }
            if (!SearchSnapshotStatus.Status.RUNNING.equals(snapshotStatus)) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Snapshot search failed: " + snapshotStatus)
                );
            }

            final var elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > awaitLimitMillis) {
                return CompletableFuture.failedFuture(
                        new TimeoutException("Timeout exceeded after " + awaitLimitMillis + " ms"));
            }

            return CompletableFuture.runAsync(
                    () -> {},
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

    private CompletableFuture<List<DataPayload>> getSearchResult(
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
        ).thenApply(entities -> entities
                .map(EntityResponse::getPayload)
                .toList()
        );
    }

    private <ENTITY_TYPE> List<ENTITY_TYPE> handleNotFoundOrThrow(final Throwable exception) {
        if (isNotFound(exception)) {
            logger.warn("Not found happens", exception);
            return Collections.emptyList();
        }
        final var cause = exception instanceof CompletionException ? exception.getCause() : exception;
        throw new CompletionException("Unhandled error", cause);
    }

    private boolean isNotFound(final Throwable exception) {
        return exception instanceof StatusRuntimeException ex && ex.getStatus().getCode().equals(Status.Code.NOT_FOUND);
    }

    @Override
    public CompletableFuture<Long> getEntityCount(@NotNull final ModelSpec modelSpec) {
        String token = authentication.getAccessToken().getTokenValue();
        return httpUtils.sendGetRequest(token, CYODA_API_URL, "entity/stats")
                .thenApply(response -> {
                    JsonNode jsonNode = response.get("json");
                    if (jsonNode == null || !jsonNode.isArray()) {
                        logger.warn("Invalid response from /entity/stats: {}", response);
                        return 0L;
                    }

                    // Find the matching model in the stats array
                    for (JsonNode stat : jsonNode) {
                        String modelName = stat.path("modelName").asText();
                        int modelVersion = stat.path("modelVersion").asInt();

                        if (modelSpec.getName().equals(modelName) &&
                            modelSpec.getVersion().equals(modelVersion)) {
                            return stat.path("count").asLong(0L);
                        }
                    }

                    // Model not found in stats, return 0
                    return 0L;
                })
                .exceptionally(ex -> {
                    logger.error("Failed to get entity count for model {}/{}: {}",
                            modelSpec.getName(), modelSpec.getVersion(), ex.getMessage());
                    return 0L;
                });
    }
}