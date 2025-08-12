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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class IngestNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    public IngestNobelLaureatesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && entity.getStatus() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Starting ingestion process for job: {}", job.getJobName());

        try {
            job.setStatus("INGESTING");
            logger.info("Job status set to INGESTING");

            // Fetch laureate data from OpenDataSoft API
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(OPEN_DATA_SOFT_API))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Failed to fetch data from OpenDataSoft API. Status code: {}", response.statusCode());
                job.setStatus("FAILED");
                return job;
            }

            String responseBody = response.body();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            ArrayNode records = (ArrayNode) rootNode.path("records");
            if (records == null || records.isEmpty()) {
                logger.warn("No laureate records found in API response");
                job.setStatus("FAILED");
                return job;
            }

            List<CompletableFuture<UUID>> futures = new ArrayList<>();

            for (JsonNode recordNode : records) {
                JsonNode fieldsNode = recordNode.path("record").path("fields");
                if (fieldsNode.isMissingNode()) {
                    logger.warn("Missing fields node in record");
                    continue;
                }

                Laureate laureate = mapToLaureate(fieldsNode);
                if (!laureate.isValid()) {
                    logger.warn("Invalid laureate data, skipping: {}", laureate);
                    continue;
                }

                CompletableFuture<UUID> future = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
                );
                futures.add(future);
            }

            // Wait for all futures to complete
            List<UUID> ids = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            logger.info("Ingested {} laureate records successfully", ids.size());

            job.setStatus("SUCCEEDED");
            logger.info("Job ingestion succeeded");
        } catch (Exception e) {
            logger.error("Error during ingestion process", e);
            job.setStatus("FAILED");
        }

        return job;
    }

    private Laureate mapToLaureate(JsonNode fieldsNode) {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(fieldsNode.path("id").asText(null));
        laureate.setFirstname(fieldsNode.path("firstname").asText(null));
        laureate.setSurname(fieldsNode.path("surname").asText(null));
        laureate.setBorn(fieldsNode.path("born").asText(null));
        laureate.setDied(fieldsNode.path("died").isNull() ? null : fieldsNode.path("died").asText(null));
        laureate.setBorncountry(fieldsNode.path("borncountry").asText(null));
        laureate.setBorncountrycode(fieldsNode.path("borncountrycode").asText(null));
        laureate.setBorncity(fieldsNode.path("borncity").asText(null));
        laureate.setGender(fieldsNode.path("gender").asText(null));
        laureate.setYear(fieldsNode.path("year").asText(null));
        laureate.setCategory(fieldsNode.path("category").asText(null));
        laureate.setMotivation(fieldsNode.path("motivation").asText(null));
        laureate.setAffiliationName(fieldsNode.path("name").asText(null));
        laureate.setAffiliationCity(fieldsNode.path("city").asText(null));
        laureate.setAffiliationCountry(fieldsNode.path("country").asText(null));
        return laureate;
    }
}
