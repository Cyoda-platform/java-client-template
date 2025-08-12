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
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class IngestDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String OPEN_DATA_SOFT_API_URL = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    public IngestDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .validate(this::isValidEntity, "Invalid Job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().trim().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // 1. Update status to 'INGESTING'
        job.setStatus("INGESTING");
        job.setStartedAt(ZonedDateTime.now());
        logger.info("Job {} status updated to INGESTING", job.getJobName());

        // 2. Fetch laureates data from OpenDataSoft API
        try {
            List<Laureate> laureates = fetchLaureatesData();
            logger.info("Fetched {} laureates", laureates.size());

            // 3. Create Laureate entities
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (Laureate laureate : laureates) {
                CompletableFuture<UUID> future = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
                );
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            // 4. Update job status to SUCCEEDED
            job.setStatus("SUCCEEDED");
            job.setEndedAt(ZonedDateTime.now());
            job.setResultSummary("Processed " + laureates.size() + " laureates successfully.");
            logger.info("Job {} completed with status SUCCEEDED", job.getJobName());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing laureates data", e);
            job.setStatus("FAILED");
            job.setEndedAt(ZonedDateTime.now());
            job.setResultSummary("Failed to process laureates: " + e.getMessage());
        }

        return job;
    }

    private List<Laureate> fetchLaureatesData() throws InterruptedException, ExecutionException {
        // Using Java 11 HttpClient for HTTP request
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(OPEN_DATA_SOFT_API_URL))
            .GET()
            .build();

        try {
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            List<Laureate> laureates = new ArrayList<>();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode records = root.path("records");
            if (records.isArray()) {
                for (JsonNode record : records) {
                    JsonNode fields = record.path("record").path("fields");
                    Laureate laureate = parseLaureate(fields);
                    laureates.add(laureate);
                }
            }

            return laureates;
        } catch (Exception e) {
            logger.error("Failed to fetch or parse laureates data", e);
            throw new ExecutionException(e);
        }
    }

    private Laureate parseLaureate(JsonNode fields) {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(fields.path("id").asText(null));
        laureate.setFirstname(fields.path("firstname").asText(null));
        laureate.setSurname(fields.path("surname").asText(null));
        laureate.setGender(fields.path("gender").asText(null));
        laureate.setBorn(fields.path("born").asText(null));
        laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
        laureate.setBorncountry(fields.path("borncountry").asText(null));
        laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
        laureate.setBorncity(fields.path("borncity").asText(null));
        laureate.setYear(fields.path("year").asText(null));
        laureate.setCategory(fields.path("category").asText(null));
        laureate.setMotivation(fields.path("motivation").asText(null));
        laureate.setAffiliationName(fields.path("name").asText(null));
        laureate.setAffiliationCity(fields.path("city").asText(null));
        laureate.setAffiliationCountry(fields.path("country").asText(null));
        return laureate;
    }
}
