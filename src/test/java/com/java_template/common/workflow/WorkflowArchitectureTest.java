package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.entity.PetEntity;
import com.java_template.common.workflow.entity.PetFetchRequestEntity;
import com.java_template.entity.pet.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify the new workflow architecture works correctly.
 */
public class WorkflowArchitectureTest {

    private ObjectMapper objectMapper;
    private WorkflowFactory workflowFactory;
    private Workflow petWorkflow;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        petWorkflow = new Workflow();

        // Create handler beans map for factory
        Map<String, WorkflowHandler> handlerBeans = new HashMap<>();
        handlerBeans.put("petWorkflow", petWorkflow);

        workflowFactory = new WorkflowFactory(handlerBeans);
    }

    @Test
    void testPetEntityCreationAndConversion() {
        // Create test data
        ObjectNode testData = objectMapper.createObjectNode();
        testData.put("id", 123L);
        testData.put("name", "Fluffy");
        testData.put("status", "AVAILABLE");
        testData.put("category", "Cat");

        // Test entity creation
        PetEntity petEntity = new PetEntity(testData);
        
        // Verify entity properties
        assertEquals(123L, petEntity.getId());
        assertEquals("Fluffy", petEntity.getName());
        assertEquals("AVAILABLE", petEntity.getStatus());
        assertEquals("Cat", petEntity.getCategory());
        assertEquals("pet", petEntity.getEntityType());
        assertTrue(petEntity.isValid());

        // Test conversion back to ObjectNode
        ObjectNode converted = petEntity.toObjectNode();
        assertEquals(123L, converted.get("id").asLong());
        assertEquals("Fluffy", converted.get("name").asText());
        assertEquals("AVAILABLE", converted.get("status").asText());
        assertEquals("Cat", converted.get("category").asText());
    }

    @Test
    void testPetFetchRequestEntityCreationAndConversion() {
        // Create test data
        ObjectNode testData = objectMapper.createObjectNode();
        testData.put("sourceUrl", "https://petstore.swagger.io/v2/pet/findByStatus");
        testData.put("status", "available");

        // Test entity creation
        PetFetchRequestEntity entity = new PetFetchRequestEntity(testData);
        
        // Verify entity properties
        assertEquals("https://petstore.swagger.io/v2/pet/findByStatus", entity.getSourceUrl());
        assertEquals("available", entity.getStatus());
        assertEquals("petfetchrequest", entity.getEntityType());
        assertTrue(entity.isValid());

        // Test validation
        entity.validateRequest();
        assertTrue(entity.getValid());

        // Test conversion back to ObjectNode
        ObjectNode converted = entity.toObjectNode();
        assertEquals("https://petstore.swagger.io/v2/pet/findByStatus", converted.get("sourceUrl").asText());
        assertEquals("available", converted.get("status").asText());
        assertTrue(converted.get("valid").asBoolean());
    }

    @Test
    void testPetWorkflowHandler() throws Exception {
        // Test workflow handler interface
        assertEquals("pet", petWorkflow.getEntityType());
        String[] availableMethods = petWorkflow.getAvailableMethods();
        assertEquals(2, availableMethods.length);
        assertTrue(java.util.Arrays.asList(availableMethods).contains("normalizeStatus"));
        assertTrue(java.util.Arrays.asList(availableMethods).contains("addLastModifiedTimestamp"));

        // Create test entity
        ObjectNode testData = objectMapper.createObjectNode();
        testData.put("id", 456L);
        testData.put("name", "Rex");
        testData.put("status", "PENDING");

        PetEntity entity = petWorkflow.createEntity(testData);
        assertEquals("PENDING", entity.getStatus());

        // Test normalizeStatus workflow method
        CompletableFuture<PetEntity> result = petWorkflow.processEntity(entity, "normalizeStatus");
        PetEntity processedEntity = result.get();
        
        assertEquals("pending", processedEntity.getStatus()); // Should be lowercase now
        assertEquals("Rex", processedEntity.getName()); // Other properties unchanged
    }

    @Test
    void testWorkflowFactoryRegistration() {
        // Verify handler is registered (should be auto-registered in constructor)
        WorkflowHandler<? extends WorkflowEntity> handler = workflowFactory.getHandler("pet");
        assertNotNull(handler);
        assertEquals("pet", handler.getEntityType());

        // Verify methods are registered
        String[] registeredMethods = workflowFactory.getRegisteredMethods();
        assertTrue(registeredMethods.length >= 2);

        // Test method execution through factory
        ObjectNode testData = objectMapper.createObjectNode();
        testData.put("id", 789L);
        testData.put("name", "Buddy");
        testData.put("status", "AVAILABLE");

        CompletableFuture<ObjectNode> result = workflowFactory.processWorkflow("pet", "normalizeStatus", testData);
        assertNotNull(result);
    }

    @Test
    void testPetEntityBusinessLogic() {
        PetEntity entity = new PetEntity();
        entity.setId(100L);
        entity.setName("Test Pet");
        entity.setStatus("AVAILABLE");
        
        // Test status normalization
        entity.normalizeStatus();
        assertEquals("available", entity.getStatus());
        
        // Test timestamp addition
        assertNull(entity.getLastModified());
        entity.addLastModifiedTimestamp();
        assertNotNull(entity.getLastModified());
        
        // Test status presence check
        assertTrue(entity.hasStatus());
        
        entity.setStatus("");
        assertFalse(entity.hasStatus());
    }

    @Test
    void testInvalidPetEntity() {
        PetEntity entity = new PetEntity();
        // No ID or name set
        assertFalse(entity.isValid());
        
        entity.setId(123L);
        assertFalse(entity.isValid()); // Still no name
        
        entity.setName("Valid Name");
        assertTrue(entity.isValid()); // Now valid
    }
}
