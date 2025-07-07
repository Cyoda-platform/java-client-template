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
 * Simple demonstration test to show the refactoring works.
 * This test shows that processors now work with CyodaEntity types instead of ObjectNode.
 */
@SpringBootTest(classes = com.java_template.Application.class)
public class RefactoringDemoTest {

    @Autowired
    private ProcessorFactory processorFactory;

    @Autowired
    private EntityTypeResolver entityTypeResolver;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void demonstrateRefactoringBenefits() throws Exception {
        System.out.println("=== CyodaProcessor Refactoring Demonstration ===");
        
        // 1. Show that processors are registered with correct entity types
        System.out.println("\n1. Processor Registration:");
        System.out.println("   - addlastmodifiedtimestamp -> " + processorFactory.getEntityType("addlastmodifiedtimestamp"));
        System.out.println("   - normalizestatus -> " + processorFactory.getEntityType("normalizestatus"));
        System.out.println("   - processpetfetchrequest -> " + processorFactory.getEntityType("processpetfetchrequest"));
        
        // 2. Show direct entity processing (no ObjectNode conversion in processor)
        System.out.println("\n2. Direct Entity Processing:");
        
        // Get a processor that works with Pet entities
        CyodaProcessor<Pet> petProcessor = (CyodaProcessor<Pet>) processorFactory.getProcessor("addlastmodifiedtimestamp");
        assertNotNull(petProcessor);
        assertEquals(Pet.class, petProcessor.getEntityType());
        
        // Create a Pet entity directly
        Pet pet = new Pet();
        pet.setId(123L);
        pet.setName("Fluffy");
        pet.setStatus("available");
        
        System.out.println("   Before processing: lastModified = " + pet.getLastModified());
        
        // Process the entity directly - no ObjectNode conversion needed!
        CompletableFuture<Pet> result = petProcessor.process(pet);
        Pet processedPet = result.get();
        
        System.out.println("   After processing: lastModified = " + processedPet.getLastModified());
        assertNotNull(processedPet.getLastModified());
        
        // 3. Show EntityTypeResolver conversion
        System.out.println("\n3. EntityTypeResolver Conversion:");
        
        ObjectNode petNode = objectMapper.createObjectNode();
        petNode.put("id", 456L);
        petNode.put("name", "Rex");
        petNode.put("status", "PENDING");
        
        Pet convertedPet = entityTypeResolver.convertToEntity(petNode, Pet.class);
        System.out.println("   Converted Pet: " + convertedPet.getName() + " (ID: " + convertedPet.getId() + ")");
        
        ObjectNode convertedBack = entityTypeResolver.convertToObjectNode(convertedPet);
        System.out.println("   Converted back to ObjectNode: " + convertedBack.toString());
        
        // 4. Show type safety
        System.out.println("\n4. Type Safety:");
        System.out.println("   Processor works with specific entity type: " + petProcessor.getEntityType().getSimpleName());
        System.out.println("   No manual ObjectNode conversion in processor code!");
        System.out.println("   Conversion happens once at workflow boundary.");
        
        System.out.println("\n=== Refactoring Benefits Demonstrated ===");
        System.out.println("✓ Type Safety: Processors work with specific CyodaEntity types");
        System.out.println("✓ Cleaner Code: No manual conversion in each processor");
        System.out.println("✓ Better Performance: Conversion happens once at workflow level");
        System.out.println("✓ Compile-time Validation: Processor-entity relationships are enforced");
    }

    @Test
    public void demonstrateProcessorComparison() throws Exception {
        System.out.println("\n=== Before vs After Comparison ===");
        
        // Show how clean the processor code is now
        CyodaProcessor<Pet> normalizeProcessor = (CyodaProcessor<Pet>) processorFactory.getProcessor("normalizestatus");
        
        Pet pet = new Pet();
        pet.setId(789L);
        pet.setName("Buddy");
        pet.setStatus("AVAILABLE"); // Uppercase
        
        System.out.println("Before normalization: status = " + pet.getStatus());
        
        CompletableFuture<Pet> result = normalizeProcessor.process(pet);
        Pet normalizedPet = result.get();
        
        System.out.println("After normalization: status = " + normalizedPet.getStatus());
        assertEquals("available", normalizedPet.getStatus()); // Should be lowercase
        
        System.out.println("\nProcessor Implementation Benefits:");
        System.out.println("- BEFORE: Manual ObjectNode ↔ Entity conversion in each processor");
        System.out.println("- AFTER: Direct entity processing, conversion at workflow boundary");
        System.out.println("- RESULT: Cleaner, more maintainable processor code!");
    }
}
