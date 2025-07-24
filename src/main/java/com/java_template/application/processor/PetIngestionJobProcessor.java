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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import java.util.UUID;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .validate(this::isValidEntity, "Invalid PetIngestionJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
               "petingestionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetIngestionJob job) {
        return job != null && job.getId() != null && !job.getId().isBlank() && job.getStatus() != null;
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        // Example business logic for PetIngestionJob processing
        // 1. Fetch data from external Petstore API (dummy placeholder logic here as no actual API)
        // 2. Save new Pet entities immutably with AVAILABLE status
        // 3. Update job status to COMPLETED or FAILED

        // This example does not include real API calls due to limitation but follows the business flow
        try {
            // Simulate fetching pet data and adding new pets
            List<com.java_template.application.entity.Pet> newPets = new ArrayList<>();

            // Simulated pet data (in real scenario, fetched from external API)
            com.java_template.application.entity.Pet pet1 = new com.java_template.application.entity.Pet();
            pet1.setName("Fluffy");
            pet1.setCategory("cat");
            pet1.setPhotoUrls(List.of("http://example.com/photo1.jpg"));
            pet1.setTags(List.of("cute", "friendly"));
            pet1.setStatus("AVAILABLE");
            newPets.add(pet1);

            for (com.java_template.application.entity.Pet pet : newPets) {
                entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
            }

            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Error processing PetIngestionJob: ", e);
            job.setStatus("FAILED");
        }

        return job;
    }

}
