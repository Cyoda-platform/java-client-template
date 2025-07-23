package com.java_template.application.processor;

import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetEvent;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PetRegistrationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    
    // Simulating counters for pet and event ids as AtomicInteger
    private static final AtomicInteger petIdCounter = new AtomicInteger(1);
    private static final AtomicInteger petEventIdCounter = new AtomicInteger(1);

    public PetRegistrationJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetRegistrationJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetRegistrationJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetRegistrationJob.class)
            .validate(PetRegistrationJob::isValid, "Invalid PetRegistrationJob")
            .map(entity -> {
                try {
                    return processPetRegistrationJob(entity);
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("Exception during processing PetRegistrationJob", e);
                    entity.setStatus("FAILED");
                    try {
                        entityService.addItem("PetRegistrationJob", Config.ENTITY_VERSION, entity).get();
                    } catch (Exception ex) {
                        logger.error("Failed to persist failed PetRegistrationJob", ex);
                    }
                    return entity;
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRegistrationJobProcessor".equals(modelSpec.operationName()) &&
               "petRegistrationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetRegistrationJob processPetRegistrationJob(PetRegistrationJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing PetRegistrationJob with ID: {}", job.getId());
        if (!job.isValid()) {
            logger.error("Invalid PetRegistrationJob during processing: {}", job);
            job.setStatus("FAILED");
            entityService.addItem("PetRegistrationJob", Config.ENTITY_VERSION, job).get();
            return job;
        }

        Pet pet = new Pet();
        String petId = "pet-" + petIdCounter.getAndIncrement();
        pet.setPetId(petId);
        pet.setId(petId);
        pet.setName(job.getPetName());
        pet.setType(job.getPetType());
        pet.setOwner(job.getOwnerName());
        pet.setRegisteredAt(Instant.now().toString());
        pet.setStatus("ACTIVE");

        entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
        logger.info("Created Pet {} from Job {}", pet.getId(), job.getId());

        PetEvent event = new PetEvent();
        String eventId = "event-" + petEventIdCounter.getAndIncrement();
        event.setEventId(eventId);
        event.setId(eventId);
        event.setPetId(pet.getId());
        event.setEventType("CREATED");
        event.setEventTimestamp(Instant.now().toString());
        event.setStatus("RECORDED");

        entityService.addItem("PetEvent", Config.ENTITY_VERSION, event).get();
        logger.info("Created PetEvent {} for Pet {}", event.getId(), pet.getId());

        job.setStatus("COMPLETED");
        entityService.addItem("PetRegistrationJob", Config.ENTITY_VERSION, job).get();
        logger.info("PetRegistrationJob {} completed", job.getId());

        return job;
    }
}
