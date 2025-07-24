package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.application.entity.Pet;
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
import java.time.Instant;
import java.util.List;
import com.java_template.common.service.EntityService;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PurrfectPetsJob.class)
            .validate(this::isValidEntity, "Invalid PurrfectPetsJob entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectpetsjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PurrfectPetsJob entity) {
        return entity.getPetStatus() != null && !entity.getPetStatus().isBlank();
    }

    private PurrfectPetsJob processEntityLogic(PurrfectPetsJob job) {
        try {
            logger.info("Starting job processing for petStatus: {}", job.getPetStatus());

            // Call Petstore API to get pets by status
            List<Pet> pets = entityService.fetchPetsByStatus(job.getPetStatus());

            // For each pet in response, create immutable Pet entities with ingestedAt timestamp
            String ingestedAt = Instant.now().toString();
            for (Pet pet : pets) {
                Pet newPet = new Pet();
                newPet.setPetId(pet.getPetId());
                newPet.setName(pet.getName());
                newPet.setCategory(pet.getCategory());
                newPet.setPhotoUrls(pet.getPhotoUrls());
                newPet.setTags(pet.getTags());
                newPet.setStatus(pet.getStatus());
                newPet.setIngestedAt(ingestedAt);

                entityService.addItem(newPet);
            }

            job.setStatus("COMPLETED");
            job.setResultSummary("Ingested " + pets.size() + " pets");

        } catch (Exception e) {
            logger.error("Error processing PurrfectPetsJob", e);
            job.setStatus("FAILED");
            job.setResultSummary("Error: " + e.getMessage());
        }

        return job;
    }
}
