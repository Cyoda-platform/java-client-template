package com.java_template.application.processor;

import com.java_template.application.entity.PetRegistrationJob;
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

import com.java_template.common.service.EntityService;

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
        // Business logic based on functional requirements in functional_requirement.md
        // 1. Initial State: Job created with PENDING status
        // 2. Validation: already done by isValid in workflow
        // 3. Processing: Create immutable Pet entity record with AVAILABLE or PENDING status

        // Construct Pet entity from PetRegistrationJob data
        com.java_template.application.entity.Pet pet = new com.java_template.application.entity.Pet();
        pet.setPetId(java.util.UUID.randomUUID().toString());
        pet.setName(job.getPetName());
        pet.setCategory(job.getPetType());
        pet.setStatus(job.getPetStatus());

        // We don't have photoUrls or tags from job, so set empty lists
        pet.setPhotoUrls(new java.util.ArrayList<>());
        pet.setTags(new java.util.ArrayList<>());

        // Add new Pet entity
        entityService.addItem(pet);

        // 4. Completion: Update Job status to COMPLETED
        job.setStatus("COMPLETED");

        // 5. Notification: Log the registration
        logger.info("PetRegistrationJob processed successfully, Pet created with id: {}", pet.getPetId());

        return job;
    }

}
