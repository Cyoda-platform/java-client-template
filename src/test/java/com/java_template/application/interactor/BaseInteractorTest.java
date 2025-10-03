package com.java_template.application.interactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Base test class providing common testing utilities and patterns for all interactor tests.
 * Provides reusable methods for creating test data, mocking EntityService, and asserting results.
 */
public abstract class BaseInteractorTest<T extends CyodaEntity> {

    @Mock
    protected EntityService entityService;

    protected ObjectMapper objectMapper;
    protected UUID testEntityId;
    protected UUID testEntityId2;

    @BeforeEach
    void baseSetUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testEntityId = UUID.randomUUID();
        testEntityId2 = UUID.randomUUID();
    }

    // ========================================
    // ABSTRACT METHODS - Subclasses must implement
    // ========================================

    /**
     * Get the entity name for the entity under test
     */
    protected abstract String getEntityName();

    /**
     * Get the entity version for the entity under test
     */
    protected abstract Integer getEntityVersion();

    /**
     * Get the business ID field name (e.g., "loanId", "partyId")
     */
    protected abstract String getBusinessIdField();

    /**
     * Create a valid test entity with the given business ID
     */
    protected abstract T createValidEntity(String businessId);

    /**
     * Get the business ID from an entity
     */
    protected abstract String getBusinessId(T entity);

    /**
     * Set the business ID on an entity
     */
    protected abstract void setBusinessId(T entity, String businessId);

    /**
     * Assert that two entities are equal (excluding metadata fields like createdAt, updatedAt)
     */
    protected abstract void assertEntityEquals(T expected, T actual);

    // ========================================
    // TEST DATA BUILDERS
    // ========================================

    protected ModelSpec createModelSpec() {
        return new ModelSpec()
                .withName(getEntityName())
                .withVersion(getEntityVersion());
    }

    protected EntityWithMetadata<T> createEntityWithMetadata(T entity, UUID entityId, String state) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(entityId);
        metadata.setState(state);
        metadata.setCreationDate(new Date());

        return new EntityWithMetadata<>(entity, metadata);
    }

    protected EntityWithMetadata<T> createEntityWithMetadata(T entity, UUID entityId) {
        return createEntityWithMetadata(entity, entityId, "VALIDATED");
    }

    protected DataPayload createDataPayload(T entity, UUID entityId, String state) {
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(entity));

        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(entityId);
        metadata.setState(state);
        metadata.setCreationDate(new Date());
        payload.setMeta(objectMapper.valueToTree(metadata));

        return payload;
    }

    protected DataPayload createDataPayload(T entity, UUID entityId) {
        return createDataPayload(entity, entityId, "VALIDATED");
    }

    // ========================================
    // MOCKITO SETUP HELPERS
    // ========================================

    @SuppressWarnings("unchecked")
    protected void mockCreate(T entity, EntityWithMetadata<T> response) {
        when(entityService.create(any())).thenReturn((EntityWithMetadata) response);
    }

    @SuppressWarnings("unchecked")
    protected void mockGetById(UUID id, EntityWithMetadata<T> response) {
        when(entityService.getById(eq(id), any(ModelSpec.class), any(Class.class)))
                .thenReturn((EntityWithMetadata) response);
    }

    @SuppressWarnings("unchecked")
    protected void mockFindByBusinessId(String businessId, EntityWithMetadata<T> response) {
        when(entityService.findByBusinessId(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        )).thenReturn((EntityWithMetadata) response);
    }

    protected void mockFindByBusinessIdNotFound(String businessId) {
        when(entityService.findByBusinessId(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        )).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    protected void mockFindByBusinessIdOrNull(String businessId, EntityWithMetadata<T> response) {
        when(entityService.findByBusinessIdOrNull(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        )).thenReturn((EntityWithMetadata) response);
    }

    protected void mockFindByBusinessIdOrNullNotFound(String businessId) {
        when(entityService.findByBusinessIdOrNull(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        )).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    protected void mockUpdate(UUID id, EntityWithMetadata<T> response) {
        when(entityService.update(eq(id), any(), anyString()))
                .thenReturn((EntityWithMetadata) response);
    }

    @SuppressWarnings("unchecked")
    protected void mockUpdateByBusinessId(EntityWithMetadata<T> response) {
        when(entityService.updateByBusinessId(any(), eq(getBusinessIdField()), anyString()))
                .thenReturn((EntityWithMetadata) response);
    }

    @SuppressWarnings("unchecked")
    protected void mockFindAll(List<EntityWithMetadata<T>> response) {
        when(entityService.findAll(any(ModelSpec.class), any(Class.class)))
                .thenReturn((List) response);
    }

    @SuppressWarnings("unchecked")
    protected void mockSearch(List<EntityWithMetadata<T>> response) {
        when(entityService.search(any(ModelSpec.class), any(GroupCondition.class), any(Class.class)))
                .thenReturn((List) response);
    }

    protected void mockDeleteById(UUID id) {
        when(entityService.deleteById(any(UUID.class))).thenReturn(id);
    }

    // ========================================
    // ASSERTION HELPERS
    // ========================================

    protected void assertMetadata(EntityWithMetadata<T> result, UUID expectedId, String expectedState) {
        assertNotNull(result);
        assertNotNull(result.metadata());
        assertEquals(expectedId, result.metadata().getId());
        assertEquals(expectedState, result.metadata().getState());
    }

    protected void assertMetadata(EntityWithMetadata<T> result, UUID expectedId) {
        assertMetadata(result, expectedId, "VALIDATED");
    }

    protected void assertBusinessId(T entity, String expectedBusinessId) {
        assertEquals(expectedBusinessId, getBusinessId(entity));
    }

    protected void assertEntityServiceCreateCalled(int times) {
        verify(entityService, times(times)).create(any());
    }

    protected void assertEntityServiceGetByIdCalled(UUID id, int times) {
        verify(entityService, times(times)).getById(eq(id), any(ModelSpec.class), any(Class.class));
    }

    protected void assertEntityServiceFindByBusinessIdCalled(String businessId, int times) {
        verify(entityService, times(times)).findByBusinessId(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        );
    }

    protected void assertEntityServiceFindByBusinessIdOrNullCalled(String businessId, int times) {
        verify(entityService, times(times)).findByBusinessIdOrNull(
                any(ModelSpec.class),
                eq(businessId),
                eq(getBusinessIdField()),
                any(Class.class)
        );
    }

    protected void assertEntityServiceUpdateCalled(UUID id, int times) {
        verify(entityService, times(times)).update(eq(id), any(), anyString());
    }

    protected void assertEntityServiceUpdateByBusinessIdCalled(int times) {
        verify(entityService, times(times)).updateByBusinessId(any(), eq(getBusinessIdField()), anyString());
    }

    protected void assertEntityServiceDeleteByIdCalled(UUID id, int times) {
        verify(entityService, times(times)).deleteById(any(UUID.class));
    }

    protected void assertEntityServiceFindAllCalled(int times) {
        verify(entityService, times(times)).findAll(any(ModelSpec.class), any(Class.class));
    }

    protected void assertEntityServiceSearchCalled(int times) {
        verify(entityService, times(times)).search(any(ModelSpec.class), any(GroupCondition.class), any(Class.class));
    }

    // ========================================
    // EXCEPTION ASSERTION HELPERS
    // ========================================

    protected <E extends Exception> void assertThrowsWithMessage(
            Class<E> exceptionClass,
            String expectedMessage,
            Runnable operation
    ) {
        E exception = assertThrows(exceptionClass, operation::run);
        assertTrue(exception.getMessage().contains(expectedMessage),
                "Expected message to contain '" + expectedMessage + "' but was: " + exception.getMessage());
    }

    // ========================================
    // COMMON TEST PATTERNS
    // ========================================

    /**
     * Test pattern for create with duplicate business ID
     */
    protected void testCreateDuplicate(
            Function<T, EntityWithMetadata<T>> createMethod,
            Class<? extends RuntimeException> expectedExceptionClass
    ) {
        T entity = createValidEntity("TEST-001");
        EntityWithMetadata<T> existing = createEntityWithMetadata(entity, testEntityId);

        mockFindByBusinessIdOrNull("TEST-001", existing);

        assertThrowsWithMessage(
                expectedExceptionClass,
                "already exists",
                () -> createMethod.apply(entity)
        );

        assertEntityServiceFindByBusinessIdOrNullCalled("TEST-001", 1);
        assertEntityServiceCreateCalled(0);
    }

    /**
     * Test pattern for get by business ID not found
     */
    protected void testGetByBusinessIdNotFound(
            Function<String, EntityWithMetadata<T>> getMethod,
            Class<? extends RuntimeException> expectedExceptionClass
    ) {
        mockFindByBusinessIdNotFound("NONEXISTENT");

        assertThrowsWithMessage(
                expectedExceptionClass,
                "not found",
                () -> getMethod.apply("NONEXISTENT")
        );

        assertEntityServiceFindByBusinessIdCalled("NONEXISTENT", 1);
    }

    /**
     * Test pattern for successful retrieval by business ID
     */
    protected void testGetByBusinessIdSuccess(
            Function<String, EntityWithMetadata<T>> getMethod
    ) {
        T entity = createValidEntity("TEST-001");
        EntityWithMetadata<T> expected = createEntityWithMetadata(entity, testEntityId);

        mockFindByBusinessId("TEST-001", expected);

        EntityWithMetadata<T> result = getMethod.apply("TEST-001");

        assertNotNull(result);
        assertMetadata(result, testEntityId);
        assertEntityEquals(entity, result.entity());
        assertEntityServiceFindByBusinessIdCalled("TEST-001", 1);
    }
}

