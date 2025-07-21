package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
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
import java.util.concurrent.ExecutionException;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    private final java.util.concurrent.atomic.AtomicInteger petIdCounter = new java.util.concurrent.atomic.AtomicInteger(1);

    public PetProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(PurrfectPetsJob::isValid)
                .map(this::processPurrfectPetsJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processPurrfectPetsJob(PurrfectPetsJob job) {
        try {
            logger.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());
            if (job.getPetType() == null || job.getPetType().isBlank()) {
                logger.error("Job petType is invalid");
                job.setStatus("FAILED");
                entityService.addItem("PurrfectPetsJob", Config.ENTITY_VERSION, job).get();
                return job;
            }

            job.setStatus("PROCESSING");
            entityService.addItem("PurrfectPetsJob", Config.ENTITY_VERSION, job).get();

            List<Pet> fetchedPets = new ArrayList<>();

            Pet pet1 = new Pet();
            pet1.setPetId("pet-" + petIdCounter.getAndIncrement());
            pet1.setName("Whiskers");
            pet1.setType(job.getPetType());
            pet1.setAge(3);
            pet1.setStatus("AVAILABLE");
            UUID pet1Id = entityService.addItem("Pet", Config.ENTITY_VERSION, pet1).get();
            pet1.setTechnicalId(pet1Id);
            fetchedPets.add(pet1);

            Pet pet2 = new Pet();
            pet2.setPetId("pet-" + petIdCounter.getAndIncrement());
            pet2.setName("Fido");
            pet2.setType(job.getPetType());
            pet2.setAge(5);
            pet2.setStatus("AVAILABLE");
            UUID pet2Id = entityService.addItem("Pet", Config.ENTITY_VERSION, pet2).get();
            pet2.setTechnicalId(pet2Id);
            fetchedPets.add(pet2);

            job.setStatus("COMPLETED");
            entityService.addItem("PurrfectPetsJob", Config.ENTITY_VERSION, job).get();

            logger.info("Completed PurrfectPetsJob with technicalId: {}. Pets fetched: {}", job.getTechnicalId(), fetchedPets.size());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing PurrfectPetsJob", e);
            job.setStatus("FAILED");
            try {
                entityService.addItem("PurrfectPetsJob", Config.ENTITY_VERSION, job).get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error updating failed status for PurrfectPetsJob", ex);
            }
        }
        return job;
    }
}
