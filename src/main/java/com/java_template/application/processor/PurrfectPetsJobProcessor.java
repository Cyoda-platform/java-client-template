package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.java_template.common.service.EntityService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.client.RestTemplate;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final AtomicLong petIdCounter = new AtomicLong(1L);

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, EntityService entityService, RestTemplate restTemplate) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = restTemplate;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PurrfectPetsJob.class)
            .validate(PurrfectPetsJob::isValid)
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processEntityLogic(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with ID: {}", job.getTechnicalId());
        job.setStatus("PROCESSING");
        try {
            if (job.getPetStatus() == null || job.getPetStatus().isBlank()) {
                logger.error("Job validation failed: petStatus is blank");
                job.setStatus("FAILED");
                job.setResultSummary("Validation failed: petStatus is blank");
                return job;
            }

            String petstoreUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + job.getPetStatus();
            Pet[] petsFromApi = restTemplate.getForObject(petstoreUrl, Pet[].class);

            if (petsFromApi == null) {
                logger.error("No pets returned from Petstore API");
                job.setStatus("FAILED");
                job.setResultSummary("No pets returned from Petstore API");
                return job;
            }

            int ingestedCount = 0;
            List<Pet> validPets = new ArrayList<>();

            for (Pet apiPet : petsFromApi) {
                if (apiPet == null) continue;

                Pet pet = new Pet();
                String petTechnicalId = "pet-" + petIdCounter.getAndIncrement();
                pet.setPetId(apiPet.getPetId());
                pet.setName(apiPet.getName());
                pet.setCategory(apiPet.getCategory());
                pet.setPhotoUrls(apiPet.getPhotoUrls());
                pet.setTags(apiPet.getTags());
                pet.setStatus(apiPet.getStatus());
                pet.setIngestedAt(java.time.Instant.now().toString());
                pet.setTechnicalId(petTechnicalId);

                if (!pet.isValid()) {
                    logger.error("Invalid pet data skipped: {}", pet);
                    continue;
                }

                validPets.add(pet);
                ingestedCount++;
            }

            if (!validPets.isEmpty()) {
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems("Pet", Config.ENTITY_VERSION, validPets);
                try {
                    idsFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to add pets to EntityService: {}", e.getMessage());
                    job.setStatus("FAILED");
                    job.setResultSummary("Failed to ingest pets: " + e.getMessage());
                    return job;
                }

                validPets.forEach(this::processPet);
            }

            job.setStatus("COMPLETED");
            job.setResultSummary("Ingested " + ingestedCount + " pets");
            logger.info("Job {} completed, ingested {} pets", job.getTechnicalId(), ingestedCount);

        } catch (Exception ex) {
            logger.error("Error processing PurrfectPetsJob {}: {}", job.getTechnicalId(), ex.getMessage());
            job.setStatus("FAILED");
            job.setResultSummary("Error during processing: " + ex.getMessage());
        }

        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getPetId());
        logger.info("Pet processed successfully: {}", pet.getName());
    }

    // Inner Pet class based on prototype definition
    private static class Pet {
        private Long petId;
        private String name;
        private String category;
        private List<String> photoUrls;
        private List<String> tags;
        private String status;
        private String ingestedAt;
        private String technicalId;

        public Long getPetId() { return petId; }
        public void setPetId(Long petId) { this.petId = petId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<String> getPhotoUrls() { return photoUrls; }
        public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getIngestedAt() { return ingestedAt; }
        public void setIngestedAt(String ingestedAt) { this.ingestedAt = ingestedAt; }

        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }

        public boolean isValid() {
            if (petId == null) return false;
            if (name == null || name.isBlank()) return false;
            if (status == null || status.isBlank()) return false;
            return true;
        }
    }
}
