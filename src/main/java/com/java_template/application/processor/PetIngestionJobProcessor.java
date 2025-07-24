package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    // Simulated cache and id counter for demonstration (mimicking prototype behavior)
    private final ConcurrentHashMap<String, PetIngestionJob> petIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    public PetIngestionJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .validate(this::validateSourceUrl, "Invalid sourceUrl in PetIngestionJob")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "petingestionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean validateSourceUrl(PetIngestionJob job) {
        return job.getSourceUrl() != null && !job.getSourceUrl().isBlank();
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        logger.info("Processing PetIngestionJob with id: {}", job.getId());

        // Set status to PROCESSING
        job.setStatus("PROCESSING");

        // Simulate creation of Pet entity from ingestion job
        Pet newPet = new Pet();
        newPet.setId(String.valueOf(petIdCounter.getAndIncrement()));
        newPet.setTechnicalId(UUID.randomUUID());
        newPet.setName("SamplePetFromIngestion");
        newPet.setCategory("cat");
        newPet.setStatus("AVAILABLE");

        // Here we should persist the new Pet entity or pass it to further processing
        // For now just logging
        logger.info("Created new Pet from ingestion job: {}", newPet);

        // Set status to COMPLETED
        job.setStatus("COMPLETED");

        return job;
    }
}
