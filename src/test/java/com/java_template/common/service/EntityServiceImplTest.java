package com.java_template.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
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
    private static final String TRANSITION_UPDATE = "UPDATE";
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
        public boolean isValid() {
            return id != null && name != null && !name.trim().isEmpty();
        }
    }

    // ========================================
    // REPOSITORY FAILURE TESTS
    // ========================================

    @Test
    @DisplayName("getById should handle repository failure gracefully")
    void testGetByIdRepositoryFailure() {
        when(repository.findById(testEntityId))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Repository error")));

        assertRepositoryFailure(() -> entityService.getById(testEntityId, createTestModelSpec(), TestEntity.class), "Repository error");
        verify(repository).findById(testEntityId);
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
        when(repository.findById(testEntityId))
                .thenReturn(CompletableFuture.completedFuture(dataPayload));

        EntityWithMetadata<TestEntity> result = entityService.getById(testEntityId, createTestModelSpec(), TestEntity.class);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertMetadata(result, testEntityId, testEntity.getStatus());
        verify(repository).findById(testEntityId);
    }

    @Test
    @DisplayName("save should return EntityWithMetadata when successful")
    void testCreateSuccess() {
        UUID savedEntityId = UUID.randomUUID();
        EntityTransactionResponse transactionResponse = createTransactionResponse(savedEntityId);
        when(repository.save(eq(createTestModelSpec()), any()))
                .thenReturn(CompletableFuture.completedFuture(transactionResponse));

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
    @DisplayName("saveAllAndReturnTransactionInfo should return null when empty collection provided")
    void testCreateAllAndReturnTransactionInfoEmptyCollection() {
        Collection<TestEntity> emptyEntities = List.of();

        var result = entityService.saveAllAndReturnTransactionInfo(emptyEntities);

        assertNull(result);
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
        when(repository.updateAll(any(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(responses));

        List<EntityWithMetadata<TestEntity>> result = entityService.updateAll(entities, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertNotNull(entityWithMetadata.entity());
        assertNotNull(entityWithMetadata.metadata());
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertEquals(testEntityId, entityWithMetadata.metadata().getId());
        verify(repository).updateAll(any(), isNull());
    }

    // ========================================
    // REPOSITORY CALL VERIFICATION TESTS
    // ========================================

    @Test
    @DisplayName("getItems should call repository.findAll with correct parameters")
    void testGetItemsRepositoryCall() {
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAll(createTestModelSpec(), 50, 2, null))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        List<EntityWithMetadata<TestEntity>> result = entityService.getItems(TestEntity.class, createTestModelSpec(), 50, 2, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAll(createTestModelSpec(), 50, 2, null);
    }

    @Test
    @DisplayName("getItems should use default pagination when parameters are null")
    void testGetItemsWithDefaultPagination() {
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAll(createTestModelSpec(), 100, 1, null))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        List<EntityWithMetadata<TestEntity>> result = entityService.getItems(TestEntity.class, createTestModelSpec(), null, null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAll(createTestModelSpec(), 100, 1, null);
    }

    // ========================================
    // MISSING COVERAGE TESTS
    // ========================================

    @Test
    @DisplayName("findByBusinessId should call repository.findAllByCriteria with correct search condition")
    void testFindByBusinessIdRepositoryCall() {
        TestEntity entityWithBusinessId = new TestEntity(123L, "TEST-123", "ACTIVE");
        List<DataPayload> payloads = List.of(createTestDataPayload(entityWithBusinessId, testEntityId));
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        EntityWithMetadata<TestEntity> result = entityService.findByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), entityWithBusinessId);
        assertMetadata(result, testEntityId, entityWithBusinessId.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
    }

    @Test
    @DisplayName("findByBusinessId should handle repository failure")
    void testFindByBusinessIdRepositoryFailure() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search failed")));

        assertRepositoryFailure(() -> entityService.findByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class),
                "Search failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
    }

    @Test
    @DisplayName("findAll should call repository.findAll with correct model parameters")
    void testFindAllRepositoryCall() {
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAll(createTestModelSpec(), 100, 1, null))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        List<EntityWithMetadata<TestEntity>> result = entityService.findAll(createTestModelSpec(), TestEntity.class);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAll(createTestModelSpec(), 100, 1, null);
    }

    @Test
    @DisplayName("findAll should handle repository failure")
    void testFindAllRepositoryFailure() {
        when(repository.findAll(createTestModelSpec(), 100, 1, null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find all failed")));

        assertRepositoryFailure(() -> entityService.findAll(createTestModelSpec(), TestEntity.class), "Find all failed");
        verify(repository).findAll(createTestModelSpec(), 100, 1, null);
    }

    @Test
    @DisplayName("search should call repository.findAllByCriteria with search condition")
    void testSearchRepositoryCall() {
        GroupCondition condition = createActiveStatusCondition();
        List<DataPayload> payloads = List.of(createTestDataPayload(testEntity, testEntityId));
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        List<EntityWithMetadata<TestEntity>> result = entityService.search(createTestModelSpec(), condition, TestEntity.class);

        assertNotNull(result);
        assertEquals(1, result.size());
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

    @Test
    @DisplayName("search should handle repository failure")
    void testSearchRepositoryFailure() {
        GroupCondition condition = createActiveStatusCondition();
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Search failed")));

        assertRepositoryFailure(() -> entityService.search(createTestModelSpec(), condition, TestEntity.class), "Search failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

    // ========================================
    // ADDITIONAL COVERAGE TESTS
    // ========================================

    @Test
    @DisplayName("saveAndReturnTransactionInfo should call repository.save and return transaction info")
    void testCreateAndReturnTransactionInfo() {
        EntityTransactionInfo transactionInfo = new EntityTransactionInfo();
        transactionInfo.setEntityIds(List.of(testEntityId));
        EntityTransactionResponse response = new EntityTransactionResponse();
        response.setTransactionInfo(transactionInfo);
        when(repository.save(eq(createTestModelSpec()), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        ObjectNode result = entityService.saveAndReturnTransactionInfo(testEntity);

        assertNotNull(result);
        assertTrue(result.has("entityIds"));
        verify(repository).save(eq(createTestModelSpec()), any());
    }

    @Test
    @DisplayName("saveAndReturnTransactionInfo should handle repository failure")
    void testCreateAndReturnTransactionInfoFailure() {
        when(repository.save(eq(createTestModelSpec()), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        assertRepositoryFailure(() -> entityService.saveAndReturnTransactionInfo(testEntity), "Save failed");
        verify(repository).save(eq(createTestModelSpec()), any());
    }

    @Test
    @DisplayName("getFirstItemByCondition should return empty optional when no entities found")
    void testGetFirstItemByConditionEmpty() {
        GroupCondition condition = createActiveStatusCondition();
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        var result = entityService.getFirstItemByCondition(TestEntity.class, createTestModelSpec(), condition, true);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
    }

    @Test
    @DisplayName("getFirstItemByCondition should return first entity when non-empty payloads are returned")
    void testGetFirstItemByConditionWithNonEmptyPayloads() {
        GroupCondition condition = createActiveStatusCondition();

        TestEntity firstEntity = new TestEntity(123L, "First Entity", "ACTIVE");
        TestEntity secondEntity = new TestEntity(456L, "Second Entity", "ACTIVE");
        DataPayload firstPayload = createTestDataPayload(firstEntity, testEntityId);
        DataPayload secondPayload = createTestDataPayload(secondEntity, testEntityId2);
        List<DataPayload> payloads = List.of(firstPayload, secondPayload);

        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(payloads));

        var result = entityService.getFirstItemByCondition(TestEntity.class, createTestModelSpec(), condition, true);

        assertTrue(result.isPresent());
        var entityWithMetadata = result.get();
        assertEntityMatches(entityWithMetadata.entity(), firstEntity);
        assertMetadata(entityWithMetadata, testEntityId, firstEntity.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
    }

    @Test
    @DisplayName("getItemsByCondition should return empty list when no entities found")
    void testGetItemsByConditionEmpty() {
        GroupCondition condition = createActiveStatusCondition();
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        List<EntityWithMetadata<TestEntity>> result = entityService.getItemsByCondition(TestEntity.class, createTestModelSpec(), condition, true);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

    @Test
    @DisplayName("getItemsByCondition should filter null payloads and return valid entities")
    void testGetItemsByConditionWithNullPayloads() {
        GroupCondition condition = createActiveStatusCondition();
        DataPayload validPayload = createTestDataPayload(testEntity, testEntityId);
        List<DataPayload> payloadsWithNull = new java.util.ArrayList<>();
        payloadsWithNull.add(null);
        payloadsWithNull.add(validPayload);

        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(payloadsWithNull));

        List<EntityWithMetadata<TestEntity>> result = entityService.getItemsByCondition(TestEntity.class, createTestModelSpec(), condition, true);

        assertNotNull(result);
        assertEquals(1, result.size()); // Only the valid payload should be processed
        EntityWithMetadata<TestEntity> entityWithMetadata = result.getFirst();
        assertEntityMatches(entityWithMetadata.entity(), testEntity);
        assertMetadata(entityWithMetadata, testEntityId, testEntity.getStatus());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

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
        when(repository.findAll(createTestModelSpec(), 100, 1, null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find all failed")));

        assertRepositoryFailure(() -> entityService.getItems(TestEntity.class, createTestModelSpec(), null, null, null),
                "Find all failed");
        verify(repository).findAll(createTestModelSpec(), 100, 1, null);
    }

    @Test
    @DisplayName("updateByBusinessId should handle repository failure during find")
    void testUpdateByBusinessIdFindFailure() {
        testEntity.setName("TEST-123");
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find failed")));

        assertRepositoryFailure(() -> entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE),
                "Find failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository, never()).update(any(UUID.class), any(), anyString());
    }

    @Test
    @DisplayName("updateByBusinessId should handle entity not found")
    void testUpdateByBusinessIdEntityNotFound() {
        testEntity.setName("NONEXISTENT");
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE));
        assertTrue(exception.getMessage().contains("Entity not found with business ID"));
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository, never()).update(any(UUID.class), any(), anyString());
    }

    @Test
    @DisplayName("updateByBusinessId should successfully update entity when found")
    void testUpdateByBusinessIdSuccess() {
        testEntity.setName("TEST-123");
        UUID existingEntityTechnicalId = UUID.randomUUID();

        TestEntity foundEntity = new TestEntity(123L, "TEST-123", "ACTIVE");
        DataPayload foundPayload = createTestDataPayload(foundEntity, existingEntityTechnicalId);
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(foundPayload)));

        EntityTransactionResponse updateResponse = createTransactionResponse(existingEntityTechnicalId);
        when(repository.update(eq(existingEntityTechnicalId), any(), eq(TRANSITION_ACTIVATE)))
                .thenReturn(CompletableFuture.completedFuture(updateResponse));

        EntityWithMetadata<TestEntity> result = entityService.updateByBusinessId(testEntity, BUSINESS_ID_FIELD, TRANSITION_ACTIVATE);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertNotNull(result.metadata());
        assertEntityMatches(result.entity(), testEntity);
        assertEquals(existingEntityTechnicalId, result.metadata().getId());
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository).update(eq(existingEntityTechnicalId), any(), eq(TRANSITION_ACTIVATE));
    }

    @Test
    @DisplayName("deleteByBusinessId should handle repository failure during find")
    void testDeleteByBusinessIdFindFailure() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find failed")));

        assertRepositoryFailure(() -> entityService.deleteByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class),
                "Find failed");
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("deleteByBusinessId should return false when entity not found")
    void testDeleteByBusinessIdEntityNotFound() {
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        boolean result = entityService.deleteByBusinessId(createTestModelSpec(), "NONEXISTENT", BUSINESS_ID_FIELD, TestEntity.class);

        assertFalse(result);
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("deleteByBusinessId should successfully delete entity when found")
    void testDeleteByBusinessIdSuccess() {
        UUID entityTechnicalId = UUID.randomUUID();
        TestEntity foundEntity = new TestEntity(123L, "TEST-123", "ACTIVE");
        DataPayload foundPayload = createTestDataPayload(foundEntity, entityTechnicalId);
        when(repository.findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(foundPayload)));

        EntityDeleteResponse deleteResponse = createDeleteResponse(entityTechnicalId);
        when(repository.deleteById(entityTechnicalId))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        boolean result = entityService.deleteByBusinessId(createTestModelSpec(), "TEST-123", BUSINESS_ID_FIELD, TestEntity.class);

        assertTrue(result);
        verify(repository).findAllByCriteria(eq(createTestModelSpec()), any(GroupCondition.class), eq(1), eq(1), eq(true));
        verify(repository).deleteById(entityTechnicalId);
    }

    @Test
    @DisplayName("saveAll should call repository.saveAll with non-empty collection")
    void testCreateAllWithEntitiesRepositoryCall() {
        Collection<TestEntity> entities = List.of(testEntity, testEntity2);
        EntityTransactionInfo transactionInfo = new EntityTransactionInfo();
        transactionInfo.setEntityIds(List.of(testEntityId, testEntityId2));
        EntityTransactionResponse transactionResponse = new EntityTransactionResponse();
        transactionResponse.setTransactionInfo(transactionInfo);
        when(repository.saveAll(createTestModelSpec(), entities))
                .thenReturn(CompletableFuture.completedFuture(transactionResponse));

        List<EntityWithMetadata<TestEntity>> result = entityService.save(entities);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Verify that entities are properly mapped
        assertEntityMatches(result.getFirst().entity(), testEntity);
        assertEntityMatches(result.get(1).entity(), testEntity2);
        assertEquals(testEntityId, result.getFirst().metadata().getId());
        assertEquals(testEntityId2, result.get(1).metadata().getId());
        verify(repository).saveAll(createTestModelSpec(), entities);
    }

    @Test
    @DisplayName("saveAll should handle repository failure with non-empty collection")
    void testCreateAllWithEntitiesRepositoryFailure() {
        Collection<TestEntity> entities = List.of(testEntity, testEntity2);
        when(repository.saveAll(createTestModelSpec(), entities))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save all failed")));

        assertRepositoryFailure(() -> entityService.save(entities), "Save all failed");
        verify(repository).saveAll(createTestModelSpec(), entities);
    }

    @Test
    @DisplayName("saveAllAndReturnTransactionInfo should return transaction info from repository response")
    void testCreateAllAndReturnTransactionInfoWithEntities() {
        Collection<TestEntity> entities = List.of(testEntity, testEntity2);
        EntityTransactionInfo mockTransactionInfo = new EntityTransactionInfo();
        EntityTransactionResponse mockTransactionResponse = new EntityTransactionResponse();
        mockTransactionResponse.setTransactionInfo(mockTransactionInfo);

        when(repository.saveAll(any(ModelSpec.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mockTransactionResponse));

        EntityTransactionInfo result = entityService.saveAllAndReturnTransactionInfo(entities);

        assertNotNull(result);
        assertEquals(mockTransactionInfo, result);
        verify(repository).saveAll(eq(createTestModelSpec()), any());
    }
}
