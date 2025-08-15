package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing JobValidationProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Basic validation rules derived from functional requirements
        boolean valid = true;

        // schedule validation: if job is not manual it must have a schedule
        try {
            if (!Boolean.TRUE.equals(job.getManual()) && (job.getSchedule() == null || job.getSchedule().trim().isEmpty())) {
                logger.warn("Job {} is non-manual but has no schedule", job.getTechnicalId());
                valid = false;
            }
        } catch (Exception e) {
            logger.debug("Schedule check failed: {}", e.getMessage());
            valid = false;
        }

        // transformRules must be present and parseable JSON
        if (job.getTransformRules() == null) {
            logger.warn("Job {} missing transformRules", job.getTechnicalId());
            valid = false;
        } else {
            try {
                objectMapper.readTree(job.getTransformRules());
            } catch (Exception e) {
                logger.warn("Job {} transformRules not valid JSON: {}", job.getTechnicalId(), e.getMessage());
                valid = false;
            }
        }

        // basic sourceUrl validation and optional head check
        if (job.getSourceUrl() == null || (!job.getSourceUrl().startsWith("http://") && !job.getSourceUrl().startsWith("https://"))) {
            logger.warn("Job {} has invalid sourceUrl: {}", job.getTechnicalId(), job.getSourceUrl());
            valid = false;
        } else {
            // perform a lightweight HEAD (or GET) to ensure source reachable, but do not fail hard on transient network errors
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(job.getSourceUrl()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();
                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                int status = resp.statusCode();
                if (status < 200 || status >= 400) {
                    logger.warn("Job {} sourceUrl returned non-2xx/3xx status={}", job.getTechnicalId(), status);
                    // don't mark invalid solely due to remote 4xx/5xx — treat as warning
                }
            } catch (Exception e) {
                logger.debug("Job {} sourceUrl reachability check failed: {}", job.getTechnicalId(), e.getMessage());
                // do not fail validation on transient network check; only treat as warning
            }
        }

        if (!valid) {
            job.setStatus("FAILED");
            // keep existing retry counts etc. Do not generate runId for failed validation
            return job;
        }

        // Passed validation: initialize run metadata and move to FETCHING
        String runId = UUID.randomUUID().toString();
        job.setLastRunAt(Instant.now().toString());
        // store runId in the resultSummary JSON so we can maintain idempotency without changing entity model
        Map<String, Object> rs = new HashMap<>();
        rs.put("runId", runId);
        try {
            job.setResultSummary(objectMapper.writeValueAsString(rs));
        } catch (Exception e) {
            job.setResultSummary("{}");
        }
        job.setStatus("FETCHING");
        logger.info("Job {} validation passed, runId={}", job.getTechnicalId(), runId);

        return job;
    }
}
