package com.java_template.application.processor;

import com.java_template.application.entity.pet_entity.version_1.PetEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * PetDataExtractionProcessor - Extract pet data from Pet Store API and create Pet entities
 * Transition: extract_pet_data (none → extracted)
 */
@Component
public class PetDataExtractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetDataExtractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final Random random = new Random();

    public PetDataExtractionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetDataExtraction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(PetEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetEntity> entityWithMetadata) {
        return entityWithMetadata != null && entityWithMetadata.metadata() != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<PetEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetEntity> context) {

        EntityWithMetadata<PetEntity> entityWithMetadata = context.entityResponse();
        PetEntity entity = entityWithMetadata.entity();

        logger.debug("Extracting pet data from Pet Store API simulation");

        // Simulate Pet Store API data extraction
        // In real implementation, this would call the actual Pet Store API
        extractPetDataFromAPI(entity);

        logger.info("Pet data extraction completed for entity: {}", entity.getPetId());

        return entityWithMetadata;
    }

    /**
     * Simulate extracting pet data from Pet Store API
     * In real implementation, this would make HTTP calls to https://petstore.swagger.io/
     */
    private void extractPetDataFromAPI(PetEntity entity) {
        // Simulate API data - in real implementation, this would be actual API calls
        Long petId = random.nextLong(1000) + 1;
        entity.setPetId(petId);
        entity.setName(generatePetName());
        entity.setCategory(generateCategory());
        entity.setPhotoUrls(generatePhotoUrls());
        entity.setTags(generateTags());
        entity.setPrice(calculatePrice(entity.getCategory(), entity.getTags()));
        entity.setStockLevel(random.nextInt(100) + 1); // Simulated stock data
        entity.setSalesVolume(0);
        entity.setRevenue(0.0);
        entity.setLastSaleDate(null);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Create additional pet entities via entityService
        createAdditionalPets();
    }

    private String generatePetName() {
        String[] names = {"Buddy", "Max", "Luna", "Charlie", "Lucy", "Cooper", "Daisy", "Rocky", "Molly", "Jack"};
        return names[random.nextInt(names.length)];
    }

    private PetEntity.Category generateCategory() {
        PetEntity.Category category = new PetEntity.Category();
        String[] categories = {"Dogs", "Cats", "Birds", "Fish", "Reptiles"};
        Long[] categoryIds = {1L, 2L, 3L, 4L, 5L};
        int index = random.nextInt(categories.length);
        category.setId(categoryIds[index]);
        category.setName(categories[index]);
        return category;
    }

    private List<String> generatePhotoUrls() {
        return Arrays.asList("https://example.com/photo" + random.nextInt(100) + ".jpg");
    }

    private List<PetEntity.Tag> generateTags() {
        PetEntity.Tag tag = new PetEntity.Tag();
        String[] tags = {"friendly", "playful", "calm", "energetic", "loyal"};
        Long[] tagIds = {1L, 2L, 3L, 4L, 5L};
        int index = random.nextInt(tags.length);
        tag.setId(tagIds[index]);
        tag.setName(tags[index]);
        return Arrays.asList(tag);
    }

    private Double calculatePrice(PetEntity.Category category, List<PetEntity.Tag> tags) {
        double basePrice = 100.0;
        if (category != null) {
            switch (category.getName()) {
                case "Dogs": basePrice = 300.0; break;
                case "Cats": basePrice = 200.0; break;
                case "Birds": basePrice = 150.0; break;
                case "Fish": basePrice = 50.0; break;
                case "Reptiles": basePrice = 250.0; break;
            }
        }
        return basePrice + random.nextDouble() * 100;
    }

    private void createAdditionalPets() {
        // Create a few additional pet entities to simulate API batch extraction
        for (int i = 0; i < 3; i++) {
            PetEntity newPet = new PetEntity();
            extractPetDataFromAPI(newPet);
            try {
                entityService.create(newPet);
            } catch (Exception e) {
                logger.warn("Failed to create additional pet entity: {}", e.getMessage());
            }
        }
    }
}
