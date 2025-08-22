package com.java_template.application.processor;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class CreatePetEntitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePetEntitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreatePetEntitiesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid PetEnrichmentJob state")
            .map(this::processEntityLogic)
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

        // Ensure errors list exists
        if (job.getErrors() == null) {
            job.setErrors(new ArrayList<>());
        }

        Integer fetched = job.getFetchedCount() != null ? job.getFetchedCount() : 0;

        if (fetched <= 0) {
            job.getErrors().add("No items fetched to create pets.");
            job.setStatus("FAILED");
            logger.warn("PetEnrichmentJob {} has no fetched items, marking FAILED", job.getJobId());
            return job;
        }

        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < fetched; i++) {
            try {
                Pet pet = new Pet();
                pet.setId(UUID.randomUUID().toString());
                pet.setName("Imported Pet " + (i + 1));
                pet.setSpecies("unknown");
                pet.setStatus("available");
                pet.setSource(job.getPetSource());
                pet.setPhotos(new ArrayList<>());
                pet.setBreed("unknown");
                pet.setDescription("Imported via enrichment job " + job.getJobId());
                // age/gender left null when unknown

                CompletableFuture<UUID> idFuture = entityService.addItem(
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

        // Wait for all creations to complete and collect failures
        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.join(); // wait for completion

            List<UUID> created = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException ex) {
                    // log and return null marker
                    logger.error("Error creating pet for job {}: {}", job.getJobId(), ex.getMessage());
                    return null;
                }
            }).filter(u -> u != null).collect(Collectors.toList());

            if (created.size() != futures.size()) {
                job.getErrors().add("One or more pet creation operations failed for job " + job.getJobId());
                job.setStatus("FAILED");
            } else {
                job.setStatus("COMPLETED");
            }
        } catch (Exception e) {
            String err = "One or more pet creation operations failed for job " + job.getJobId() + ": " + e.getMessage();
            job.getErrors().add(err);
            job.setStatus("FAILED");
            logger.error(err, e);
        }

        logger.info("PetEnrichmentJob {} completed. Created {} pets. Errors: {}", job.getJobId(), 
                (int) futures.stream().filter(f -> {
                    try { return f.get() != null; } catch (Exception ex) { return false; }
                }).count(),
                job.getErrors());

        return job;
    }
}