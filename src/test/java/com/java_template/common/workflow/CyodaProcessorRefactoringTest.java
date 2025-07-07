package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.entity.pet.Pet;
import com.java_template.entity.petfetchrequest.PetFetchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the CyodaProcessor refactoring works correctly.
 * Tests that processors now work with CyodaEntity types instead of ObjectNode.
 */
@SpringBootTest(classes = com.java_template.Application.class)
public class CyodaProcessorRefactoringTest {

    @Autowired
    private WorkflowProcessor workflowProcessor;

    @Autowired
    private ProcessorFactory processorFactory;

    @Autowired
    private EntityTypeResolver entityTypeResolver;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testProcessorFactoryRegistersProcessorsWithEntityTypes() {
        // Verify processors are registered
        assertTrue(processorFactory.hasProcessor("addlastmodifiedtimestamp"));
        assertTrue(processorFactory.hasProcessor("normalizestatus"));
        assertTrue(processorFactory.hasProcessor("processpetfetchrequest"));
        assertTrue(processorFactory.hasProcessor("isfetchrequestvalid"));
        assertTrue(processorFactory.hasProcessor("noop"));

        // Verify entity types are correctly resolved
        assertEquals(Pet.class, processorFactory.getEntityType("addlastmodifiedtimestamp"));
        assertEquals(Pet.class, processorFactory.getEntityType("normalizestatus"));
        assertEquals(PetFetchRequest.class, processorFactory.getEntityType("processpetfetchrequest"));
        assertEquals(PetFetchRequest.class, processorFactory.getEntityType("isfetchrequestvalid"));
        assertEquals(PetFetchRequest.class, processorFactory.getEntityType("noop"));
    }

    @Test
    public void testEntityTypeResolverConversion() throws Exception {
        // Test ObjectNode to Entity conversion
        ObjectNode petNode = objectMapper.createObjectNode();
        petNode.put("id", 123L);
        petNode.put("name", "Fluffy");
        petNode.put("status", "AVAILABLE");

        Pet pet = entityTypeResolver.convertToEntity(petNode, Pet.class);
        assertNotNull(pet);
        assertEquals(123L, pet.getId());
        assertEquals("Fluffy", pet.getName());
        assertEquals("AVAILABLE", pet.getStatus());

        // Test Entity to ObjectNode conversion
        ObjectNode convertedBack = entityTypeResolver.convertToObjectNode(pet);
        assertNotNull(convertedBack);
        assertEquals(123L, convertedBack.get("id").asLong());
        assertEquals("Fluffy", convertedBack.get("name").asText());
        assertEquals("AVAILABLE", convertedBack.get("status").asText());
    }

    @Test
    public void testWorkflowProcessorWithPetEntity() throws Exception {
        // Create a pet payload
        ObjectNode petPayload = objectMapper.createObjectNode();
        petPayload.put("id", 456L);
        petPayload.put("name", "Rex");
        petPayload.put("status", "PENDING");

        // Test normalizeStatus processor
        CompletableFuture<ObjectNode> result = workflowProcessor.processEvent("normalizeStatus", petPayload);
        ObjectNode processedPet = result.get();

        assertNotNull(processedPet);
        assertEquals("pending", processedPet.get("status").asText()); // Should be normalized to lowercase
        assertEquals("Rex", processedPet.get("name").asText());
        assertEquals(456L, processedPet.get("id").asLong());
    }

    @Test
    public void testWorkflowProcessorWithPetFetchRequestEntity() throws Exception {
        // Create a pet fetch request payload
        ObjectNode fetchRequestPayload = objectMapper.createObjectNode();
        fetchRequestPayload.put("sourceUrl", "https://petstore.swagger.io/v2/pet/findByStatus");
        fetchRequestPayload.put("status", "available");
        fetchRequestPayload.put("requestId", "test-123");

        // Test isFetchRequestValid processor
        CompletableFuture<ObjectNode> result = workflowProcessor.processEvent("isFetchRequestValid", fetchRequestPayload);
        ObjectNode processedRequest = result.get();

        assertNotNull(processedRequest);
        assertTrue(processedRequest.get("valid").asBoolean()); // Should be valid
        assertEquals("available", processedRequest.get("status").asText());
        assertEquals("https://petstore.swagger.io/v2/pet/findByStatus", processedRequest.get("sourceUrl").asText());
    }

    @Test
    public void testProcessorDirectEntityAccess() {
        // Get a processor directly and verify it works with entities
        CyodaProcessor<Pet> petProcessor = (CyodaProcessor<Pet>) processorFactory.getProcessor("addlastmodifiedtimestamp");
        assertNotNull(petProcessor);
        assertEquals(Pet.class, petProcessor.getEntityType());

        // Create a pet entity
        Pet pet = new Pet();
        pet.setId(789L);
        pet.setName("Buddy");
        pet.setStatus("available");

        // Process the entity directly
        CompletableFuture<Pet> result = petProcessor.process(pet);
        Pet processedPet = result.join();

        assertNotNull(processedPet);
        assertNotNull(processedPet.getLastModified()); // Should have timestamp added
        assertEquals("Buddy", processedPet.getName());
        assertEquals(789L, processedPet.getId());
    }
}
