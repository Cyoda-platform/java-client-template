package com.java_template.application.processor;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StartFetchProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetSyncJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetSyncJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetSyncJob entity) {
        return entity != null && entity.isValid();
    }

    private PetSyncJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetSyncJob> context) {
        PetSyncJob job = context.entity();

        // Initialize startTime if not present
        if (job.getStartTime() == null || job.getStartTime().isBlank()) {
            job.setStartTime(OffsetDateTime.now().toString());
        }

        // Basic validation of config and sourceUrl to decide next status
        Map<String, Object> cfg = job.getConfig();
        if (cfg == null) {
            logger.error("PetSyncJob {} missing config", job.getId());
            job.setStatus("failed");
            job.setErrorMessage("Missing config for job");
            job.setEndTime(OffsetDateTime.now().toString());
            return job;
        }

        Object sourceUrlObj = cfg.get("sourceUrl");
        String sourceUrl = sourceUrlObj != null ? String.valueOf(sourceUrlObj) : null;
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.error("PetSyncJob {} missing sourceUrl in config", job.getId());
            job.setStatus("failed");
            job.setErrorMessage("Missing sourceUrl in config");
            job.setEndTime(OffsetDateTime.now().toString());
            return job;
        }

        // At this stage we consider the job ready to fetch.
        // Actual HTTP fetching/parsing is performed by subsequent processors.
        logger.info("PetSyncJob {} starting fetch from {}", job.getId(), sourceUrl);
        job.setStatus("fetching");
        job.setErrorMessage(null);

        // Ensure fetchedCount initialized
        if (job.getFetchedCount() == null) {
            job.setFetchedCount(0);
        }

        // Persisting/updating of this triggering entity must NOT be done via entityService here.
        // The workflow runtime will persist changes to this entity automatically.
        // If needed, other entities may be added/updated via entityService (not used here).

        return job;
    }
}