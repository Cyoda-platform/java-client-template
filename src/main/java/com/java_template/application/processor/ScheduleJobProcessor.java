package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class ScheduleJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Business logic:
        // 1. Check API availability at job.sourceUrl
        // 2. If unreachable -> mark job FAILED, set summary, finishedAt and return
        // 3. If reachable -> set job status to INGESTING, set startedAt and clear summary

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            logger.warn("Job {} has no sourceUrl defined, marking as FAILED", job.getJobId());
            job.setStatus("FAILED");
            job.setSummary("Missing sourceUrl");
            job.setFinishedAt(Instant.now().toString());
            return job;
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Try to perform a lightweight HEAD request if possible, fall back to GET on exception
        boolean available = false;
        String availabilityError = null;
        try {
            HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(Duration.ofSeconds(10))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<Void> headResponse = client.send(headRequest, HttpResponse.BodyHandlers.discarding());
            int status = headResponse.statusCode();
            logger.debug("HEAD request to {} returned status {}", sourceUrl, status);
            if (status >= 200 && status < 300) {
                available = true;
            } else {
                // Some servers may not support HEAD properly -> try GET
                HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<Void> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.discarding());
                status = getResponse.statusCode();
                logger.debug("GET fallback to {} returned status {}", sourceUrl, status);
                if (status >= 200 && status < 300) {
                    available = true;
                } else {
                    availabilityError = "Unexpected HTTP status: " + status;
                }
            }
        } catch (Throwable t) {
            // network errors or unsupported HEAD -> try GET as fallback if not already attempted
            logger.debug("HEAD request failed for {}: {}", sourceUrl, t.getMessage());
            try {
                HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<Void> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.discarding());
                int status = getResponse.statusCode();
                logger.debug("GET request to {} returned status {}", sourceUrl, status);
                if (status >= 200 && status < 300) {
                    available = true;
                } else {
                    availabilityError = "Unexpected HTTP status: " + status;
                }
            } catch (Throwable t2) {
                logger.error("Failed to reach source URL {}: {}", sourceUrl, t2.getMessage());
                availabilityError = t2.getMessage();
            }
        }

        if (!available) {
            logger.info("Source unreachable for job {}: {}", job.getJobId(), availabilityError);
            job.setStatus("FAILED");
            job.setSummary("Source unreachable" + (availabilityError != null ? ": " + availabilityError : ""));
            job.setFinishedAt(Instant.now().toString());
            return job;
        }

        // Source is available -> mark job as INGESTING and set startedAt
        logger.info("Source reachable for job {}. Marking as INGESTING", job.getJobId());
        job.setStatus("INGESTING");
        job.setStartedAt(Instant.now().toString());
        job.setSummary(null);
        job.setFinishedAt(null);

        // Emitting the ingest event is handled by workflow engine based on status transition.
        return job;
    }
}