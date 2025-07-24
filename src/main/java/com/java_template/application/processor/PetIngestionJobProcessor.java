package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
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
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .validate(PetIngestionJob::isValid, "Invalid PetIngestionJob state")
                .map(this::processPetIngestionJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
               "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetIngestionJob processPetIngestionJob(PetIngestionJob job) {
        logger.info("Processing PetIngestionJob");
        job.setStatus("PROCESSING");
        try {
            if (!job.getSource().startsWith("http")) {
                throw new IllegalArgumentException("Invalid source URL");
            }
            // Simulate fetching pet data from Petstore API - create dummy pets for demo
            Pet pet1 = new Pet();
            pet1.setName("Fluffy");
            pet1.setType("Cat");
            pet1.setStatus("AVAILABLE");
            pet1.setCreatedAt(LocalDateTime.now());
            entityService.addItem("pet", Config.ENTITY_VERSION, pet1).get();
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setName("Buddy");
            pet2.setType("Dog");
            pet2.setStatus("AVAILABLE");
            pet2.setCreatedAt(LocalDateTime.now());
            entityService.addItem("pet", Config.ENTITY_VERSION, pet2).get();
            processPet(pet2);

            job.setStatus("COMPLETED");
            logger.info("PetIngestionJob completed successfully");
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("PetIngestionJob failed: {}", e.getMessage());
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet");
        if (!pet.isValid()) {
            logger.error("Pet is invalid");
            return;
        }
        logger.info("Pet is ready and available with status {}", pet.getStatus());
    }
}
