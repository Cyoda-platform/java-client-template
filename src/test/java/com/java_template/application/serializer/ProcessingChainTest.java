package com.java_template.application.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.ResponseBuilder;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Comprehensive unit tests for ProcessingChain design that assert correct functionality and illustrate usage patterns.
 * Tests cover basic chaining, error handling, entity processing, and practical usage scenarios.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessingChainTest {

    @Mock
    private ResponseBuilder.ProcessorResponseBuilder mockResponseBuilder;

    @Mock
    private EntityProcessorCalculationRequest mockRequest;

    @Mock
    private DataPayload mockPayload;

    private ObjectMapper objectMapper;
    private ObjectNode testPayload;
    private TestEntity testEntity;
    private TestProcessorSerializer testSerializer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create test payload
        testPayload = objectMapper.createObjectNode();
        testPayload.put("id", 123L);
        testPayload.put("name", "Fluffy");
        testPayload.put("status", "available");

        // Create test entity
        testEntity = new TestEntity(123L, "Fluffy", "available", "cat");

        // Setup common mock behavior
        when(mockRequest.getId()).thenReturn("test-request-123");
        when(mockRequest.getRequestId()).thenReturn("req-456");
        when(mockRequest.getEntityId()).thenReturn("entity-789");
        when(mockRequest.getPayload()).thenReturn(mockPayload);

        // Setup response builder chain
        when(mockResponseBuilder.withSuccess(any(JsonNode.class))).thenReturn(mockResponseBuilder);
        when(mockResponseBuilder.withError(anyString(), anyString())).thenReturn(mockResponseBuilder);
        when(mockResponseBuilder.build()).thenReturn(mock(EntityProcessorCalculationResponse.class));

        // Create test serializer
        testSerializer = new TestProcessorSerializer();
    }

    /**
     * Test entity for ProcessingChain testing.
     */
    private record TestEntity(Long id, String name, String status, String category) implements CyodaEntity {
        @Override
        public boolean isValid() {
            return id != null && name != null && !name.trim().isEmpty();
        }

        @Override
        public OperationSpecification getModelKey() {
            ModelSpec modelSpec = new ModelSpec();
            modelSpec.setName("test-entity");
            modelSpec.setVersion(1);
            return new OperationSpecification.Entity(modelSpec, "test-entity");
        }

    }

    /**
     * Test implementation of ProcessorSerializer for testing ProcessingChain functionality.
     */
    private class TestProcessorSerializer implements ProcessorSerializer {
        private boolean shouldThrowOnExtractPayload = false;
        private boolean shouldThrowOnExtractEntity = false;
        private RuntimeException extractPayloadException;
        private RuntimeException extractEntityException;
        @Setter
        private TestEntity customEntity = null;

        public void setShouldThrowOnExtractPayload(RuntimeException exception) {
            this.shouldThrowOnExtractPayload = true;
            this.extractPayloadException = exception;
        }

        public void setShouldThrowOnExtractEntity(RuntimeException exception) {
            this.shouldThrowOnExtractEntity = true;
            this.extractEntityException = exception;
        }

        @Override
        public <T extends CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz) {
            if (shouldThrowOnExtractEntity) {
                throw extractEntityException;
            }
            if (clazz == TestEntity.class) {
                TestEntity entityToReturn = customEntity != null ? customEntity : testEntity;
                return clazz.cast(entityToReturn);
            }
            throw new IllegalArgumentException("Unsupported entity type: " + clazz.getName());
        }

        @Override
        public JsonNode extractPayload(EntityProcessorCalculationRequest request) {
            if (shouldThrowOnExtractPayload) {
                throw extractPayloadException;
            }
            return testPayload;
        }

        @Override
        public <T extends CyodaEntity> JsonNode entityToJsonNode(T entity) {
            if (entity instanceof TestEntity(Long id, String name, String status, String category)) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", id);
                node.put("name", name);
                node.put("status", status);
                node.put("category", category);
                return node;
            }
            throw new IllegalArgumentException("Unsupported entity type: " + entity.getClass().getName());
        }

        @Override
        public String getType() {
            return "test-serializer";
        }

        @Override
        public ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request) {
            return mockResponseBuilder;
        }
    }

    @Test
    @DisplayName("withRequest should initialize ProcessingChain with extracted payload")
    void testWithRequestInitialization() {
        // When
        ProcessorSerializer.ProcessingChain processingChain = testSerializer.withRequest(mockRequest);

        // Then
        assertNotNull(processingChain);
        // ProcessingChain should be successfully created without throwing exceptions
    }

    @Test
    @DisplayName("map should transform JSON payload and allow chaining")
    void testMapTransformation() {

        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(node -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("transformedId", node.get("id").asLong() * 2);
                    result.put("transformedName", "Transformed: " + node.get("name").asText());
                    return result;
                })
                .complete();

        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("toEntity should extract entity and initiate entity flow")
    void testToEntityTransformation() {

        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entity -> new TestEntity(entity.id(), entity.name().toUpperCase(), "processed", entity.category()))
                .complete();

        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Multiple chained operations should work correctly")
    void testChainedOperations() {
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(node -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("step1", "First transformation");
                    result.put("originalId", node.get("id").asLong());
                    return result;
                })
                .map(node -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("step2", "Second transformation");
                    result.put("previousStep", node.get("step1").asText());
                    result.put("finalId", node.get("originalId").asLong() * 10);
                    return result;
                })
                .complete();

        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Error during payload extraction should be handled by complete()")
    void testErrorDuringPayloadExtraction() {
        // Given
        RuntimeException extractionError = new RuntimeException("Payload extraction failed");
        testSerializer.setShouldThrowOnExtractPayload(extractionError);

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Payload extraction failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Error during map operation should be handled by complete()")
    void testErrorDuringMapOperation() {
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(node -> {
                    throw new RuntimeException("Mapping operation failed");
                })
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Mapping operation failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Error during toEntity operation should be handled by complete()")
    void testErrorDuringToEntityOperation() {
        // Given
        RuntimeException entityError = new RuntimeException("Entity extraction failed");
        testSerializer.setShouldThrowOnExtractEntity(entityError);

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entity -> entity)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Entity extraction failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("withErrorHandler should handle errors with custom error handler")
    void testWithErrorHandlerWithCustomErrorHandler() {
        // Given
        RuntimeException processingError = new RuntimeException("Custom processing error");
        testSerializer.setShouldThrowOnExtractPayload(processingError);

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .withErrorHandler((error, data) ->
                        new ErrorInfo("CUSTOM_ERROR", "Custom error: " + error.getMessage())
                )
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("CUSTOM_ERROR", "Custom error: Custom processing error");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("withErrorHandler should return success response when no errors occur")
    void testWithErrorHandlerWithoutErrors() {
        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .withErrorHandler((error, data) ->
                        new ErrorInfo("SHOULD_NOT_BE_CALLED", "Should not be called")
                )
                .map(node -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    result.put("success", true);
                    return result;
                })
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
        verify(mockResponseBuilder, never()).withError(anyString(), anyString());
    }

    // ========================================
    // PRACTICAL USAGE PATTERN TESTS
    // ========================================

    @Test
    @DisplayName("Practical example: TestEntity status normalization with entity flow")
    void testEntityStatusNormalizationPattern() {
        // Given - A TestEntity with uppercase status that needs normalization
        TestEntity entityWithUppercaseStatus = new TestEntity(456L, "Rex", "AVAILABLE", "DOG");

        testSerializer.setCustomEntity(entityWithUppercaseStatus);

        Function<TestEntity, TestEntity> entityNormalizer = entity -> {
            // Normalize the entity data
            return new TestEntity(
                    entity.id(),
                    entity.name(),
                    entity.status().toLowerCase(), // Normalize status
                    entity.category().toLowerCase() // Normalize category
            );
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityNormalizer)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Practical example: Data enrichment pipeline with multiple transformations")
    void testDataEnrichmentPipeline() {
        // Given - A pipeline that enriches pet data step by step
        Function<JsonNode, JsonNode> addMetadata = node -> {
            ObjectNode enriched = node.deepCopy();
            enriched.put("enrichmentStep", "metadata");
            enriched.put("processedAt", "2025-07-12T10:00:00Z");
            return enriched;
        };

        Function<JsonNode, JsonNode> addValidation = node -> {
            ObjectNode validated = node.deepCopy();
            validated.put("enrichmentStep", "validation");
            validated.put("isValid", node.has("name") && node.has("id"));
            return validated;
        };

        Function<JsonNode, JsonNode> addBusinessLogic = node -> {
            ObjectNode processed = node.deepCopy();
            processed.put("enrichmentStep", "business_logic");
            processed.put("category", "processed");
            processed.put("finalStep", true);
            return processed;
        };

        // When - Chain multiple transformations
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(addMetadata)
                .map(addValidation)
                .map(addBusinessLogic)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Practical example: Entity validation with custom error handling")
    void testEntityValidationWithCustomErrorHandling() {
        // Given - A TestEntity entity that will fail validation
        TestEntity invalidEntity = new TestEntity(null, "", "invalid_status", null);

        testSerializer.setCustomEntity(invalidEntity);

        Function<TestEntity, TestEntity> entityValidator = entity -> {
            if (entity.id() == null) {
                throw new IllegalArgumentException("TestEntity ID cannot be null");
            }
            if (entity.name() == null || entity.name().trim().isEmpty()) {
                throw new IllegalArgumentException("TestEntity name cannot be empty");
            }
            // If validation passes, return processed entity
            return new TestEntity(entity.id(), entity.name(), "validated", entity.category());
        };

        BiFunction<Throwable, TestEntity, ErrorInfo> validationErrorHandler =
                (error, entity) -> {
                    if (error instanceof IllegalArgumentException) {
                        return new ErrorInfo("VALIDATION_ERROR",
                                "TestEntity validation failed: " + error.getMessage());
                    }
                    return new ErrorInfo("PROCESSING_ERROR",
                            "Unexpected error during entity processing");
                };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .withErrorHandler(validationErrorHandler)
                .map(entityValidator)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("VALIDATION_ERROR", "TestEntity validation failed: TestEntity ID cannot be null");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Practical example: Mixed entity and JSON processing with toJsonFlow")
    void testMixedEntityAndJsonProcessing() {
        // Given - Process entity first, then switch to JSON flow
        Function<TestEntity, TestEntity> entityProcessor = entity -> new TestEntity(entity.id(), entity.name().toUpperCase(), "processed", entity.category());

        Function<TestEntity, JsonNode> entityToJson = entity -> {
            ObjectNode petJson = objectMapper.createObjectNode();
            petJson.put("petId", entity.id());
            petJson.put("petName", entity.name());
            petJson.put("petStatus", entity.status());
            return petJson;
        };

        Function<JsonNode, JsonNode> addBusinessData = node -> {
            ObjectNode enhanced = node.deepCopy();
            enhanced.put("businessCategory", "premium");
            enhanced.put("loyaltyPoints", 100);
            enhanced.put("lastUpdated", System.currentTimeMillis());
            return enhanced;
        };

        // When - Entity flow then JSON flow
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityProcessor)
                .toJsonFlow(entityToJson)
                .map(addBusinessData)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    // ========================================
    // EDGE CASE AND ERROR PROPAGATION TESTS
    // ========================================

    @Test
    @DisplayName("Error in first operation should prevent subsequent operations from executing")
    void testErrorPropagationPreventsSubsequentOperations() {
        // Given
        Function<JsonNode, JsonNode> faultyFirstMapper = node -> {
            throw new RuntimeException("First operation failed");
        };

        Function<JsonNode, JsonNode> secondMapper = node -> {
            // This should never be called due to error in first mapper
            ObjectNode result = objectMapper.createObjectNode();
            result.put("shouldNotBeReached", true);
            return result;
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(faultyFirstMapper)
                .map(secondMapper) // This should not execute
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "First operation failed");
        verify(mockResponseBuilder).build();
        // The second mapper should never be called due to error state
    }

    @Test
    @DisplayName("Error state should be preserved through multiple chained operations")
    void testErrorStatePreservation() {
        // Given
        RuntimeException initialError = new RuntimeException("Initial error");
        testSerializer.setShouldThrowOnExtractPayload(initialError);

        Function<JsonNode, JsonNode> mapper1 = node -> objectMapper.createObjectNode();
        Function<JsonNode, JsonNode> mapper2 = node -> objectMapper.createObjectNode();

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(mapper1) // Should not execute due to initial error
                .map(mapper2) // Should not execute due to initial error
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Initial error");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Null payload should be handled gracefully")
    void testNullPayloadHandling() {
        // Given
        testSerializer.setShouldThrowOnExtractPayload(new IllegalArgumentException("Cannot process null payload"));

        Function<JsonNode, JsonNode> mapper = node -> {
            // This should not be called due to extraction error
            return objectMapper.createObjectNode();
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(mapper)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Cannot process null payload");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Complex error handler should receive both error and processed data")
    void testComplexErrorHandlerWithData() {
        // Given
        ObjectNode partialData = objectMapper.createObjectNode();
        partialData.put("partialProcessing", true);
        partialData.put("step", "initial");

        // We need to modify the test serializer to return partial data
        testPayload = partialData;

        Function<JsonNode, JsonNode> faultyMapper = node -> {
            throw new RuntimeException("Processing failed at step 2");
        };

        BiFunction<Throwable, JsonNode, ErrorInfo> dataAwareErrorHandler =
                (error, data) -> {
                    String errorMessage = "Error: " + error.getMessage();
                    if (data != null && data.has("step")) {
                        errorMessage += " (failed at: " + data.get("step").asText() + ")";
                    }
                    return new ErrorInfo("DETAILED_ERROR", errorMessage);
                };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .withErrorHandler(dataAwareErrorHandler)
                .map(faultyMapper)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("DETAILED_ERROR",
                "Error: Processing failed at step 2 (failed at: initial)");
        verify(mockResponseBuilder).build();
    }

    // ========================================
    // INTEGRATION AND REAL-WORLD USAGE EXAMPLES
    // ========================================

    @Test
    @DisplayName("Integration example: Complete entity processing workflow")
    void testCompletePetProcessingWorkflow() {
        // Given - A realistic entity processing scenario
        TestEntity rawEntity = new TestEntity(789L, "  BUDDY  ", "AVAILABLE", "DOG");

        testSerializer.setCustomEntity(rawEntity);

        // Step 1: Extract and clean entity data
        Function<TestEntity, TestEntity> entityCleaner = entity -> new TestEntity(
                entity.id(),
                entity.name().trim().toLowerCase(),
                entity.status().toLowerCase(),
                entity.category().toLowerCase()
        );

        // Convert entity to JSON for further processing
        Function<TestEntity, JsonNode> entityToJson = entity -> {
            ObjectNode cleaned = objectMapper.createObjectNode();
            cleaned.put("id", entity.id());
            cleaned.put("name", entity.name());
            cleaned.put("status", entity.status());
            cleaned.put("category", entity.category());
            cleaned.put("cleaningStep", "completed");
            return cleaned;
        };

        // Step 2: Add business validation
        Function<JsonNode, JsonNode> businessValidator = node -> {
            ObjectNode validated = node.deepCopy();
            String status = node.get("status").asText();
            boolean isValidStatus = status.equals("available") || status.equals("pending") || status.equals("sold");
            validated.put("validationStep", "completed");
            validated.put("isValidStatus", isValidStatus);
            if (!isValidStatus) {
                validated.put("status", "pending"); // Default to pending for invalid status
            }
            return validated;
        };

        // Step 3: Add metadata and finalize
        Function<JsonNode, JsonNode> finalizer = node -> {
            ObjectNode finalized = node.deepCopy();
            finalized.put("processedAt", "2025-07-12T10:00:00Z");
            finalized.put("processingVersion", "1.0");
            finalized.put("finalStep", "completed");
            return finalized;
        };

        // When - Execute the complete workflow
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityCleaner)
                .toJsonFlow(entityToJson)
                .map(businessValidator)
                .map(finalizer)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Integration example: Error recovery with fallback processing")
    void testErrorRecoveryWithFallback() {
        // Given - A scenario where primary processing fails but fallback succeeds
        RuntimeException primaryError = new RuntimeException("Primary processing service unavailable");
        testSerializer.setShouldThrowOnExtractPayload(primaryError);

        BiFunction<Throwable, JsonNode, ErrorInfo> fallbackHandler =
                (error, data) -> {
                    // In a real scenario, this might trigger fallback processing
                    if (error.getMessage().contains("service unavailable")) {
                        return new ErrorInfo("FALLBACK_PROCESSING",
                                "Primary service unavailable, fallback processing initiated");
                    }
                    return new ErrorInfo("PROCESSING_ERROR",
                            "Unrecoverable error: " + error.getMessage());
                };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .withErrorHandler(fallbackHandler)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("FALLBACK_PROCESSING",
                "Primary service unavailable, fallback processing initiated");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Usage example: Simple data transformation pattern")
    void testSimpleDataTransformationPattern() {
        // This test demonstrates the most common usage pattern
        // Given
        Function<JsonNode, JsonNode> simpleTransform = node -> {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("originalId", node.get("id").asLong());
            result.put("transformedName", "Processed: " + node.get("name").asText());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        };

        // When - Simple one-step transformation
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .map(simpleTransform)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("Usage example: Entity-first processing pattern")
    void testEntityFirstProcessingPattern() {
        // This test demonstrates processing that starts with entity extraction
        // Given
        Function<TestEntity, TestEntity> entityProcessor = entity ->
                new TestEntity(entity.id(), entity.name().toUpperCase(),
                        "processed", entity.category());

        // When - Entity-first processing
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityProcessor)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    // ========================================
    // ENTITY PROCESSING CHAIN TESTS
    // ========================================

    @Test
    @DisplayName("EntityProcessingChain: Multiple entity transformations should chain correctly")
    void testMultipleEntityTransformations() {
        // Given
        Function<TestEntity, TestEntity> firstTransform = entity ->
                new TestEntity(entity.id(), entity.name().toUpperCase(), entity.status(), entity.category());

        Function<TestEntity, TestEntity> secondTransform = entity ->
                new TestEntity(entity.id(), entity.name(), "processed", entity.category());

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(firstTransform)
                .map(secondTransform)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: toJsonFlow should switch from entity to JSON processing")
    void testToJsonFlowTransition() {
        // Given
        Function<TestEntity, TestEntity> entityTransform = entity ->
                new TestEntity(entity.id(), entity.name().toUpperCase(), "processed", entity.category());

        Function<TestEntity, JsonNode> entityToJson = entity -> {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("entityId", entity.id());
            result.put("entityName", entity.name());
            result.put("entityStatus", entity.status());
            result.put("convertedFromEntity", true);
            return result;
        };

        Function<JsonNode, JsonNode> jsonTransform = node -> {
            ObjectNode enhanced = node.deepCopy();
            enhanced.put("jsonProcessingStep", "completed");
            enhanced.put("timestamp", System.currentTimeMillis());
            return enhanced;
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityTransform)
                .toJsonFlow(entityToJson)
                .map(jsonTransform)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: complete with custom converter should work")
    void testCompleteWithCustomConverter() {
        // Given
        Function<TestEntity, TestEntity> entityTransform = entity ->
                new TestEntity(entity.id(), entity.name().toUpperCase(),
                        "processed", entity.category());

        Function<TestEntity, JsonNode> customConverter = entity -> {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("customId", entity.id());
            result.put("customName", "Custom: " + entity.name());
            result.put("customStatus", entity.status());
            result.put("customProcessed", true);
            return result;
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityTransform)
                .complete(customConverter);

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: Error in entity transformation should be handled")
    void testEntityTransformationError() {
        // Given
        Function<TestEntity, TestEntity> faultyTransform = entity -> {
            throw new RuntimeException("Entity transformation failed");
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(faultyTransform)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Entity transformation failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: Error in toJsonFlow should be handled")
    void testToJsonFlowError() {
        // Given
        Function<TestEntity, TestEntity> entityTransform = entity -> entity;

        Function<TestEntity, JsonNode> faultyConverter = entity -> {
            throw new RuntimeException("JSON conversion failed");
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(entityTransform)
                .toJsonFlow(faultyConverter)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "JSON conversion failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: withErrorHandler with entity error handler should work")
    void testEntityWithErrorHandlerWithCustomErrorHandler() {
        // Given
        Function<TestEntity, TestEntity> faultyTransform = entity -> {
            throw new IllegalStateException("Entity validation failed");
        };

        BiFunction<Throwable, TestEntity, ErrorInfo> entityErrorHandler =
                (error, entity) -> {
                    if (error instanceof IllegalStateException) {
                        String entityInfo = entity != null ? " for entity ID: " + entity.id() : "";
                        return new ErrorInfo("ENTITY_VALIDATION_ERROR",
                                "Entity validation failed" + entityInfo);
                    }
                    return new ErrorInfo("ENTITY_PROCESSING_ERROR",
                            "Unexpected entity processing error");
                };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .withErrorHandler(entityErrorHandler)
                .map(faultyTransform)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("ENTITY_VALIDATION_ERROR", "Entity validation failed for entity ID: 123");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: Pure entity flow without JSON conversion")
    void testPureEntityFlow() {
        // Given - A complete entity processing flow
        Function<TestEntity, TestEntity> normalizeEntity = entity ->
                new TestEntity(entity.id(), entity.name().trim(), entity.status().toLowerCase(), entity.category());

        Function<TestEntity, TestEntity> validateEntity = entity -> {
            if (entity.name().isEmpty()) {
                throw new IllegalArgumentException("Entity name cannot be empty");
            }
            return entity;
        };

        Function<TestEntity, TestEntity> enrichEntity = entity ->
                new TestEntity(entity.id(), entity.name(), "enriched_" + entity.status(), entity.category());

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .map(normalizeEntity)
                .map(validateEntity)
                .map(enrichEntity)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: validate should pass for valid entities")
    void testValidateSuccess() {
        // Given
        Function<TestEntity, Boolean> validator = entity -> entity.id() != null && entity.name() != null;

        Function<TestEntity, TestEntity> processor = entity ->
                new TestEntity(entity.id(), entity.name().toUpperCase(),
                        "processed", entity.category());

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .validate(validator)
                .map(processor)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: validate should fail for invalid entities")
    void testValidateFailure() {
        // Given
        Function<TestEntity, Boolean> validator = entity -> entity.id() != null && entity.id() > 1000;

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .validate(validator)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Entity validation failed");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: validate with custom error message")
    void testValidateWithCustomErrorMessage() {
        // Given
        Function<TestEntity, Boolean> validator = entity -> entity.name().length() > 10;
        String customErrorMessage = "Entity name must be longer than 10 characters";

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .validate(validator, customErrorMessage)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", customErrorMessage);
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: validate with exception in validator should be handled")
    void testValidateWithException() {
        // Given
        Function<TestEntity, Boolean> faultyValidator = entity -> {
            throw new RuntimeException("Validator error");
        };

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .validate(faultyValidator)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withError("PROCESSING_ERROR", "Validator error");
        verify(mockResponseBuilder).build();
    }

    @Test
    @DisplayName("EntityProcessingChain: Complete workflow with validation")
    void testCompleteWorkflowWithValidation() {
        // Given - A complete entity processing flow with validation
        Function<TestEntity, Boolean> validator = entity -> entity.id() != null && entity.name() != null;

        Function<TestEntity, TestEntity> normalizer = entity ->
                new TestEntity(entity.id(), entity.name().trim().toLowerCase(), entity.status(), entity.category());

        Function<TestEntity, TestEntity> enricher = entity ->
                new TestEntity(entity.id(), entity.name(), "enriched_" + entity.status(), entity.category());

        // When
        EntityProcessorCalculationResponse response = testSerializer.withRequest(mockRequest)
                .toEntity(TestEntity.class)
                .validate(validator)
                .map(normalizer)
                .validate(e -> !e.name().isEmpty(), "Name cannot be empty after normalization")
                .map(enricher)
                .complete();

        // Then
        assertNotNull(response);
        verify(mockResponseBuilder).withSuccess(any(JsonNode.class));
        verify(mockResponseBuilder).build();
    }
}
