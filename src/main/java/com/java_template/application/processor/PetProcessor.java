package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetJob.class)
                .validate(PetJob::isValid, "Invalid PetJob entity")
                .map(this::processPetJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJob(PetJob petJob) {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());

        try {
            petJob.setStatus(PetJob.StatusEnum.PROCESSING);
            // Update PetJob status to PROCESSING by creating new version
            entityService.addItem("PetJob", Config.ENTITY_VERSION, petJob).get();

            // Simulate calling Petstore API to fetch pets
            List<Pet> fetchedPets = fetchPetsFromPetstore(petJob.getRequestType(), petJob.getPetType());

            // Persist pets into entityService - each as new Pet entity with new technicalId
            List<Pet> petsWithSetStatus = new ArrayList<>();
            for (Pet pet : fetchedPets) {
                pet.setStatus(Pet.StatusEnum.AVAILABLE);
                petsWithSetStatus.add(pet);
            }
            CompletableFuture<List<UUID>> petsIdsFuture = entityService.addItems("Pet", Config.ENTITY_VERSION, petsWithSetStatus);
            List<UUID> petTechnicalIds = petsIdsFuture.get();

            petJob.setResultCount(petTechnicalIds.size());
            petJob.setStatus(PetJob.StatusEnum.COMPLETED);

            // Save PetJob update with completed status and resultCount (create new version)
            entityService.addItem("PetJob", Config.ENTITY_VERSION, petJob).get();

            logger.info("PetJob {} completed successfully with {} pets fetched", petJob.getTechnicalId(), petTechnicalIds.size());

        } catch (Exception e) {
            logger.error("Error processing PetJob {}: {}", petJob.getTechnicalId(), e.getMessage());
            petJob.setStatus(PetJob.StatusEnum.FAILED);
            try {
                entityService.addItem("PetJob", Config.ENTITY_VERSION, petJob).get();
            } catch (Exception ex) {
                logger.error("Failed to update PetJob status to FAILED for technicalId {}: {}", petJob.getTechnicalId(), ex.getMessage());
            }
        }
        return petJob;
    }

    private List<Pet> fetchPetsFromPetstore(String requestType, String petType) {
        // For prototype, simulate data retrieval with static or sample data
        List<Pet> pets = new ArrayList<>();

        Pet cat = new Pet();
        cat.setName("Whiskers");
        cat.setCategory("cat");
        cat.setPhotoUrls(List.of("http://example.com/cat1.jpg"));
        cat.setTags(List.of("playful", "indoor"));
        cat.setStatus(Pet.StatusEnum.AVAILABLE);

        Pet dog = new Pet();
        dog.setName("Rex");
        dog.setCategory("dog");
        dog.setPhotoUrls(List.of("http://example.com/dog1.jpg"));
        dog.setTags(List.of("friendly", "outdoor"));
        dog.setStatus(Pet.StatusEnum.AVAILABLE);

        if ("FETCH_ALL".equalsIgnoreCase(requestType)) {
            if (petType == null || petType.isBlank()) {
                pets.add(cat);
                pets.add(dog);
            } else if ("cat".equalsIgnoreCase(petType)) {
                pets.add(cat);
            } else if ("dog".equalsIgnoreCase(petType)) {
                pets.add(dog);
            }
        } else if ("FETCH_BY_TYPE".equalsIgnoreCase(requestType) && petType != null && !petType.isBlank()) {
            if ("cat".equalsIgnoreCase(petType)) {
                pets.add(cat);
            } else if ("dog".equalsIgnoreCase(petType)) {
                pets.add(dog);
            }
        }
        return pets;
    }

}
