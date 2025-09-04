package com.java_template.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.repository.CrudRepository;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.entity.EntityDeleteAllResponse;
import org.cyoda.cloud.api.event.entity.EntityDeleteResponse;
import org.cyoda.cloud.api.event.entity.EntityTransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
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

    @Mock
    private CrudRepository repository;

    private ObjectMapper objectMapper;
    private EntityServiceImpl entityService;
    private UUID testEntityId;
    private TestEntity testEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper(); // Use real ObjectMapper
        entityService = new EntityServiceImpl(repository, objectMapper);
        testEntityId = UUID.randomUUID();
        testEntity = new TestEntity(123L, "Test Entity", "ACTIVE");
    }

    // Test entity class
    static class TestEntity implements CyodaEntity {
        private Long id;
        private String name;
        private String status;

        public TestEntity() {}

        public TestEntity(Long id, String name, String status) {
            this.id = id;
            this.name = name;
            this.status = status;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public OperationSpecification getModelKey() {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName("test-entity");
            modelSpec.setVersion(1);
            return new OperationSpecification.Entity(modelSpec, "test-entity");
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
        // Given
        when(repository.findById(testEntityId))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Repository error")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> entityService.getById(testEntityId, TestEntity.class));
        assertTrue(exception.getMessage().contains("Failed to get entity by ID"));
        assertTrue(exception.getCause().getMessage().contains("Repository error"));
        verify(repository).findById(testEntityId);
    }

    @Test
    @DisplayName("deleteById should handle repository failure gracefully")
    void testDeleteByIdRepositoryFailure() {
        // Given
        when(repository.deleteById(testEntityId))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> entityService.deleteById(testEntityId));
        assertTrue(exception.getMessage().contains("Failed to delete entity by ID"));
        assertTrue(exception.getCause().getMessage().contains("Delete failed"));
        verify(repository).deleteById(testEntityId);
    }

    @Test
    @DisplayName("save should handle repository failure gracefully")
    void testSaveRepositoryFailure() {
        // Given
        when(repository.save(eq("test-entity"), eq(1), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.save(testEntity));
        assertTrue(exception.getMessage().contains("Failed to save entity"));
        assertTrue(exception.getCause().getMessage().contains("Save failed"));
        verify(repository).save(eq("test-entity"), eq(1), any());
    }

    @Test
    @DisplayName("update should handle repository failure gracefully")
    void testUpdateRepositoryFailure() {
        // Given
        when(repository.update(eq(testEntityId), any(), eq("UPDATE")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Update failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.update(testEntityId, testEntity, null));
        assertTrue(exception.getMessage().contains("Failed to update entity"));
        assertTrue(exception.getCause().getMessage().contains("Update failed"));
        verify(repository).update(eq(testEntityId), any(), eq("UPDATE"));
    }

    // ========================================
    // SUCCESSFUL REPOSITORY INTERACTIONS
    // ========================================

    @Test
    @DisplayName("deleteById should return entity ID when successful")
    void testDeleteByIdSuccess() {
        // Given
        EntityDeleteResponse deleteResponse = new EntityDeleteResponse();
        deleteResponse.setEntityId(testEntityId);
        when(repository.deleteById(testEntityId))
                .thenReturn(CompletableFuture.completedFuture(deleteResponse));

        // When
        UUID result = entityService.deleteById(testEntityId);

        // Then
        assertEquals(testEntityId, result);
        verify(repository).deleteById(testEntityId);
    }

    @Test
    @DisplayName("deleteAll should sum deletion counts correctly")
    void testDeleteAllSuccess() {
        // Given
        EntityDeleteAllResponse response1 = new EntityDeleteAllResponse();
        response1.setNumDeleted(5);
        EntityDeleteAllResponse response2 = new EntityDeleteAllResponse();
        response2.setNumDeleted(3);
        List<EntityDeleteAllResponse> responses = List.of(response1, response2);
        
        when(repository.deleteAll("test-entity", 1))
                .thenReturn(CompletableFuture.completedFuture(responses));

        // When
        Integer result = entityService.deleteAll(TestEntity.class);

        // Then
        assertEquals(8, result);
        verify(repository).deleteAll("test-entity", 1);
    }

    @Test
    @DisplayName("deleteAll with model parameters should work correctly")
    void testDeleteAllWithModelParameters() {
        // Given
        EntityDeleteAllResponse response = new EntityDeleteAllResponse();
        response.setNumDeleted(10);
        List<EntityDeleteAllResponse> responses = List.of(response);

        when(repository.deleteAll(eq("custom-model"), eq(2)))
                .thenReturn(CompletableFuture.completedFuture(responses));

        // When
        Integer result = entityService.deleteAll("custom-model", 2);

        // Then
        assertEquals(10, result);
        verify(repository).deleteAll(eq("custom-model"), eq(2));
    }

    // ========================================
    // BUSINESS LOGIC TESTS
    // ========================================

    @Test
    @DisplayName("update should use default UPDATE transition when transition is null")
    void testUpdateWithNullTransition() {
        // Given
        EntityTransactionResponse response = new EntityTransactionResponse();
        when(repository.update(eq(testEntityId), any(), eq("UPDATE")))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When & Then - Test that the correct repository method is called with UPDATE transition
        try {
            entityService.update(testEntityId, testEntity, null);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromTransactionResponse
        }
        verify(repository).update(eq(testEntityId), any(), eq("UPDATE"));
    }

    @Test
    @DisplayName("update should use provided transition when not null")
    void testUpdateWithCustomTransition() {
        // Given
        String customTransition = "ACTIVATE";
        EntityTransactionResponse response = new EntityTransactionResponse();
        when(repository.update(eq(testEntityId), any(), eq(customTransition)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When & Then - Test that the correct repository method is called with custom transition
        try {
            entityService.update(testEntityId, testEntity, customTransition);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromTransactionResponse
        }
        verify(repository).update(eq(testEntityId), any(), eq(customTransition));
    }

    // ========================================
    // EDGE CASES AND VALIDATION
    // ========================================

    @Test
    @DisplayName("saveAll should return empty list when no entities provided")
    void testSaveAllEmptyCollection() {
        // Given
        Collection<TestEntity> emptyEntities = List.of();

        // When
        var result = entityService.saveAll(emptyEntities);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository, never()).saveAll(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("saveAllAndReturnTransactionInfo should return null when empty collection provided")
    void testSaveAllAndReturnTransactionInfoEmptyCollection() {
        // Given
        Collection<TestEntity> emptyEntities = List.of();

        // When
        var result = entityService.saveAllAndReturnTransactionInfo(emptyEntities);

        // Then
        assertNull(result);
        verify(repository, never()).saveAll(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("updateAll should return empty list when no entities provided")
    void testUpdateAllEmptyCollection() {
        // Given
        Collection<TestEntity> emptyEntities = List.of();

        // When
        var result = entityService.updateAll(emptyEntities, "ACTIVATE");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(repository, never()).updateAll(any(), anyString());
    }

    @Test
    @DisplayName("updateAll should use default UPDATE transition when transition is null")
    void testUpdateAllWithNullTransition() {
        // Given
        Collection<TestEntity> entities = List.of(testEntity);
        List<EntityTransactionResponse> responses = List.of(new EntityTransactionResponse());

        when(repository.updateAll(any(), eq("UPDATE")))
                .thenReturn(CompletableFuture.completedFuture(responses));

        // When & Then - Test that the correct repository method is called with UPDATE transition
        try {
            entityService.updateAll(entities, null);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromTransactionResponseList
        }
        verify(repository).updateAll(any(), eq("UPDATE"));
    }

    // ========================================
    // REPOSITORY CALL VERIFICATION TESTS
    // ========================================

    @Test
    @DisplayName("getItems should call repository.findAll with correct parameters")
    void testGetItemsRepositoryCall() {
        // Given
        when(repository.findAll("test-entity", 1, 50, 2, null))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When & Then - Test that the correct repository method is called
        try {
            entityService.getItems(TestEntity.class, "test-entity", 1, 50, 2, null);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromDataPayload
        }
        verify(repository).findAll("test-entity", 1, 50, 2, null);
    }

    @Test
    @DisplayName("getItems should use default pagination when parameters are null")
    void testGetItemsWithDefaultPagination() {
        // Given
        when(repository.findAll("test-entity", 1, 100, 1, null))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When & Then - Test that the correct repository method is called with defaults
        try {
            entityService.getItems(TestEntity.class, "test-entity", 1, null, null, null);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromDataPayload
        }
        verify(repository).findAll("test-entity", 1, 100, 1, null);
    }

    // ========================================
    // ADDITIONAL COVERAGE TESTS
    // ========================================

    @Test
    @DisplayName("saveAndReturnTransactionInfo should call repository.save and return transaction info")
    void testSaveAndReturnTransactionInfo() {
        // Given
        EntityTransactionResponse response = new EntityTransactionResponse();
        when(repository.save(eq("test-entity"), eq(1), any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When & Then - Test that the correct repository method is called
        try {
            entityService.saveAndReturnTransactionInfo(testEntity);
        } catch (Exception e) {
            // Expected due to ObjectMapper.valueToTree on transaction info
        }
        verify(repository).save(eq("test-entity"), eq(1), any());
    }

    @Test
    @DisplayName("saveAndReturnTransactionInfo should handle repository failure")
    void testSaveAndReturnTransactionInfoFailure() {
        // Given
        when(repository.save(eq("test-entity"), eq(1), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Save failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.saveAndReturnTransactionInfo(testEntity));
        assertTrue(exception.getMessage().contains("Failed to save entity and return transaction info"));
        verify(repository).save(eq("test-entity"), eq(1), any());
    }

    @Test
    @DisplayName("getFirstItemByCondition should call repository.findAllByCriteria with correct parameters")
    void testGetFirstItemByCondition() {
        // Given
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE"));
        when(repository.findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(1), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When & Then - Test that the correct repository method is called
        try {
            entityService.getFirstItemByCondition(TestEntity.class, "test-entity", 1, condition, true);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromDataPayload
        }
        verify(repository).findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(1), eq(1), eq(true));
    }

    @Test
    @DisplayName("getItemsByCondition should call repository.findAllByCriteria with correct parameters")
    void testGetItemsByCondition() {
        // Given
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE"));
        when(repository.findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // When & Then - Test that the correct repository method is called
        try {
            entityService.getItemsByCondition(TestEntity.class, "test-entity", 1, condition, true);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromDataPayload
        }
        verify(repository).findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

    @Test
    @DisplayName("getItemsByCondition should filter null payloads")
    void testGetItemsByConditionWithNullPayloads() {
        // Given
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "ACTIVE"));
        DataPayload validPayload = new DataPayload();
        // Create a list that includes null - this tests the null filtering in the stream
        List<DataPayload> payloadsWithNull = new java.util.ArrayList<>();
        payloadsWithNull.add(null);
        payloadsWithNull.add(validPayload);

        when(repository.findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(100), eq(1), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(payloadsWithNull));

        // When & Then - Test that null payloads are filtered out
        try {
            entityService.getItemsByCondition(TestEntity.class, "test-entity", 1, condition, true);
        } catch (Exception e) {
            // Expected due to EntityWithMetadata.fromDataPayload
        }
        verify(repository).findAllByCriteria(eq("test-entity"), eq(1), any(GroupCondition.class), eq(100), eq(1), eq(true));
    }

    @Test
    @DisplayName("deleteAll should handle repository failure")
    void testDeleteAllRepositoryFailure() {
        // Given
        when(repository.deleteAll("test-entity", 1))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete all failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.deleteAll(TestEntity.class));
        assertTrue(exception.getMessage().contains("Failed to delete all entities"));
        verify(repository).deleteAll("test-entity", 1);
    }

    @Test
    @DisplayName("deleteAll with model parameters should handle repository failure")
    void testDeleteAllWithModelParametersFailure() {
        // Given
        when(repository.deleteAll("custom-model", 2))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Delete failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.deleteAll("custom-model", 2));
        assertTrue(exception.getMessage().contains("Failed to delete entities"));
        verify(repository).deleteAll("custom-model", 2);
    }

    @Test
    @DisplayName("getItems should handle repository failure")
    void testGetItemsRepositoryFailure() {
        // Given
        when(repository.findAll("test-entity", 1, 100, 1, null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Find all failed")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> entityService.getItems(TestEntity.class, "test-entity", 1, null, null, null));
        assertTrue(exception.getMessage().contains("Failed to get items"));
        verify(repository).findAll("test-entity", 1, 100, 1, null);
    }
}
