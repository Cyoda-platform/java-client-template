package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String OPEN_DATASOFT_API_URL = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    public FetchNobelLaureatesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Step 3a: Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now());

            // Step 3b: Fetch Nobel laureates data from OpenDataSoft API
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(OPEN_DATASOFT_API_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to fetch Nobel laureates data from API, status code: " + response.statusCode());
                job.setFinishedAt(Instant.now());
                return job;
            }

            String responseBody = response.body();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            ArrayNode records = (ArrayNode) rootNode.path("records");
            if (records == null || records.isEmpty()) {
                job.setStatus("FAILED");
                job.setErrorMessage("No laureate records found in API response");
                job.setFinishedAt(Instant.now());
                return job;
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();

            for (JsonNode recordNode : records) {
                JsonNode fieldsNode = recordNode.path("record").path("fields");
                if (fieldsNode.isMissingNode()) {
                    continue;
                }

                Laureate laureate = mapJsonToLaureate(fieldsNode);
                if (laureate == null || !laureate.isValid()) {
                    logger.warn("Skipping invalid laureate record: {}", fieldsNode.toString());
                    continue;
                }

                CompletableFuture<UUID> future = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureate
                );
                futures.add(future);
            }

            // Wait for all adds to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Step 4a: If all laureates ingested successfully
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now());

        } catch (Exception ex) {
            logger.error("Error processing Job ingestion", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
        }
        return job;
    }

    private Laureate mapJsonToLaureate(JsonNode fieldsNode) {
        try {
            Laureate laureate = new Laureate();
            // Map fields from JSON to Laureate entity
            laureate.setLaureateId(fieldsNode.path("id").isNumber() ? fieldsNode.path("id").longValue() : null);
            laureate.setFirstname(fieldsNode.path("firstname").asText(null));
            laureate.setSurname(fieldsNode.path("surname").asText(null));
            laureate.setGender(fieldsNode.path("gender").asText(null));
            laureate.setBorn(fieldsNode.path("born").asText(null));
            laureate.setDied(fieldsNode.path("died").isNull() ? null : fieldsNode.path("died").asText(null));
            laureate.setBorncountry(fieldsNode.path("borncountry").asText(null));
            laureate.setBorncountrycode(fieldsNode.path("borncountrycode").asText(null));
            laureate.setBorncity(fieldsNode.path("borncity").asText(null));
            laureate.setYear(fieldsNode.path("year").asText(null));
            laureate.setCategory(fieldsNode.path("category").asText(null));
            laureate.setMotivation(fieldsNode.path("motivation").asText(null));
            laureate.setAffiliationName(fieldsNode.path("name").asText(null));
            laureate.setAffiliationCity(fieldsNode.path("city").asText(null));
            laureate.setAffiliationCountry(fieldsNode.path("country").asText(null));
            return laureate;
        } catch (Exception e) {
            logger.error("Error mapping laureate JSON to entity", e);
            return null;
        }
    }
}
