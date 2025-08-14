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
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Component
public class IngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    @Autowired
    public IngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid job entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.getJobName() != null && !entity.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        try {
            // Transition status to INGESTING
            entity.setStatus("INGESTING");
            entity.setCreatedAt(Instant.now().toString());
            logger.info("Job {} status set to INGESTING", entity.getJobName());

            // Fetch laureate data from OpenDataSoft API synchronously for processing
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(OPEN_DATA_SOFT_API))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Failed to fetch data from OpenDataSoft API, status code: {}", response.statusCode());
                entity.setStatus("FAILED");
                entity.setCompletedAt(Instant.now().toString());
                return entity;
            }

            String responseBody = response.body();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode recordsNode = rootNode.path("records");
            if (!recordsNode.isArray()) {
                logger.error("Unexpected API response format: 'records' is not an array");
                entity.setStatus("FAILED");
                entity.setCompletedAt(Instant.now().toString());
                return entity;
            }

            // Process each laureate record
            for (JsonNode recordNode : recordsNode) {
                JsonNode fieldsNode = recordNode.path("fields");
                if (fieldsNode.isMissingNode()) {
                    logger.warn("Record missing 'fields' node, skipping");
                    continue;
                }

                Laureate laureate = parseLaureateFromFields(fieldsNode);
                if (laureate == null) {
                    logger.warn("Failed to parse laureate from fields, skipping");
                    continue;
                }

                // Add laureate entity asynchronously
                CompletableFuture<UUID> addFuture = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureate
                );

                try {
                    addFuture.get(); // Wait for completion
                    logger.info("Laureate added: {} {}", laureate.getFirstname(), laureate.getSurname());
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to add laureate: {} {}, error: {}", laureate.getFirstname(), laureate.getSurname(), e.getMessage());
                }
            }

            // Set job status to SUCCEEDED and completedAt
            entity.setStatus("SUCCEEDED");
            entity.setCompletedAt(Instant.now().toString());
            logger.info("Job {} ingestion completed successfully", entity.getJobName());

        } catch (Exception e) {
            logger.error("Exception during ingestion processing: {}", e.getMessage());
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
        }
        return entity;
    }

    private Laureate parseLaureateFromFields(JsonNode fieldsNode) {
        try {
            Laureate laureate = new Laureate();
            laureate.setLaureateId(fieldsNode.path("id").asInt());
            laureate.setFirstname(fieldsNode.path("firstname").asText(null));
            laureate.setSurname(fieldsNode.path("surname").asText(null));
            laureate.setBorn(fieldsNode.path("born").asText(null));
            laureate.setDied(fieldsNode.path("died").asText(null));
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
        } catch (Exception e) {
            logger.error("Error parsing laureate fields: {}", e.getMessage());
            return null;
        }
    }
}
