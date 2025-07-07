package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.entity.pet.Pet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify the new processor-based architecture works correctly.
 */
public class ProcessorArchitectureTest {

    private ObjectMapper objectMapper;
    private WorkflowProcessor workflowProcessor;
    private ProcessorFactory processorFactory;
    private EntityTypeResolver entityTypeResolver;
    private CyodaProcessor<Pet> mockProcessor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entityTypeResolver = new EntityTypeResolver(objectMapper);
        mockProcessor = mock(CyodaProcessor.class);

        // Mock the getEntityType method to return Pet.class
        when(mockProcessor.getEntityType()).thenReturn(Pet.class);

        // Create processor factory with mock processors (using lowercase bean names)
        Map<String, CyodaProcessor> processorBeans = new HashMap<>();
        processorBeans.put("normalizestatus", mockProcessor);
        processorBeans.put("faultyprocessor", mockProcessor);

        processorFactory = new ProcessorFactory(processorBeans);
        workflowProcessor = new WorkflowProcessor(processorFactory, entityTypeResolver);
    }

    @Test
    void testProcessorDispatch() throws Exception {
        // Setup
        ObjectNode testPayload = objectMapper.createObjectNode();
        testPayload.put("id", 123L);
        testPayload.put("name", "Test Pet");
        testPayload.put("status", "AVAILABLE");

        // Create expected Pet entity result
        Pet expectedPet = new Pet();
        expectedPet.setId(123L);
        expectedPet.setName("Test Pet");
        expectedPet.setStatus("available"); // normalized

        // Mock the processor to return processed Pet entity
        when(mockProcessor.process(any(Pet.class)))
            .thenReturn(CompletableFuture.completedFuture(expectedPet));

        // Execute (WorkflowProcessor will convert to lowercase)
        CompletableFuture<ObjectNode> result = workflowProcessor.processEvent("normalizeStatus", testPayload);

        // Verify
        assertNotNull(result);
        ObjectNode actualResult = result.get();
        assertEquals("available", actualResult.get("status").asText());
        assertEquals("Test Pet", actualResult.get("name").asText());
        assertEquals(123L, actualResult.get("id").asLong());

        // Verify the processor was called with Pet entity (not ObjectNode)
        verify(mockProcessor).process(any(Pet.class));
    }

    @Test
    void testProcessorNotFound() throws Exception {
        // Setup
        ObjectNode testPayload = objectMapper.createObjectNode();
        testPayload.put("test", "data");

        // Execute with non-existent processor
        CompletableFuture<ObjectNode> result = workflowProcessor.processEvent("nonExistentProcessor", testPayload);

        // Verify
        assertNotNull(result);
        ObjectNode actualResult = result.get();
        assertFalse(actualResult.get("success").asBoolean());
        assertTrue(actualResult.get("error").asText().contains("No processor found"));
    }

    @Test
    void testProcessorException() throws Exception {
        // Setup
        ObjectNode testPayload = objectMapper.createObjectNode();
        testPayload.put("id", 456L);
        testPayload.put("name", "Test Pet");
        testPayload.put("status", "AVAILABLE");

        // Mock processor that throws exception
        when(mockProcessor.process(any(Pet.class)))
            .thenThrow(new RuntimeException("Processing failed"));

        // Execute (WorkflowProcessor will convert to lowercase)
        CompletableFuture<ObjectNode> result = workflowProcessor.processEvent("faultyProcessor", testPayload);

        // Verify error handling
        assertNotNull(result);
        ObjectNode actualResult = result.get();
        assertFalse(actualResult.get("success").asBoolean());
        assertTrue(actualResult.get("error").asText().contains("Processing failed"));
    }

    @Test
    void testProcessorFactoryFunctionality() {
        // Test factory methods (using lowercase bean names)
        assertTrue(processorFactory.hasProcessor("normalizestatus"));
        assertFalse(processorFactory.hasProcessor("nonexistent"));
        assertEquals(2, processorFactory.getProcessorCount());

        String[] processors = processorFactory.getRegisteredProcessors();
        assertEquals(2, processors.length);

        CyodaProcessor<? extends CyodaEntity> processor = processorFactory.getProcessor("normalizestatus");
        assertNotNull(processor);
        assertEquals(mockProcessor, processor);

        // Test entity type resolution
        Class<? extends CyodaEntity> entityType = processorFactory.getEntityType("normalizestatus");
        assertEquals(Pet.class, entityType);
    }
}
