package com.java_template.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionInfo;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EntityServiceImpl focusing on repository interactions,
 * error handling, and business logic validation.
 */
@ExtendWith(MockitoExtension.class)
class EntityServiceImplTest {

    // Test constants
    private static final String MODEL_NAME = "test-entity";
    private static final int MODEL_VERSION = 1;
    private static final String BUSINESS_ID_FIELD = "name";
    private static final String TRANSITION_ACTIVATE = "ACTIVATE";

    @Mock
    private CrudRepository repository;

    private ObjectMapper objectMapper;
    private EntityServiceImpl entityService;
    private UUID testEntityId;
    private UUID testEntityId2;
    private TestEntity testEntity;
    private TestEntity testEntity2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entityService = new EntityServiceImpl(repository, objectMapper);
        testEntityId = UUID.randomUUID();
        testEntityId2 = UUID.randomUUID();
        testEntity = new TestEntity(123L, "Test Entity", "ACTIVE");
        testEntity2 = new TestEntity(456L, "Test Entity 2", "INACTIVE");
    }

    // ========================================
    // TEST DATA BUILDERS
    // ========================================

    private DataPayload createTestDataPayload(TestEntity entity, UUID entityId, String state) {
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(entity));

        // Create EntityMetadata using the actual Cyoda class
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(entityId);
        metadata.setState(state);
        metadata.setCreationDate(new Date());
        payload.setMeta(objectMapper.valueToTree(metadata));

        return payload;
    }

    private DataPayload createTestDataPayload(TestEntity entity, UUID entityId) {
        return createTestDataPayload(entity, entityId, entity.getStatus());
    }

    private EntityTransactionResponse createTransactionResponse(UUID entityId) {
        EntityTransactionResponse response = new EntityTransactionResponse();
        EntityTransactionInfo transactionInfo = new EntityTransactionInfo();
        transactionInfo.setEntityIds(List.of(entityId));
        transactionInfo.setTransactionId(UUID.randomUUID());
        response.setTransactionInfo(transactionInfo);
        return response;
    }

    private EntityDeleteResponse createDeleteResponse(UUID entityId) {
        EntityDeleteResponse response = new EntityDeleteResponse();
        response.setEntityId(entityId);
        return response;
    }

    private EntityDeleteAllResponse createDeleteAllResponse(int numDeleted) {
        EntityDeleteAllResponse response = new EntityDeleteAllResponse();
        response.setNumDeleted(numDeleted);
        return response;
    }

    private GroupCondition createActiveStatusCondition() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleCondition simpleCondition = new SimpleCondition()
                .withJsonPath("$.status")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree("ACTIVE"));

        return new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(simpleCondition));
    }

    private ModelSpec createTestModelSpec() {
        return new ModelSpec().withName(MODEL_NAME).withVersion(MODEL_VERSION);
    }

    private void assertEntityMatches(TestEntity actual, TestEntity expected) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getStatus(), actual.getStatus());
    }

    private void assertMetadata(EntityWithMetadata<?> result, UUID expectedId, String expectedState) {
        assertEquals(expectedId, result.metadata().getId());
        assertEquals(expectedState, result.metadata().getState());
    }

    private void assertRepositoryFailure(Runnable operation, String expectedCauseMessage) {
        RuntimeException exception = assertThrows(RuntimeException.class, operation::run);
        // EntityService doesn't wrap exceptions, so the repository exception bubbles up directly
        assertTrue(exception.getMessage().contains(expectedCauseMessage),
                "Expected message to contain '" + expectedCauseMessage + "' but was: " + exception.getMessage());
    }

    // Test entity class
    @Setter
    @Getter
    static class TestEntity implements CyodaEntity {
        private Long id;
        private String name;
        private String status;

        @SuppressWarnings("unused") // Used by Jackson
        public TestEntity() {}

        public TestEntity(Long id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }

        @Override
        public OperationSpecification getModelKey() {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName(MODEL_NAME);
            modelSpec.setVersion(MODEL_VERSION);
            return new OperationSpecification.Entity(modelSpec, MODEL_NAME);
        }

        @Override
        public boolean isValid(EntityMetadata metadata) {
            return id != null && name != null && !name.trim().isEmpty();
        }
    }

    // ========================================
    // REPOSITORY FAILURE TESTS
    // ========================================

    @Test
    @DisplayName("getById should handle repository failure gracefully")
    void testGetByIdRepositoryFailure() {
        when(repository.findById(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Repository error")));

        assertRepositoryFailure(() -> entityService.getById(testEntityId, createTestModelSpec(), TestEntity.class), "Repository error");
        verify(repository).findById(eq(testEntityId), isNull());
    }

    @Test
    @DisplayName("deleteById should handle repository failure gracefully")
    void testDeleteByIdRepositoryFailure() {
        when(repository.deleteById(testEntityId))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete failed")));

        assertRepositoryFailure(() -> entityService.deleteById(testEntityId), "Delete failed");
        verify(repository).deleteById(testEntityId);
    }

    @Test
    @DisplayName("save should handle repository failure gracefully")
    void testCreateRepositoryFailure() {
        when(repository.save(eq(createTestModelSpec()), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        assertRepositoryFailure(() -> entityService.create(testEntity), "Save failed");
        verify(repository).save(eq(createTestModelSpec()), any());
    }

    @Test
    @DisplayName("update should handle repository failure gracefully")
    void testUpdateRepositoryFailure() {
        when(repository.update(eq(testEntityId), any(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Update failed")));

        assertRepositoryFailure(() -> entityService.update(testEntityId, testEntity, null), "Update failed");
        verify(repository).update(eq(testEntityId), any(), isNull());
    }

    // ========================================
    // SUCCESSFUL REPOSITORY INTERACTIONS
    // ========================================

    @Test
    @DisplayName("getById should return EntityWithMetadata when successful")
    void testGetByIdSuccess() {
        DataPayload dataPayload = createTestDataPayload(testEntity, testEntityId);
        when(repository.findById(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(dataPayload));

        EntityWithMetadata<TestEntity> result = entityService.getById(testEntityId, createTestModelSpec(), TestEntity.class);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertMetadata(result, testEntityId, testEntity.getStatus());
        verify(repository).findById(eq(testEntityId), isNull());
    }

    @Test
    @DisplayName("save should return EntityWithMetadata when successful")
    void testCreateSuccess() {
        UUID savedEntityId = UUID.randomUUID();
        EntityTransactionResponse transactionResponse = createTransactionResponse(savedEntityId);
        when(repository.save(eq(createTestModelSpec()), any()))
                .thenReturn(CompletableFuture.completedFuture(transactionResponse));
        when(repository.findById(eq(savedEntityId), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity, savedEntityId)));
        when(repository.getEntityChangesMetadata(eq(savedEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(transactionResponse.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        EntityWithMetadata<TestEntity> result = entityService.create(testEntity);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertEquals(savedEntityId, result.metadata().getId());
        verify(repository).save(eq(createTestModelSpec()), any());
    }

    @Test
    @DisplayName("deleteById should return entity ID when successful")
    void testDeleteByIdSuccess() {
        EntityDeleteResponse deleteResponse = createDeleteResponse(testEntityId);
        when(repository.deleteById(testEntityId))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        UUID result = entityService.deleteById(testEntityId);

        assertEquals(testEntityId, result);
        verify(repository).deleteById(testEntityId);
    }

    @Test
    @DisplayName("deleteAll should sum deletion counts correctly")
    void testDeleteAllSuccess() {
        List<EntityDeleteAllResponse> responses = List.of(
                createDeleteAllResponse(5),
                createDeleteAllResponse(3)
        );
        when(repository.deleteAll(createTestModelSpec()))
                .thenReturn(CompletableFuture.completedFuture(responses));

        Integer result = entityService.deleteAll(createTestModelSpec());

        assertEquals(8, result);
        verify(repository).deleteAll(createTestModelSpec());
    }

    @Test
    @DisplayName("deleteAll with model parameters should work correctly")
    void testDeleteAllWithModelParameters() {
        String customModel = "custom-model";
        int customVersion = 2;
        ModelSpec customModelSpec = new ModelSpec().withName(customModel).withVersion(customVersion);
        List<EntityDeleteAllResponse> responses = List.of(createDeleteAllResponse(10));
        when(repository.deleteAll(eq(customModelSpec)))
                .thenReturn(CompletableFuture.completedFuture(responses));

        Integer result = entityService.deleteAll(customModelSpec);

        assertEquals(10, result);
        verify(repository).deleteAll(eq(customModelSpec));
    }

    // ========================================
    // BUSINESS LOGIC TESTS
    // ========================================

    @Test
    @DisplayName("update should pass null transition directly to repository")
    void testUpdateWithNullTransition() {
        EntityTransactionResponse response = createTransactionResponse(testEntityId);
        when(repository.update(eq(testEntityId), any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(repository.findById(eq(testEntityId), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity, testEntityId)));
        when(repository.getEntityChangesMetadata(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(response.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        EntityWithMetadata<TestEntity> result = entityService.update(testEntityId, testEntity, null);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertEquals(testEntityId, result.metadata().getId());
        verify(repository).update(eq(testEntityId), any(), isNull());
    }

    @Test
    @DisplayName("update should use provided transition when not null")
    void testUpdateWithCustomTransition() {
        EntityTransactionResponse response = createTransactionResponse(testEntityId);
        when(repository.update(eq(testEntityId), any(), eq(TRANSITION_ACTIVATE)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(repository.findById(eq(testEntityId), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity, testEntityId)));
        when(repository.getEntityChangesMetadata(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(response.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        EntityWithMetadata<TestEntity> result = entityService.update(testEntityId, testEntity, TRANSITION_ACTIVATE);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertEquals(testEntityId, result.metadata().getId());
        verify(repository).update(eq(testEntityId), any(), eq(TRANSITION_ACTIVATE));
    }

    // ========================================
    // EDGE CASES AND VALIDATION
    // ========================================

    @Test
    @DisplayName("saveAll should return empty list when no entities provided")
    void testCreateAllEmptyCollection() {
        Collection<TestEntity> emptyEntities = List.of();

        var result = entityService.save(emptyEntities);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository, never()).saveAll(any(ModelSpec.class), any());
    }

    @Test
    @DisplayName("updateAll should return empty list when no entities provided")
    void testUpdateAllEmptyCollection() {
        Collection<TestEntity> emptyEntities = List.of();

        var result = entityService.updateAll(emptyEntities, TRANSITION_ACTIVATE);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository, never()).updateAll(any(), anyString());
    }

    @Test
    @DisplayName("updateAll should pass null transition directly to repository")
    void testUpdateAllWithNullTransition() {
        Collection<TestEntity> entities = List.of(testEntity);
        List<EntityTransactionResponse> responses = List.of(createTransactionResponse(testEntityId));
        when(repository.updateAll(any(), isNull(),any(), any()))
                .thenReturn(CompletableFuture.completedFuture(responses));

        when(repository.findById(eq(testEntityId), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity, testEntityId)));
        when(repository.getEntityChangesMetadata(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(responses.getFirst().getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        List<EntityWithMetadata<TestEntity>> result = entityService.updateAll(entities, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertNotNull(entityWithMetadata.entity());
        assertNotNull(entityWithMetadata.metadata());
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertEquals(testEntityId, entityWithMetadata.metadata().getId());
        verify(repository).updateAll(any(), isNull(), any(), any());
    }

    // ========================================
    // MISSING COVERAGE TESTS
    // ========================================

    @Test
    @DisplayName("findByBusinessId should call repository.findAllByCriteria with correct search condition")
    void testFindByBusinessIdRepositoryCall() {
        TestEntity entityWithBusinessId = new TestEntity(123L, "TEST-123", "ACTIVE");
        List<DataPayload> payloads = List.of(createTestDataPayload(entityWithBusinessId, testEntityId));
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), payloads, 1, 1, payloads.size())));

        EntityWithMetadata<TestEntity> result = entityService.findByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), entityWithBusinessId);
        assertMetadata(result, testEntityId, entityWithBusinessId.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
    }

    @Test
    @DisplayName("findByBusinessId should handle repository failure")
    void testFindByBusinessIdRepositoryFailure() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search failed")));

        assertRepositoryFailure(() -> entityService.findByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class),
                "Search failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
    }

    @Test
    @DisplayName("findAll should call repository.findAll with correct model parameters")
    void testFindAllRepositoryCall() {
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAll(createTestModelSpec(), 100, 1, null, null))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), payloads, 1, 100, payloads.size())));

        PageResult<EntityWithMetadata<TestEntity>> result = entityService.findAll(createTestModelSpec(), TestEntity.class, 100, 1, null, null);

        assertNotNull(result);
        assertEquals(1, result.data().size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.data().getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAll(createTestModelSpec(), 100, 1, null, null);
    }

    @Test
    @DisplayName("findAll should handle repository failure")
    void testFindAllRepositoryFailure() {
        when(repository.findAll(createTestModelSpec(), 100, 1, null, null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find all failed")));

        assertRepositoryFailure(() -> entityService.findAll(createTestModelSpec(), TestEntity.class, 100, 1, null, null), "Find all failed");
        verify(repository).findAll(createTestModelSpec(), 100, 1, null, null);
    }

    @Test
    @DisplayName("search should call repository.findAllByCriteria with search condition")
    void testSearchRepositoryCall() {
        GroupCondition condition = createActiveStatusCondition();
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), payloads, 1, 100, payloads.size())));

        PageResult<EntityWithMetadata<TestEntity>> result = entityService.search(createTestModelSpec(), condition, TestEntity.class, 100, 1, true, null, null);

        assertNotNull(result);
        assertEquals(1, result.data().size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.data().getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true), isNull(), isNull());
    }

    @Test
    @DisplayName("search should handle repository failure")
    void testSearchRepositoryFailure() {
        GroupCondition condition = createActiveStatusCondition();
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search failed")));

        assertRepositoryFailure(() -> entityService.search(createTestModelSpec(), condition, TestEntity.class, 100, 1, true, null, null), "Search failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true), isNull(), isNull());
    }

    // ========================================
    // ADDITIONAL COVERAGE TESTS
    // ========================================


    @Test
    @DisplayName("deleteAll should handle repository failure")
    void testDeleteAllRepositoryFailure() {
        when(repository.deleteAll(createTestModelSpec()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete all failed")));

        assertRepositoryFailure(() -> entityService.deleteAll(createTestModelSpec()), "Delete all failed");
        verify(repository).deleteAll(createTestModelSpec());
    }

    @Test
    @DisplayName("deleteAll with model parameters should handle repository failure")
    void testDeleteAllWithModelParametersFailure() {
        String customModel = "custom-model";
        int customVersion = 2;
        ModelSpec customModelSpec = new ModelSpec().withName(customModel).withVersion(customVersion);
        when(repository.deleteAll(customModelSpec))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete failed")));

        assertRepositoryFailure(() -> entityService.deleteAll(customModelSpec), "Delete failed");
        verify(repository).deleteAll(customModelSpec);
    }

    @Test
    @DisplayName("getItems should handle repository failure")
    void testGetItemsRepositoryFailure() {
        when(repository.findAll(createTestModelSpec(), 100, 1, null, null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find all failed")));

        assertRepositoryFailure(() -> entityService.findAll(createTestModelSpec(), TestEntity.class, 100, 1, null, null),
                "Find all failed");
        verify(repository).findAll(createTestModelSpec(), 100, 1, null, null);
    }

    @Test
    @DisplayName("updateByBusinessId should handle repository failure during find")
    void testUpdateByBusinessIdFindFailure() {
        testEntity.setName("TEST-123");
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find failed")));

        assertRepositoryFailure(() -> entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE),
                "Find failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository, never()).update(any(UUID.class), any(), anyString());
    }

    @Test
    @DisplayName("updateByBusinessId should handle entity not found")
    void testUpdateByBusinessIdEntityNotFound() {
        testEntity.setName("NONEXISTENT");
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), List.of(), 1, 1, 0L)));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE));
        assertTrue(exception.getMessage().contains("Entity not found with business ID"));
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository, never()).update(any(UUID.class), any(), anyString());
    }

    @Test
    @DisplayName("updateByBusinessId should successfully update entity when found")
    void testUpdateByBusinessIdSuccess() {
        testEntity.setName("TEST-123");
        UUID existingEntityTechnicalId = UUID.randomUUID();

        TestEntity foundEntity = new TestEntity(123L, "TEST-123", "ACTIVE");
        DataPayload foundPayload = createTestDataPayload(foundEntity, existingEntityTechnicalId);
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), List.of(foundPayload), 1, 1, 1L)));

        EntityTransactionResponse updateResponse = createTransactionResponse(existingEntityTechnicalId);
        when(repository.update(eq(existingEntityTechnicalId), any(), eq(TRANSITION_ACTIVATE)))
                .thenReturn(CompletableFuture.completedFuture(updateResponse));

        when(repository.findById(eq(existingEntityTechnicalId), any()))
                .thenReturn(CompletableFuture.completedFuture(foundPayload));
        when(repository.getEntityChangesMetadata(eq(existingEntityTechnicalId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(updateResponse.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        EntityWithMetadata<TestEntity> result = entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertEquals(existingEntityTechnicalId, result.metadata().getId());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository).update(eq(existingEntityTechnicalId), any(), eq(TRANSITION_ACTIVATE));
    }

    @Test
    @DisplayName("deleteByBusinessId should handle repository failure during find")
    void testDeleteByBusinessIdFindFailure() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find failed")));

        assertRepositoryFailure(() -> entityService.deleteByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class),
                "Find failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("deleteByBusinessId should return false when entity not found")
    void testDeleteByBusinessIdEntityNotFound() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), List.of(), 1, 1, 0L)));

        boolean result = entityService.deleteByBusinessId(createTestModelSpec(), "NONEXISTENT", BUSINESS_ID_FIELD, TestEntity.class);

        assertFalse(result);
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("deleteByBusinessId should successfully delete entity when found")
    void testDeleteByBusinessIdSuccess() {
        UUID entityTechnicalId = UUID.randomUUID();
        TestEntity foundEntity = new TestEntity(123L, "TEST-123", "ACTIVE");
        DataPayload foundPayload = createTestDataPayload(foundEntity, entityTechnicalId);
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(PageResult.of(UUID.randomUUID(), List.of(foundPayload), 1, 1, 1L)));

        EntityDeleteResponse deleteResponse = createDeleteResponse(entityTechnicalId);
        when(repository.deleteById(entityTechnicalId))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        boolean result = entityService.deleteByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class);

        assertTrue(result);
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true), isNull(), isNull());
        verify(repository).deleteById(entityTechnicalId);
    }

    @Test
    @DisplayName("saveAll should call repository.saveAll with non-empty collection")
    void testCreateAllWithEntitiesRepositoryCall() {
        Collection<TestEntity> entities = List.of(testEntity, testEntity2);
        EntityTransactionInfo transactionInfo = new EntityTransactionInfo();
        transactionInfo.setEntityIds(List.of(testEntityId, testEntityId2));
        transactionInfo.setTransactionId(UUID.randomUUID());
        EntityTransactionResponse transactionResponse = new EntityTransactionResponse();
        transactionResponse.setTransactionInfo(transactionInfo);

        when(repository.saveAll(eq(createTestModelSpec()), eq(entities), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(transactionResponse));

        when(repository.findById(eq(testEntityId), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity, testEntityId)));
        when(repository.getEntityChangesMetadata(eq(testEntityId), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(transactionResponse.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        when(repository.findById(eq(testEntityId2), any()))
                .thenReturn(CompletableFuture.completedFuture(createTestDataPayload(testEntity2, testEntityId2)));
        when(repository.getEntityChangesMetadata(eq(testEntityId2), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(
                        new EntityChangeMeta()
                                .withTransactionId(transactionResponse.getTransactionInfo().getTransactionId())
                                .withTimeOfChange(new Date())
                )));

        List<EntityWithMetadata<TestEntity>> result = entityService.save(entities);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Verify that entities are properly mapped
        assertEntityMatches(result.getFirst().entity(), testEntity);
        assertEntityMatches(result.get(1).entity(), testEntity2);
        assertEquals(testEntityId, result.getFirst().metadata().getId());
        assertEquals(testEntityId2, result.get(1).metadata().getId());
        verify(repository).saveAll(eq(createTestModelSpec()), eq(entities), any(), any());
    }

    @Test
    @DisplayName("saveAll should handle repository failure with non-empty collection")
    void testCreateAllWithEntitiesRepositoryFailure() {
        Collection<TestEntity> entities = List.of(testEntity, testEntity2);
        when(repository.saveAll(eq(createTestModelSpec()), eq(entities), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save all failed")));

        assertRepositoryFailure(() -> entityService.save(entities), "Save all failed");
        verify(repository).saveAll(eq(createTestModelSpec()), eq(entities), any(), any());
    }
}
