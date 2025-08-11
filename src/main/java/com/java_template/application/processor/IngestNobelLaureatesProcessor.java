package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

import static com.java_template.common.config.Config.*;

@Component
public class IngestNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IngestNobelLaureatesProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(ctx -> processJob(ctx.entity()))
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
            logger.error("Job jobName is null or empty");
            return false;
        }
        if (job.getScheduledTime() == null || job.getScheduledTime().isEmpty()) {
            logger.error("Job scheduledTime is null or empty");
            return false;
        }
        // Additional ISO 8601 timestamp validation can be added here
        return true;
    }

    private Job processJob(Job job) {
        job.setJobStatus("INGESTING");
        job.setStartedTime(Instant.now().toString());

        try {
            // Fetch Nobel laureates data from OpenDataSoft API
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Failed to fetch Nobel laureates data. Status code: {}", response.statusCode());
                job.setJobStatus("FAILED");
                job.setFinishedTime(Instant.now().toString());
                return job;
            }

            // Parse the JSON response
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode recordsNode = rootNode.path("records");
            if (!recordsNode.isArray()) {
                logger.error("Records node is not an array");
                job.setJobStatus("FAILED");
                job.setFinishedTime(Instant.now().toString());
                return job;
            }

            int successCount = 0;
            int failureCount = 0;

            for (JsonNode recordNode : recordsNode) {
                try {
                    JsonNode fields = recordNode.path("fields");
                    Laureate laureate = parseLaureate(fields);
                    if (laureate != null) {
                        // Save laureate entity asynchronously
                        CompletableFuture<UUID> future = entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                        );
                        future.get();
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to process laureate record", e);
                    failureCount++;
                }
            }

            job.setJobStatus("SUCCEEDED");
            job.setFinishedTime(Instant.now().toString());
            job.setResultSummary(successCount + " laureates ingested, " + failureCount + " failed");

        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.error("Exception during Nobel laureates ingestion", e);
            job.setJobStatus("FAILED");
            job.setFinishedTime(Instant.now().toString());
        }

        return job;
    }

    private Laureate parseLaureate(JsonNode fields) {
        try {
            Laureate laureate = new Laureate();
            laureate.setLaureateId(fields.path("id").asInt());
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
        } catch (Exception e) {
            logger.error("Error parsing laureate fields", e);
            return null;
        }
    }
}