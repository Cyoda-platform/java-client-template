package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.serializer.ErrorInfo;
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
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .validate(this::isValidEntity, "Invalid PetIngestionJob entity")
            .map(this::processPetIngestionJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
               "petingestionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetIngestionJob processPetIngestionJobLogic(PetIngestionJob job) {
        // Business logic for processing PetIngestionJob
        // Step 1: Initial State: PetIngestionJob created with PENDING status (assumed already set)

        // Step 2: Fetch Data: Call external Petstore API to retrieve pet data
        // For demonstration, simulate fetching data with dummy data
        List<Pet> fetchedPets = fetchPetsFromExternalAPI(job.getSource());

        // Step 3: Data Persistence: Save new Pet entities immutably with AVAILABLE status
        for (Pet pet : fetchedPets) {
            pet.setStatus("AVAILABLE");
            entityService.addItem(pet); // Persist new Pet entity
        }

        // Step 4: Completion: Update job status to COMPLETED
        job.setStatus("COMPLETED");

        // Step 5: Notification: (Optional) Trigger downstream workflows or monitoring (not implemented here)

        return job;
    }

    private List<Pet> fetchPetsFromExternalAPI(String source) {
        // Simulate fetching pets from an external API based on source
        // In real implementation, make HTTP calls and parse response
        List<Pet> pets = new ArrayList<>();
        // Dummy pet example
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fluffy");
        pet.setCategory("cat");
        pet.setPhotoUrls(List.of("http://example.com/photo1.jpg"));
        pet.setTags(List.of("cute", "friendly"));
        pet.setStatus("AVAILABLE");
        pets.add(pet);
        return pets;
    }

    private boolean isValidEntity(PetIngestionJob job) {
        return job != null && job.getId() != null && !job.getId().isBlank() &&
               job.getStatus() != null && !job.getStatus().isBlank();
    }
}
