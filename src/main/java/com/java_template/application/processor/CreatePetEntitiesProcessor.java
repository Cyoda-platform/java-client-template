package com.java_template.application.processor;
import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreatePetEntitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePetEntitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public CreatePetEntitiesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetEnrichmentJob entity) {
        return entity != null && entity.isValid();
    }

    private PetEnrichmentJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetEnrichmentJob> context) {
        PetEnrichmentJob job = context.entity();

        // Ensure errors list is initialized
        if (job.getErrors() == null) {
            job.setErrors(new ArrayList<>());
        }

        Integer fetched = job.getFetchedCount() != null ? job.getFetchedCount() : 0;
        if (fetched <= 0) {
            // Nothing to create -> mark job as failed with an error
            job.getErrors().add("No items fetched to create pets.");
            job.setStatus("FAILED");
            logger.warn("PetEnrichmentJob {} has no fetched items, marking FAILED", job.getJobId());
            return job;
        }

        List<CompletableFuture<java.util.UUID>> futures = new ArrayList<>();
        for (int i = 0; i < fetched; i++) {
            Pet pet = new Pet();
            // Required fields for Pet.isValid(): id, name, species, status, photos (non-null)
            pet.setId(UUID.randomUUID().toString());
            pet.setName("Imported Pet " + (i + 1));
            pet.setSpecies("unknown");
            pet.setStatus("available");
            pet.setSource(job.getPetSource());
            pet.setPhotos(new ArrayList<>());

            try {
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        pet
                );
                futures.add(idFuture);
            } catch (Exception e) {
                String err = "Failed to queue pet creation for job " + job.getJobId() + " item " + (i + 1) + ": " + e.getMessage();
                job.getErrors().add(err);
                logger.error(err, e);
            }
        }

        // Wait for all add operations to complete and collect failures
        try {
            CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .join();
        } catch (Exception e) {
            String err = "One or more pet creation operations failed for job " + job.getJobId() + ": " + e.getMessage();
            job.getErrors().add(err);
            logger.error(err, e);
            job.setStatus("FAILED");
            return job;
        }

        // If we reach here, assume creates succeeded
        job.setStatus("COMPLETED");
        logger.info("PetEnrichmentJob {} completed. Created {} pets.", job.getJobId(), futures.size());
        return job;
    }
}