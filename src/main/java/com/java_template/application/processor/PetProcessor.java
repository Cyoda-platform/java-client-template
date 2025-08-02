package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public PetProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        // A PetIngestionJob is valid for processing if it's in PENDING status.
        return entity != null && "PENDING".equalsIgnoreCase(entity.getStatus());
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob job = context.entity();

        // Update job status to IN_PROGRESS and record start time
        job.setStatus("IN_PROGRESS");
        job.setStartTime(Instant.now().toString());
        job.setIngestedPetCount(0); // Reset count for a new processing attempt
        job.setErrorMessage(null); // Clear any previous error message

        try {
            // Simulate fetching pet data from an external Petstore API
            // In a real application, this would be an actual HTTP client call to the Petstore API
            List<ExternalPetData> rawPets = simulatePetstoreApiCall(job.getTargetPetStatus());

            int successfullyIngestedCount = 0;
            for (ExternalPetData rawPet : rawPets) {
                try {
                    // Generate a unique fun fact based on pet attributes
                    String funFact = generateFunFact(rawPet);

                    // Map external pet data to internal Pet entity
                    Pet newPet = mapToPetEntity(rawPet, funFact);

                    // Save the new Pet entity. This operation is expected to trigger processPet()
                    // as a post-save hook as per the functional requirements.
                    CompletableFuture<UUID> future = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Integer.parseInt(Config.ENTITY_VERSION)), // Use Config.ENTITY_VERSION
                        newPet
                    );
                    future.join(); // Block until the save operation completes
                    successfullyIngestedCount++;
                    logger.info("Successfully processed and saved Pet: {}", newPet.getName());
                } catch (Exception e) {
                    logger.error("Error processing individual pet (Petstore ID: {}): {}", rawPet.getPetstoreId(), e.getMessage());
                }
            }
            job.setIngestedPetCount(successfullyIngestedCount);
        } catch (Exception e) {
            // General error during API fetch or overall processing
            job.setErrorMessage("Failed to ingest pets due to a critical error: " + e.getMessage());
            job.setIngestedPetCount(0); // Indicate no pets were ingested successfully in case of a critical error
            logger.error("Pet ingestion job encountered a critical failure: {}", e.getMessage(), e);
        }

        return job; // Return the modified PetIngestionJob entity
    }

    // --- Helper methods to simulate external interactions and data transformation ---

    // Inner class to simulate data received from external Petstore API
    // Explicit getters/setters instead of Lombok @Data as per instructions
    private static class ExternalPetData {
        private Long petstoreId;
        private String name;
        private String status;
        private String category;
        private List<String> photoUrls;
        private List<String> tags;

        public ExternalPetData(Long petstoreId, String name, String status, String category, List<String> photoUrls, List<String> tags) {
            this.petstoreId = petstoreId;
            this.name = name;
            this.status = status;
            this.category = category;
            this.photoUrls = photoUrls;
            this.tags = tags;
        }

        // Getters
        public Long getPetstoreId() { return petstoreId; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public String getCategory() { return category; }
        public List<String> getPhotoUrls() { return photoUrls; }
        public List<String> getTags() { return tags; }

        // Setters (though not strictly needed for this usage, good practice)
        public void setPetstoreId(Long petstoreId) { this.petstoreId = petstoreId; }
        public void setName(String name) { this.name = name; }
        public void setStatus(String status) { this.status = status; }
        public void setCategory(String category) { this.category = category; }
        public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    // Simulates an external API call to Petstore based on targetStatus
    private List<ExternalPetData> simulatePetstoreApiCall(String targetStatus) {
        List<ExternalPetData> pets = new ArrayList<>();
        if ("available".equalsIgnoreCase(targetStatus)) {
            pets.add(new ExternalPetData(12345L, "Buddy", "available", "Dog", Arrays.asList("http://example.com/buddy.jpg"), Arrays.asList("friendly", "playful")));
            pets.add(new ExternalPetData(67890L, "Whiskers", "available", "Cat", Arrays.asList("http://example.com/whiskers.jpg"), Arrays.asList("cute", "sleepy")));
            pets.add(new ExternalPetData(99999L, "Goldie", "available", "Fish", Arrays.asList("http://example.com/goldie.jpg"), Arrays.asList("aquatic")));
        } else if ("pending".equalsIgnoreCase(targetStatus)) {
            pets.add(new ExternalPetData(11111L, "Shadow", "pending", "Dog", Arrays.asList("http://example.com/shadow.jpg"), Arrays.asList("shy")));
        }
        return pets;
    }

    // Simulates generating a fun fact for a pet based on its attributes
    private String generateFunFact(ExternalPetData rawPet) {
        if ("Dog".equalsIgnoreCase(rawPet.getCategory())) {
            return rawPet.getName() + " loves chasing squirrels in the park!";
        } else if ("Cat".equalsIgnoreCase(rawPet.getCategory())) {
            return rawPet.getName() + " can sleep up to 16 hours a day!";
        } else if ("Fish".equalsIgnoreCase(rawPet.getCategory())) {
            return rawPet.getName() + " enjoys swimming in circles!";
        }
        return "A unique fun fact about " + rawPet.getName() + ".";
    }

    // Maps raw external data to our internal Pet entity
    private Pet mapToPetEntity(ExternalPetData rawPet, String funFact) {
        Pet pet = new Pet();
        pet.setPetstoreId(rawPet.getPetstoreId());
        pet.setName(rawPet.getName());
        pet.setStatus(rawPet.getStatus());
        pet.setCategory(rawPet.getCategory());
        pet.setPhotoUrls(rawPet.getPhotoUrls());
        pet.setTags(rawPet.getTags());
        pet.setFunFact(funFact);
        return pet;
    }
}