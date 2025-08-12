package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class JobValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public JobValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating Job entity for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state or missing jobName")
            .map(context1 -> {
                Job job = context1.entity();
                // Update status to INGESTING and set startedAt timestamp
                job.setStatus("INGESTING");
                job.setStartedAt(Instant.now().toString());
                logger.info("Job {} status updated to INGESTING", job.getJobName());

                // Fetch laureates data from OpenDataSoft API
                try {
                    fetchAndPersistLaureates(job);
                    job.setStatus("SUCCEEDED");
                    job.setResultSummary("Laureates ingestion succeeded.");
                    logger.info("Job {} ingestion succeeded", job.getJobName());
                } catch (Exception e) {
                    job.setStatus("FAILED");
                    job.setResultSummary("Laureates ingestion failed: " + e.getMessage());
                    logger.error("Job {} ingestion failed", job.getJobName(), e);
                }

                return job;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        // Validate jobName is not null or empty
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private void fetchAndPersistLaureates(Job job) throws Exception {
        // Use HTTP client to fetch laureates data from OpenDataSoft API
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records"))
            .GET()
            .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch laureate data, status code: " + response.statusCode());
        }

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = entityService.getObjectMapper();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(response.body());
        com.fasterxml.jackson.databind.JsonNode recordsNode = rootNode.path("records");

        if (!recordsNode.isArray()) {
            throw new RuntimeException("Invalid laureate data format: 'records' is not an array");
        }

        int count = 0;
        for (com.fasterxml.jackson.databind.JsonNode recordNode : recordsNode) {
            com.fasterxml.jackson.databind.JsonNode fieldsNode = recordNode.path("fields");
            if (fieldsNode.isMissingNode()) {
                continue;
            }

            // Map fieldsNode to Laureate entity
            com.java_template.application.entity.laureate.version_1.Laureate laureate = objectMapper.treeToValue(fieldsNode, com.java_template.application.entity.laureate.version_1.Laureate.class);

            // Persist laureate entity asynchronously
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                com.java_template.application.entity.laureate.version_1.Laureate.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.laureate.version_1.Laureate.ENTITY_VERSION),
                laureate
            );
            idFuture.get();
            count++;
        }

        logger.info("Persisted {} laureates", count);
    }
}
