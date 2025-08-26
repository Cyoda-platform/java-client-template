package com.java_template.application.processor;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;

@Component
public class ValidateSourceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSourceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSourceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob entity = context.entity();

        // Ensure counts are initialized
        if (entity.getProcessedCount() == null) {
            entity.setProcessedCount(0);
        }
        if (entity.getFailedCount() == null) {
            entity.setFailedCount(0);
        }

        String sourceUrl = entity.getSourceUrl();
        StringBuilder noteBuilder = new StringBuilder();
        if (entity.getNotes() != null && !entity.getNotes().isBlank()) {
            noteBuilder.append(entity.getNotes()).append(" | ");
        }
        noteBuilder.append("ValidateSourceProcessor run at ").append(Instant.now().toString()).append(": ");

        if (sourceUrl == null || sourceUrl.isBlank()) {
            String msg = "Source URL is blank or missing";
            logger.warn(msg + " for ImportJob {}", entity.getJobId());
            entity.setStatus("FAILED");
            noteBuilder.append(msg);
            entity.setNotes(noteBuilder.toString());
            return entity;
        }

        // Basic URL format and reachability check
        try {
            URL url = new URL(sourceUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int code = conn.getResponseCode();
            conn.disconnect();

            if (code >= 200 && code < 400) {
                // Source reachable
                logger.info("Source reachable (HTTP {}) for ImportJob {}", code, entity.getJobId());
                entity.setStatus("VALIDATING");
                noteBuilder.append("Source reachable (HTTP ").append(code).append(")");
            } else {
                String msg = "Source returned non-success response code: " + code;
                logger.warn(msg + " for ImportJob {}", entity.getJobId());
                entity.setStatus("FAILED");
                // increment failedCount to reflect failure at validation stage
                entity.setFailedCount(entity.getFailedCount() + 1);
                noteBuilder.append(msg);
            }
        } catch (IOException e) {
            String msg = "Source unreachable: " + e.getMessage();
            logger.warn("Source unreachable for ImportJob {}: {}", entity.getJobId(), e.getMessage());
            entity.setStatus("FAILED");
            entity.setFailedCount(entity.getFailedCount() + 1);
            noteBuilder.append(msg);
        } catch (Exception e) {
            String msg = "Unexpected error during source validation: " + e.getMessage();
            logger.error("Error validating source for ImportJob {}: {}", entity.getJobId(), e.getMessage());
            entity.setStatus("FAILED");
            entity.setFailedCount(entity.getFailedCount() + 1);
            noteBuilder.append(msg);
        }

        entity.setNotes(noteBuilder.toString());
        return entity;
    }
}