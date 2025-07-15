package com.java_template.application.criteria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.pet.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.DataPayload;
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
 * ABOUTME: Demonstration test showing the enhanced EvaluationChain capabilities.
 * Shows how to use EvaluationOutcome sealed classes and different attachment strategies.
 */
class EvaluationChainDemoTest {

    private CriterionSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(List.of(), List.of((CriterionSerializer) criterionSerializer));
        serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Test
    @DisplayName("Demonstrates basic EvaluationChain with outcomes attached to warnings")
    void demonstrateBasicEvaluationChainWithOutcomes() {
        // Given
        EntityCriteriaCalculationRequest request = createRequest(createInvalidPetData());

        // When - Using the enhanced EvaluationChain with outcomes
        EntityCriteriaCalculationResponse response = serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validatePet)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getMatches());

        // Verify detailed reason is attached to warnings
        assertNotNull(response.getWarnings());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("STRUCTURAL_FAILURE"));
        assertTrue(response.getWarnings().get(0).contains("Pet ID must be positive"));
    }

    @Test
    @DisplayName("Demonstrates different reason attachment strategies")
    void demonstrateDifferentAttachmentStrategies() {
        // Test 1: No attachment strategy (reasons are not attached)
        EntityCriteriaCalculationRequest request1 = createRequest(createInvalidPetData());
        EntityCriteriaCalculationResponse response1 = serializer.withRequest(request1)
            .evaluateEntity(Pet.class, this::validatePet)
            .withReasonAttachment(ReasonAttachmentStrategy.none())
            .complete();

        assertFalse(response1.getMatches());
        assertTrue(response1.getWarnings() == null || response1.getWarnings().isEmpty());

        // Test 2: Success case - no warnings should be attached since Success outcome has no reason
        EntityCriteriaCalculationRequest request2 = createRequest(createValidPetData());
        EntityCriteriaCalculationResponse response2 = serializer.withRequest(request2)
            .evaluateEntity(Pet.class, this::validatePet)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();

        assertTrue(response2.getMatches());
        assertTrue(response2.getWarnings() == null || response2.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("Demonstrates simple outcome-based evaluation")
    void demonstrateSimpleOutcomeEvaluation() {
        // Given
        EntityCriteriaCalculationRequest request = createRequest(createValidPetData());

        // When - Using simple outcome-based evaluation
        EntityCriteriaCalculationResponse response = serializer.withRequest(request)
            .evaluateEntity(Pet.class, pet -> pet.isValid() ?
                EvaluationOutcome.success() :
                EvaluationOutcome.Fail.structuralFailure("Pet validation failed"))
            .complete();

        // Then
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getMatches());

        // No warnings should be attached for success
        assertTrue(response.getWarnings() == null || response.getWarnings().isEmpty());
    }

    // Helper methods

    private EvaluationOutcome validatePet(Pet pet) {
        if (pet == null) {
            return EvaluationOutcome.Fail.structuralFailure("Pet entity is null");
        }

        if (!pet.isValid()) {
            return EvaluationOutcome.Fail.structuralFailure("Pet entity failed basic validation (missing required fields)");
        }

        if (pet.getId() != null && pet.getId() <= 0) {
            return EvaluationOutcome.Fail.structuralFailure("Pet ID must be positive, got: " + pet.getId());
        }

        return EvaluationOutcome.success();
    }

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

    private ObjectNode createInvalidPetData() {
        ObjectNode petData = createValidPetData();
        petData.put("id", -1); // Invalid ID
        return petData;
    }

    private EntityCriteriaCalculationRequest createRequest(ObjectNode petData) {
        EntityCriteriaCalculationRequest request = new EntityCriteriaCalculationRequest();
        request.setId("demo-123");
        request.setRequestId("demo-123");
        request.setEntityId("entity-456");

        DataPayload payload = new DataPayload();
        payload.setData(petData);
        request.setPayload(payload);

        return request;
    }
}
