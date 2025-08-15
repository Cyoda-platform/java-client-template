package com.java_template.application.processor;

import com.java_template.application.entity.importtask.version_1.ImportTask;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

@Component
public class ImportTaskProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportTaskProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ImportTaskProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportTask for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportTask.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportTask task) {
        return task != null;
    }

    private ImportTask processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportTask> context) {
        ImportTask task = context.entity();
        // In a real implementation, you would load HackerNewsItem from datastore using hnItemTechnicalId
        // Here we simulate the processing steps by assuming we have the HackerNewsItem in context attachments
        Object attachment = context.attachmentOrNull("hackerNewsItem");
        HackerNewsItem item = null;
        if (attachment instanceof HackerNewsItem) {
            item = (HackerNewsItem) attachment;
        }

        // Mark task as processing
        task.setStatus("PROCESSING");
        task.setLastUpdatedAt(Instant.now());
        task.setAttempts((task.getAttempts() == null ? 0 : task.getAttempts()) + 1);

        if (item == null) {
            // Can't process without item; mark failed
            task.setStatus("FAILED");
            task.setErrorMessage("Missing HackerNewsItem attachment");
            logger.warn("ImportTaskProcessor missing HackerNewsItem for task {}", task.getTechnicalId());
            return task;
        }

        // Run validation (business criterion) - in real flow this would be a separate criterion execution
        // Here we perform the equivalent checks inline for simplicity
        boolean hasId = item.getOriginalJson() != null && item.getOriginalJson().contains("\"id\"");
        boolean hasType = item.getOriginalJson() != null && item.getOriginalJson().contains("\"type\"");

        // Enrich
        try {
            // delegate to enrichment processor logic (simplified)
            // Assume enrichment sets importTimestamp and extracts id/type
            // We'll set importTimestamp only
            item.setImportTimestamp(Instant.now());
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage("Enrichment failed: " + e.getMessage());
            return task;
        }

        // Assign state
        if (hasId && hasType) {
            item.setState("VALID");
            item.setValidationErrors(null);
            task.setStatus("SUCCEEDED");
        } else {
            item.setState("INVALID");
            item.setValidationErrors("missing id and/or type");
            task.setStatus("FAILED");
        }

        // In real implementation persist item and task updates
        task.setLastUpdatedAt(Instant.now());
        return task;
    }
}
