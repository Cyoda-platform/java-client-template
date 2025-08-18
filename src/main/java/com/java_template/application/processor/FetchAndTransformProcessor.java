package com.java_template.application.processor;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FetchAndTransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndTransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchAndTransformProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchAndTransform for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && "RUNNING".equalsIgnoreCase(entity.getStatus());
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        if (job == null) return null;
        try {
            // Note: actual HTTP fetching is out of scope; implement a simple simulated transform that increments importedCount
            AtomicInteger imported = new AtomicInteger(job.getImportedCount() == null ? 0 : job.getImportedCount());

            // Simulate importing 5 items per run. In real implementation this would page a remote API.
            int toImport = 5;
            for (int i = 0; i < toImport; i++) {
                try {
                    Pet pet = new Pet();
                    pet.setTechnicalId(null); // persistence layer assigns
                    pet.setName("Imported Pet " + (imported.get() + 1));
                    pet.setSpecies("cat");
                    pet.setLifecycleState("CREATED");
                    pet.setCreatedAt(Instant.now().toString());
                    pet.setUpdatedAt(Instant.now().toString());
                    // In a real implementation, persist pet here. Eventual persistence will trigger pet workflow.
                    imported.incrementAndGet();
                } catch (Exception e) {
                    logger.error("Error importing item for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
                    job.setErrorMessage((job.getErrorMessage() == null ? "" : job.getErrorMessage() + "\n") + e.getMessage());
                }
            }
            job.setImportedCount(imported.get());
            job.setStatus("PROCESSING_PETS");
            job.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.error("Error in fetch and transform for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setErrorMessage((job.getErrorMessage() == null ? "" : job.getErrorMessage() + "\n") + e.getMessage());
            job.setStatus("FAILED");
            job.setUpdatedAt(Instant.now().toString());
        }
        return job;
    }
}
