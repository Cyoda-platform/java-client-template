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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;
    private final AtomicInteger petIdCounter = new AtomicInteger(1);

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
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
                .validate(PurrfectPetsJob::isValid, "Invalid PurrfectPetsJob entity state")
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
        logger.info("Processing PurrfectPetsJob with jobName: {}", job.getJobName());
        try {
            // Simulate fetching pet data from Petstore API
            // For demo, just log and create a dummy pet entity
            Pet demoPet = new Pet();
            demoPet.setPetId(999L);
            demoPet.setName("DemoPet");
            demoPet.setCategory("cat");
            demoPet.setStatus("available");
            demoPet.setPhotoUrls("http://example.com/photo1.jpg");
            demoPet.setTags("demo,example");

            // Create new pet event (immutable) using entityService
            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, demoPet);
            UUID petTechnicalId = petIdFuture.get();
            String petTechIdStr = "pet-" + petIdCounter.getAndIncrement();
            logger.info("Ingested Pet with technicalId: {}", petTechIdStr);

            // Optionally create orders similarly if parameters request (skipped here)

            job.setStatus("COMPLETED");
            logger.info("PurrfectPetsJob {} completed successfully", job.getJobName());
        } catch (Exception e) {
            logger.error("Failed processing PurrfectPetsJob {}: {}", job.getJobName(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }
}
