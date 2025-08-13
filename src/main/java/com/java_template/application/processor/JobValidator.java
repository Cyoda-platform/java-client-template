package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class JobValidator implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobValidator.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobValidator(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state or missing required fields")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (job.getJobName() == null || job.getJobName().isEmpty()) {
            logger.error("JobName is null or empty");
            return false;
        }
        if (job.getStatus() == null || job.getStatus().isEmpty()) {
            logger.error("Status is null or empty");
            return false;
        }
        if (job.getTriggerTime() == null || job.getTriggerTime().isEmpty()) {
            logger.error("TriggerTime is null or empty");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Business logic: Transition status from SCHEDULED to INGESTING
        if ("scheduled".equalsIgnoreCase(job.getStatus())) {
            logger.info("Updating job status from SCHEDULED to INGESTING");
            job.setStatus("INGESTING");

            // Trigger ingestion asynchronously
            fetchAndIngestLaureates(job);
        }

        return job;
    }

    private void fetchAndIngestLaureates(Job job) {
        String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .build();

        logger.info("Starting ingestion from OpenDataSoft API");

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenAccept(responseBody -> {
                try {
                    ObjectNode root = (ObjectNode) objectMapper.readTree(responseBody);
                    ArrayNode records = (ArrayNode) root.path("records");
                    int successCount = 0;
                    for (JsonNode record : records) {
                        JsonNode fields = record.path("record").path("fields");
                        Laureate laureate = new Laureate();
                        laureate.setLaureateId(fields.path("id").asInt());
                        laureate.setFirstname(fields.path("firstname").asText(null));
                        laureate.setSurname(fields.path("surname").asText(null));
                        laureate.setBorn(fields.path("born").asText(null));
                        laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
                        laureate.setBorncountry(fields.path("borncountry").asText(null));
                        laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                        laureate.setBorncity(fields.path("borncity").asText(null));
                        laureate.setGender(fields.path("gender").asText(null));
                        laureate.setYear(fields.path("year").asText(null));
                        laureate.setCategory(fields.path("category").asText(null));
                        laureate.setMotivation(fields.path("motivation").asText(null));
                        laureate.setAffiliationName(fields.path("name").asText(null));
                        laureate.setAffiliationCity(fields.path("city").asText(null));
                        laureate.setAffiliationCountry(fields.path("country").asText(null));

                        // Save laureate entity asynchronously
                        entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                        ).thenAccept(id -> {
                            logger.info("Saved laureate with id: {}", id);
                        }).exceptionally(ex -> {
                            logger.error("Failed to save laureate: {}", ex.getMessage());
                            return null;
                        });

                        successCount++;
                    }

                    // After processing all laureates, update job status to SUCCEEDED
                    job.setStatus("SUCCEEDED");
                    job.setIngestionResult(successCount + " laureates ingested successfully");
                    logger.info("Ingestion completed successfully with {} laureates", successCount);

                } catch (Exception e) {
                    logger.error("Failed to parse laureates data: {}", e.getMessage());
                    job.setStatus("FAILED");
                    job.setIngestionResult("Failed to ingest laureates data");
                }
            })
            .exceptionally(ex -> {
                logger.error("Ingestion API call failed: {}", ex.getMessage());
                job.setStatus("FAILED");
                job.setIngestionResult("Failed to fetch laureates data");
                return null;
            });
    }
}
