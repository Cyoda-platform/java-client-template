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
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final AtomicLong petIdCounter = new AtomicLong(1); // simulates pet ID generation as in prototype

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(PurrfectPetsJob.class)
            .validate(PurrfectPetsJob::isValid, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processEntityLogic(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());

        // 1. Validate petType
        if (job.getPetType() == null || job.getPetType().isBlank()) {
            logger.error("Job petType is invalid");
            job.setStatus("FAILED");
            return job;
        }

        job.setStatus("PROCESSING");

        // 2. Simulate fetching pets of petType
        List<Pet> fetchedPets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setPetId("pet-" + petIdCounter.get());
        pet1.setName("Whiskers");
        pet1.setType(job.getPetType());
        pet1.setAge(3);
        pet1.setStatus("AVAILABLE");
        pet1.setTechnicalId(null); // No technical id here
        fetchedPets.add(pet1);

        Pet pet2 = new Pet();
        pet2.setPetId("pet-" + (petIdCounter.get() + 1));
        pet2.setName("Fido");
        pet2.setType(job.getPetType());
        pet2.setAge(5);
        pet2.setStatus("AVAILABLE");
        pet2.setTechnicalId(null);
        fetchedPets.add(pet2);

        // 3. Here we would persist or update pets - omitted due to lack of injected service
        // In a real system, we might inject EntityService or repository to save pets

        // 4. Completion
        job.setStatus("COMPLETED");

        // 5. Notification (log)
        logger.info("Completed PurrfectPetsJob with technicalId: {}. Pets fetched: {}", job.getTechnicalId(), fetchedPets.size());

        return job;
    }
}
