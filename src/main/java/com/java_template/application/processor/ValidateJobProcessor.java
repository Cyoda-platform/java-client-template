package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataIngestJob validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataIngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataIngestJob entity) {
        return entity != null && entity.isValid();
    }

    private DataIngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataIngestJob> context) {
        DataIngestJob job = context.entity();
        try {
            logger.info("ValidateJobProcessor starting for jobTechnicalId={}", job.getTechnicalId());
            // mark as validating
            job.setStatus("VALIDATING");

            // If scheduled in the future, mark SCHEDULED and return (scheduler will re-trigger validation later)
            String scheduledAt = job.getScheduled_at();
            if (scheduledAt != null && !scheduledAt.isBlank()) {
                try {
                    Instant scheduledInstant = Instant.parse(scheduledAt);
                    if (scheduledInstant.isAfter(Instant.now())) {
                        job.setStatus("SCHEDULED");
                        logger.info("Job {} scheduled for future execution at {}", job.getTechnicalId(), scheduledAt);
                        return job;
                    }
                } catch (DateTimeParseException e) {
                    logger.warn("Unable to parse scheduled_at value {} for job {}: {}", scheduledAt, job.getTechnicalId(), e.getMessage());
                    // proceed with validation - malformed date treated as immediate
                }
            }

            // Basic reachability check for source_url: attempt a HEAD-like GET with small timeout
            String sourceUrl = job.getSource_url();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                job.setStatus("FAILED");
                logger.warn("Job {} failed validation: empty source_url", job.getTechnicalId());
                return job;
            }

            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                int status = resp.statusCode();
                if (status < 200 || status >= 400) {
                    job.setStatus("FAILED");
                    logger.warn("Job {} failed validation: source_url returned status {}", job.getTechnicalId(), status);
                    return job;
                }
            } catch (Exception e) {
                logger.warn("Job {} failed validation: cannot reach source_url {} - {}", job.getTechnicalId(), sourceUrl, e.getMessage());
                job.setStatus("FAILED");
                return job;
            }

            // All validations passed -> move to DOWNLOADING
            job.setStatus("DOWNLOADING");
            logger.info("Job {} passed validation and moved to DOWNLOADING", job.getTechnicalId());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error while validating job {}: {}", job.getTechnicalId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}
