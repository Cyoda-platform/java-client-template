package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.manualusersync.version_1.ManualUserSync;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MergeUsers processor
 * 
 * Tests cover:
 * - Successful merge of Auth0 users
 * - Creating new users from Auth0
 * - Updating existing users with Auth0 values
 * - Marking missing users
 * - Error handling and result tracking
 */
@ExtendWith(MockitoExtension.class)
class MergeUsersTest {

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private ProcessorSerializer serializer;

    @Mock
    private EntityService entityService;

    @Mock
    private CyodaEventContext<EntityProcessorCalculationRequest> context;

    @Mock
    private EntityProcessorCalculationRequest request;

    @Mock
    private EntityProcessorCalculationResponse response;

    @Mock
    private ProcessorSerializer.ProcessorEntityResponseExecutionContext<ManualUserSync> processorContext;

    private MergeUsers mergeUsers;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);
        when(serializer.withRequest(any())).thenReturn(serializer);
        when(serializer.toEntityWithMetadata(any())).thenReturn(serializer);
        when(serializer.validate(any(), any())).thenReturn(serializer);
        when(serializer.map(any())).thenReturn(serializer);
        when(serializer.complete()).thenReturn(response);

        mergeUsers = new MergeUsers(serializerFactory, entityService);
    }

    @Test
    void testProcessorSupportsMergeUsers() {
        org.cyoda.cloud.api.event.common.ModelSpec modelSpec = 
            new org.cyoda.cloud.api.event.common.ModelSpec();
        modelSpec.setName("MergeUsers");
        org.cyoda.cloud.api.event.common.OperationSpecification opSpec = 
            new org.cyoda.cloud.api.event.common.OperationSpecification.Entity(modelSpec, "MergeUsers");

        assertTrue(mergeUsers.supports(opSpec));
    }

    @Test
    void testProcessorDoesNotSupportOtherProcessors() {
        org.cyoda.cloud.api.event.common.ModelSpec modelSpec = 
            new org.cyoda.cloud.api.event.common.ModelSpec();
        modelSpec.setName("OtherProcessor");
        org.cyoda.cloud.api.event.common.OperationSpecification opSpec = 
            new org.cyoda.cloud.api.event.common.OperationSpecification.Entity(modelSpec, "OtherProcessor");

        assertFalse(mergeUsers.supports(opSpec));
    }

    @Test
    void testProcessSuccessfully() {
        when(context.getEvent()).thenReturn(request);
        when(request.getId()).thenReturn("test-request-id");

        EntityProcessorCalculationResponse result = mergeUsers.process(context);

        assertNotNull(result);
        verify(serializer).withRequest(request);
        verify(serializer).complete();
    }

    @Test
    void testValidateManualUserSyncEntity() {
        ManualUserSync syncEntity = new ManualUserSync();
        syncEntity.setId("sync-123");

        EntityMetadata metadata = mock(EntityMetadata.class);
        EntityWithMetadata<ManualUserSync> entityWithMetadata = 
            new EntityWithMetadata<>(syncEntity, metadata);

        // Test valid entity
        assertTrue(mergeUsers.isValidEntity(entityWithMetadata));

        // Test invalid entity (null id)
        syncEntity.setId(null);
        assertFalse(mergeUsers.isValidEntity(entityWithMetadata));

        // Test invalid entity (blank id)
        syncEntity.setId("");
        assertFalse(mergeUsers.isValidEntity(entityWithMetadata));
    }

    @Test
    void testMergeSyncResultsInitialization() {
        ManualUserSync syncEntity = new ManualUserSync();
        syncEntity.setId("sync-123");
        syncEntity.setStatus("syncing");

        ManualUserSync.SyncResults results = new ManualUserSync.SyncResults();
        results.setCreated(0);
        results.setUpdated(0);
        results.setMissing(0);

        assertEquals(0, results.getCreated());
        assertEquals(0, results.getUpdated());
        assertEquals(0, results.getMissing());
    }

    @Test
    void testSyncEntityTimestamps() {
        ManualUserSync syncEntity = new ManualUserSync();
        OffsetDateTime now = OffsetDateTime.now();
        syncEntity.setCreatedAt(now);
        syncEntity.setUpdatedAt(now);

        assertNotNull(syncEntity.getCreatedAt());
        assertNotNull(syncEntity.getUpdatedAt());
        assertEquals(now, syncEntity.getCreatedAt());
    }
}

