package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.time.Instant;

@Component
public class PetRegistrationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

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
                .validate(PetRegistrationJob::isValid, "Invalid PetRegistrationJob state")
                .map(this::processPetRegistrationJobLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetRegistrationJobProcessor".equals(modelSpec.operationName()) &&
                "petregistrationjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetRegistrationJob processPetRegistrationJobLogic(PetRegistrationJob job) {
        // Business logic from processPetRegistrationJob() flow in functional requirements
        // 1. Initial State: Job created with PENDING status
        // 2. Validation: Check pet data completeness and correctness (petName, petType)
        // 3. Processing: Create immutable Pet entity record
        // 4. Completion: Update Job status to COMPLETED if Pet created successfully, else FAILED
        // 5. Notification: Log job completion

        Pet pet = new Pet();
        pet.setPetId(generatePetId());
        pet.setName(job.getPetName());
        pet.setCategory(job.getPetType());
        pet.setStatus(job.getPetStatus());
        pet.setPhotoUrls(null);
        pet.setTags(null);

        try {
            UUID petId = entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();
            logger.info("Persisted Pet entity with generated ID: {}", petId);
            job.setStatus("COMPLETED");
        } catch (Exception e) {
            logger.error("Failed to persist Pet entity: {}", e.getMessage());
            job.setStatus("FAILED");
        }

        logger.info("PetRegistrationJob processed with status: {}", job.getStatus());
        return job;
    }

    private String generatePetId() {
        return "pet-" + Instant.now().toEpochMilli();
    }
}
