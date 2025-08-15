package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.entity.importtask.version_1.ImportTask;
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
import java.util.UUID;

@Component
public class ImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

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

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getPayload() != null && !job.getPayload().trim().isEmpty();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        // Mark job in progress (in real implementation persist status updates)
        job.setStatus("IN_PROGRESS");
        // Persist a HackerNewsItem representing this payload
        HackerNewsItem item = new HackerNewsItem();
        item.setTechnicalId(UUID.randomUUID().toString());
        item.setOriginalJson(job.getPayload());
        item.setCreatedAt(Instant.now());
        // In a real implementation, you would persist item and create ImportTask records in datastore.
        ImportTask task = new ImportTask();
        task.setTechnicalId(UUID.randomUUID().toString());
        task.setJobTechnicalId(job.getTechnicalId());
        task.setHnItemTechnicalId(item.getTechnicalId());
        task.setStatus("QUEUED");
        task.setCreatedAt(Instant.now());

        logger.info("ImportJobProcessor created HackerNewsItem technicalId={} and ImportTask technicalId={}",
            item.getTechnicalId(), task.getTechnicalId());

        // Update job counters
        job.setItemsCreatedCount(1);
        job.setStatus("IN_PROGRESS");
        return job;
    }
}
