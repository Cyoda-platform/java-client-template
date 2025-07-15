package com.java_template.application.criteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ABOUTME: Test class for IsValidPet criteria implementation using enhanced EvaluationChain approach.
 * Verifies that the refactored implementation correctly validates Pet entities and provides detailed reasons.
 */
class IsValidPetTest {

    private IsValidPet isValidPet;
    private ObjectMapper objectMapper;
    private SerializerFactory serializerFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        isValidPet = new IsValidPet(serializerFactory);
    }

    @Test
    @DisplayName("Should return match for valid Pet entity and not add warnings")
    void testValidPet() {
        // Given
        CyodaEventContext<EntityCriteriaCalculationRequest> context = createEventContext(
            createValidPetData()
        );

        // When
        EntityCriteriaCalculationResponse response = isValidPet.check(context);

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches());
        assertEquals("test-123", response.getId());
        assertEquals("entity-456", response.getEntityId());

        // Verify that no warnings are added for successful evaluation
        assertTrue(response.getWarnings() == null || response.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("Should return non-match for Pet with invalid ID and include reason in warnings")
    void testInvalidPetId() {
        // Given
        ObjectNode petData = createValidPetData();
        petData.put("id", -1); // Invalid ID
        CyodaEventContext<EntityCriteriaCalculationRequest> context = createEventContext(petData);

        // When
        EntityCriteriaCalculationResponse response = isValidPet.check(context);

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());

        // Verify that the reason is attached to warnings
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("Pet ID must be positive"));
        assertTrue(response.getWarnings().get(0).contains("STRUCTURAL_FAILURE"));
    }

    @Test
    @DisplayName("Should return non-match for Pet with empty name and include reason in warnings")
    void testInvalidPetName() {
        // Given
        ObjectNode petData = createValidPetData();
        petData.put("name", ""); // Empty name
        CyodaEventContext<EntityCriteriaCalculationRequest> context = createEventContext(petData);

        // When
        EntityCriteriaCalculationResponse response = isValidPet.check(context);

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());

        // Verify that the reason is attached to warnings
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("Pet entity failed basic validation"));
        assertTrue(response.getWarnings().get(0).contains("STRUCTURAL_FAILURE"));
    }

    @Test
    @DisplayName("Should return non-match for Pet with invalid status and include reason in warnings")
    void testInvalidPetStatus() {
        // Given
        ObjectNode petData = createValidPetData();
        petData.put("status", "invalid_status"); // Invalid status
        CyodaEventContext<EntityCriteriaCalculationRequest> context = createEventContext(petData);

        // When
        EntityCriteriaCalculationResponse response = isValidPet.check(context);

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());

        // Verify that the reason is attached to warnings
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("Pet status 'invalid_status' is invalid"));
        assertTrue(response.getWarnings().get(0).contains("BUSINESS_RULE_FAILURE"));
    }

    @Test
    @DisplayName("Should return non-match for Pet with too many tags and include reason in warnings")
    void testTooManyTags() {
        // Given
        ObjectNode petData = createValidPetData();
        // Add more than 10 tags
        var tagsArray = objectMapper.createArrayNode();
        List.of("tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10", "tag11")
            .forEach(tagsArray::add);
        petData.set("tags", tagsArray);
        CyodaEventContext<EntityCriteriaCalculationRequest> context = createEventContext(petData);

        // When
        EntityCriteriaCalculationResponse response = isValidPet.check(context);

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());

        // Verify that the reason is attached to warnings
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("Pet has too many tags (11)"));
        assertTrue(response.getWarnings().get(0).contains("BUSINESS_RULE_FAILURE"));
    }



    @Test
    @DisplayName("Should support Pet entity operations")
    void testSupports() {
        // Given
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(1000); // Use the actual ENTITY_VERSION from Config
        OperationSpecification.Entity entitySpec = new OperationSpecification.Entity(modelSpec, "pet");

        // When
        boolean supports = isValidPet.supports(entitySpec);

        // Then
        assertTrue(supports);
    }

    @Test
    @DisplayName("Should not support non-Pet entity operations")
    void testDoesNotSupport() {
        // Given
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("user");
        modelSpec.setVersion(1);
        OperationSpecification.Entity entitySpec = new OperationSpecification.Entity(modelSpec, "user");

        // When
        boolean supports = isValidPet.supports(entitySpec);

        // Then
        assertFalse(supports);
    }

    @Test
    @DisplayName("Should provide detailed reasons for different validation failure categories")
    void testDetailedValidationReasons() {
        // Test structural failure (invalid ID)
        ObjectNode structuralFailureData = createValidPetData();
        structuralFailureData.put("id", 0);
        EntityCriteriaCalculationResponse structuralResponse = isValidPet.check(createEventContext(structuralFailureData));

        assertFalse(structuralResponse.getMatches());
        assertNotNull(structuralResponse.getWarnings());
        assertTrue(structuralResponse.getWarnings().get(0).contains("STRUCTURAL_FAILURE"));
        assertTrue(structuralResponse.getWarnings().get(0).contains("Pet ID must be positive"));

        // Test business rule failure (invalid status)
        ObjectNode businessFailureData = createValidPetData();
        businessFailureData.put("status", "invalid");
        EntityCriteriaCalculationResponse businessResponse = isValidPet.check(createEventContext(businessFailureData));

        assertFalse(businessResponse.getMatches());
        assertNotNull(businessResponse.getWarnings());
        assertTrue(businessResponse.getWarnings().get(0).contains("BUSINESS_RULE_FAILURE"));
        assertTrue(businessResponse.getWarnings().get(0).contains("Pet status 'invalid' is invalid"));

        // Test data quality failure (invalid URL)
        ObjectNode qualityFailureData = createValidPetData();
        qualityFailureData.set("photoUrls", objectMapper.createArrayNode().add("invalid-url"));
        EntityCriteriaCalculationResponse qualityResponse = isValidPet.check(createEventContext(qualityFailureData));

        assertFalse(qualityResponse.getMatches());
        assertNotNull(qualityResponse.getWarnings());
        assertTrue(qualityResponse.getWarnings().get(0).contains("DATA_QUALITY_FAILURE"));
        assertTrue(qualityResponse.getWarnings().get(0).contains("Pet photo URL at index 0 is invalid"));
    }

    @Test
    @DisplayName("Should demonstrate logical chaining stops at first failure")
    void testLogicalChainingStopsAtFirstFailure() {
        // Create Pet data with multiple issues - ID and status both invalid
        ObjectNode multipleIssuesData = createValidPetData();
        multipleIssuesData.put("id", -1); // First failure: invalid ID
        multipleIssuesData.put("status", "invalid_status"); // Second failure: invalid status

        EntityCriteriaCalculationResponse response = isValidPet.check(createEventContext(multipleIssuesData));

        // Should fail and return the FIRST failure encountered (ID validation)
        assertFalse(response.getMatches());
        assertNotNull(response.getWarnings());
        assertEquals(1, response.getWarnings().size()); // Only one warning - the first failure
        assertTrue(response.getWarnings().get(0).contains("STRUCTURAL_FAILURE"));
        assertTrue(response.getWarnings().get(0).contains("Pet ID must be positive"));
        // Should NOT contain the status error since chaining stopped at first failure
        assertFalse(response.getWarnings().get(0).contains("status"));
    }

    // Helper methods

    private ObjectNode createValidPetData() {
        ObjectNode petData = objectMapper.createObjectNode();
        petData.put("id", 123L);
        petData.put("name", "Fluffy");
        petData.put("status", "available");
        petData.put("category", "dog");
        petData.set("photoUrls", objectMapper.createArrayNode().add("https://example.com/photo.jpg"));
        petData.set("tags", objectMapper.createArrayNode().add("friendly").add("small"));
        return petData;
    }

    private CyodaEventContext<EntityCriteriaCalculationRequest> createEventContext(ObjectNode petData) {
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();
        request.setId("test-123");
        request.setRequestId("test-123");
        request.setEntityId("entity-456");

        DataPayload payload = new DataPayload();
        payload.setData(petData);
        request.setPayload(payload);

        return new CyodaEventContext<>() {
            @Override
            public CloudEvent getCloudEvent() {
                return mock(CloudEvent.class);
            }

            @Override
            public @NotNull EntityCriteriaCalculationRequest getEvent() {
                return request;
            }
        };
    }
}
