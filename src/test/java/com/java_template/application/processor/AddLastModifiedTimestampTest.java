package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Test class for AddLastModifiedTimestamp processor to verify ProcessingChaining refactoring.
 * Tests the fluent entity processing with validation, transformation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AddLastModifiedTimestampTest {

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private ProcessorSerializer serializer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CyodaEventContext<EntityProcessorCalculationRequest> context;

    @Mock
    private EntityProcessorCalculationRequest request;

    @Mock
    private ProcessorSerializer.EntityProcessingChain<Pet> entityChain;

    @Mock
    private EntityProcessorCalculationResponse response;

    private AddLastModifiedTimestamp processor;

    @BeforeEach
    void setUp() {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);
        processor = new AddLastModifiedTimestamp(serializerFactory, objectMapper);
    }

    @Test
    @DisplayName("Should process valid Pet entity using ProcessingChaining")
    void shouldProcessValidPetEntity() {
        // Given
        when(context.getEvent()).thenReturn(request);
        when(request.getId()).thenReturn("test-request-123");
        
        // Mock the fluent chain
        when(serializer.withRequest(request)).thenReturn(mock(ProcessorSerializer.ProcessingChain.class));
        ProcessorSerializer.ProcessingChain processingChain = serializer.withRequest(request);
        when(processingChain.toEntity(Pet.class)).thenReturn(entityChain);
        when(entityChain.withErrorHandler(any())).thenReturn(entityChain);
        when(entityChain.validate(any(), anyString())).thenReturn(entityChain);
        when(entityChain.map(any())).thenReturn(entityChain);
        when(entityChain.complete()).thenReturn(response);

        // When
        EntityProcessorCalculationResponse result = processor.process(context);

        // Then
        assertNotNull(result);
        assertEquals(response, result);
        
        // Verify the chain was called correctly
        verify(processingChain).toEntity(Pet.class);
        verify(entityChain).withErrorHandler(any());
        verify(entityChain, times(2)).validate(any(), anyString());
        verify(entityChain, times(7)).map(any()); // Now 7 individual business logic steps: copy + 5 transformations + timestamp
        verify(entityChain).complete();
    }

    @Test
    @DisplayName("Should support Pet entity with correct version")
    void shouldSupportPetEntity() {
        // Given
        ModelSpec modelSpec = mock(ModelSpec.class);
        when(modelSpec.getName()).thenReturn("pet");
        when(modelSpec.getVersion()).thenReturn(Integer.parseInt(Config.ENTITY_VERSION));
        
        OperationSpecification.Entity entitySpec = mock(OperationSpecification.Entity.class);
        when(entitySpec.modelKey()).thenReturn(modelSpec);

        // When
        boolean supports = processor.supports(entitySpec);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should not support non-Pet entities")
    void shouldNotSupportNonPetEntity() {
        // Given
        ModelSpec modelSpec = mock(ModelSpec.class);
        when(modelSpec.getName()).thenReturn("dog");
        // Note: version doesn't matter since name is wrong

        OperationSpecification.Entity entitySpec = mock(OperationSpecification.Entity.class);
        when(entitySpec.modelKey()).thenReturn(modelSpec);

        // When
        boolean supports = processor.supports(entitySpec);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should not support wrong version")
    void shouldNotSupportWrongVersion() {
        // Given
        ModelSpec modelSpec = mock(ModelSpec.class);
        when(modelSpec.getName()).thenReturn("pet");
        when(modelSpec.getVersion()).thenReturn(999);
        
        OperationSpecification.Entity entitySpec = mock(OperationSpecification.Entity.class);
        when(entitySpec.modelKey()).thenReturn(modelSpec);

        // When
        boolean supports = processor.supports(entitySpec);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should return correct processor metadata")
    void shouldReturnCorrectMetadata() {
        // When & Then
        assertEquals("AddLastModifiedTimestamp", processor.getName());
        assertEquals("3.0.0", processor.getVersion());
        assertEquals(1, processor.getSupportedEntityTypes().size());
        assertTrue(processor.getSupportedEntityTypes().contains("pet"));
    }

    // Note: Private method testing removed as they are implementation details
    // The ProcessingChaining integration tests above verify the overall functionality
}
