package com.java_template.application.processor;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class UpdatePetStatusProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePetStatusProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UpdatePetStatusProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionJob entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionJob> context) {
        AdoptionJob job = context.entity();
        String now = Instant.now().toString();

        try {
            // Ensure petId present
            if (job.getPetId() == null || job.getPetId().isBlank()) {
                job.setStatus("failed");
                job.setResultDetails("Missing petId on AdoptionJob");
                job.setProcessedAt(now);
                return job;
            }

            UUID petTechnicalId;
            try {
                petTechnicalId = UUID.fromString(job.getPetId());
            } catch (Exception ex) {
                job.setStatus("failed");
                job.setResultDetails("Invalid petId format: " + ex.getMessage());
                job.setProcessedAt(now);
                return job;
            }

            // Load Pet
            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    petTechnicalId
            );

            ObjectNode petNode = petFuture.join();
            if (petNode == null) {
                job.setStatus("failed");
                job.setResultDetails("Pet not found: " + job.getPetId());
                job.setProcessedAt(now);
                return job;
            }

            Pet pet = objectMapper.treeToValue(petNode, Pet.class);

            String jobStatus = job.getStatus() != null ? job.getStatus().toLowerCase() : "";

            // Transition when job is approved: set pet -> adoption_in_progress
            if ("approved".equals(jobStatus)) {
                String petStatus = pet.getStatus() != null ? pet.getStatus().toLowerCase() : "";
                // Only allow transition if pet is in a sensible state (available or adoption-related)
                if (!"available".equals(petStatus) && !"reserved".equals(petStatus)) {
                    job.setStatus("failed");
                    job.setResultDetails("Pet not available for adoption (current pet.status=" + pet.getStatus() + ")");
                    job.setProcessedAt(now);
                    return job;
                }

                pet.setStatus("adoption_in_progress");
                pet.setUpdatedAt(now);

                // Persist pet update
                entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        UUID.fromString(pet.getId()),
                        pet
                ).join();

                // Move job to post_processing to indicate next steps (payment/finalization)
                job.setStatus("post_processing");
                // do not set processedAt yet; finalization will set it
                return job;
            }

            // Finalization: when job is in post_processing, finalize adoption -> adopted
            if ("post_processing".equals(jobStatus) || "finalize".equals(jobStatus) || "complete_adoption".equals(jobStatus)) {
                // Set pet adopted state
                pet.setStatus("adopted");
                pet.setAdoptedByUserId(job.getUserId());
                pet.setUpdatedAt(now);

                // Persist pet update
                entityService.updateItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        UUID.fromString(pet.getId()),
                        pet
                ).join();

                // Mark job completed and record processedAt
                job.setStatus("completed");
                job.setProcessedAt(now);
                job.setResultDetails("Pet marked as adopted and assigned to user: " + job.getUserId());
                return job;
            }

            // No action for other statuses; just update timestamp on job to indicate processed attempt
            job.setProcessedAt(now);
            job.setResultDetails("No pet status change performed for job.status=" + job.getStatus());
            return job;

        } catch (Exception ex) {
            logger.error("Error processing AdoptionJob {}: {}", job != null ? job.getId() : "unknown", ex.getMessage(), ex);
            if (job != null) {
                job.setStatus("failed");
                job.setResultDetails("Processor error: " + ex.getMessage());
                job.setProcessedAt(Instant.now().toString());
            }
            return job;
        }
    }
}