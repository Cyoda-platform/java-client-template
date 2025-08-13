package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class JobIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    @Autowired
    public JobIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob)
            .map(this::processJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        if (job == null) {
            logger.error("Job entity is null");
            return false;
        }
        if (job.getJobName() == null || job.getJobName().isEmpty()) {
            logger.error("Job name is required");
            return false;
        }
        return true;
    }

    private Job processJobLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Step 1: Change status from SCHEDULED to INGESTING
            if ("scheduled".equalsIgnoreCase(job.getStatus())) {
                job.setStatus("INGESTING");
                job.setCreatedAt(Instant.now().toString());
                logger.info("Job status set to INGESTING");
            }

            // Step 2: Ingest laureate data from OpenDataSoft API
            logger.info("Starting ingestion of laureate data from OpenDataSoft API");
            ResponseEntity<String> response = restTemplate.exchange(OPEN_DATA_SOFT_API, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch laureates data, status: " + response.getStatusCode());
            }

            String body = response.getBody();
            JsonNode rootNode = objectMapper.readTree(body);
            JsonNode recordsNode = rootNode.path("records");
            if (!recordsNode.isArray()) {
                throw new RuntimeException("Invalid laureates data structure from API");
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();

            for (JsonNode recordNode : recordsNode) {
                JsonNode fields = recordNode.path("fields");
                Laureate laureate = mapJsonToLaureate(fields);

                // Validate mandatory fields
                if (!laureate.isValid()) {
                    logger.warn("Skipping invalid laureate: {} {}", laureate.getFirstname(), laureate.getSurname());
                    continue;
                }

                // Enrich laureate data
                enrichLaureate(laureate);

                // Persist laureate asynchronously
                CompletableFuture<UUID> future = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
                );
                futures.add(future);
            }

            // Wait for all laureate saves to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // Step 3: After ingestion, set status to SUCCEEDED
            job.setStatus("SUCCEEDED");
            job.setCompletedAt(Instant.now().toString());
            logger.info("Job ingestion succeeded with {} laureates", futures.size());

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            job.setStatus("FAILED");
            job.setErrorMessage(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            logger.error("Job ingestion failed: {}", e.getMessage(), e);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            logger.error("Job ingestion failed: {}", e.getMessage(), e);
        }

        return job;
    }

    private Laureate mapJsonToLaureate(JsonNode fields) {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(fields.path("id").asText(null));
        laureate.setFirstname(fields.path("firstname").asText(null));
        laureate.setSurname(fields.path("surname").asText(null));
        laureate.setGender(fields.path("gender").asText(null));
        laureate.setBorn(fields.path("born").asText(null));
        laureate.setDied(fields.path("died").asText(null));
        laureate.setBorncountry(fields.path("borncountry").asText(null));
        laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
        laureate.setBorncity(fields.path("borncity").asText(null));
        laureate.setYear(fields.path("year").asText(null));
        laureate.setCategory(fields.path("category").asText(null));
        laureate.setMotivation(fields.path("motivation").asText(null));
        laureate.setName(fields.path("name").asText(null));
        laureate.setCity(fields.path("city").asText(null));
        laureate.setCountry(fields.path("country").asText(null));
        return laureate;
    }

    private void enrichLaureate(Laureate laureate) {
        // Normalize borncountrycode to uppercase
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        // Additional enrichment logic can be added here if needed
    }
}
