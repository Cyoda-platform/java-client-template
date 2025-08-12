package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    private static final String OPEN_DATA_SOFT_URL = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=nobel-prizes&q=&rows=50";

    @Autowired
    public LaureateIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Laureate.class)
                .map(context1 -> {
                    try {
                        return ingestLaureates(request.getId().toString());
                    } catch (Exception e) {
                        logger.error("Error ingesting laureates", e);
                        throw new RuntimeException(e);
                    }
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Laureate ingestLaureates(String jobId) throws ExecutionException, InterruptedException {
        logger.info("Starting ingestion job {}", jobId);

        // Update job status to STARTED
        updateJobStatus(jobId, "STARTED");

        // Fetch laureate data from OpenDataSoft API
        ArrayNode records = fetchLaureateData();
        if (records == null) {
            updateJobStatus(jobId, "FAILED_TO_FETCH");
            throw new RuntimeException("Failed to fetch laureate data");
        }

        // Process each laureate record
        for (JsonNode record : records) {
            try {
                Laureate laureate = parseLaureate(record);
                if (laureate != null) {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                    );
                    idFuture.get();
                }
            } catch (Exception e) {
                logger.error("Error processing laureate record", e);
            }
        }

        // Update job status to COMPLETED
        updateJobStatus(jobId, "COMPLETED");

        // Return dummy entity for completion
        return new Laureate();
    }

    private ArrayNode fetchLaureateData() {
        try {
            // Use Java's built-in HttpClient to avoid external dependencies
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(OPEN_DATA_SOFT_URL))
                    .GET()
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var mapper = serializer.getObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                return (ArrayNode) root.path("records");
            } else {
                logger.error("Failed to fetch laureate data: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Exception fetching laureate data", e);
        }
        return null;
    }

    private Laureate parseLaureate(JsonNode record) {
        try {
            ObjectNode fields = (ObjectNode) record.path("fields");
            Laureate laureate = new Laureate();
            laureate.setId(fields.path("id").asText(null));
            laureate.setFirstname(fields.path("firstname").asText(null));
            laureate.setSurname(fields.path("surname").asText(null));
            laureate.setBorn(fields.path("born").asText(null));
            laureate.setDied(fields.path("died").asText(null));
            laureate.setBornCountry(fields.path("born_country").asText(null));
            laureate.setBornCity(fields.path("born_city").asText(null));
            laureate.setGender(fields.path("gender").asText(null));
            laureate.setPrizes(fields.path("prizes")); // Assuming prizes is a JSON array
            return laureate;
        } catch (Exception e) {
            logger.error("Error parsing laureate record", e);
            return null;
        }
    }

    private void updateJobStatus(String jobId, String status) {
        try {
            // Retrieve job entity
            CompletableFuture<ObjectNode> jobFuture = entityService.getItem(
                    "Job", // Assuming Job entity name
                    "1",
                    UUID.fromString(jobId)
            );
            ObjectNode job = jobFuture.get();
            if (job != null) {
                job.put("status", status);
                if ("STARTED".equals(status)) {
                    job.put("startedAt", Instant.now().toString());
                } else if ("COMPLETED".equals(status)) {
                    job.put("completedAt", Instant.now().toString());
                }
                entityService.addItem("Job", "1", job).get(); // Save updated job
            }
        } catch (Exception e) {
            logger.error("Failed to update job status", e);
        }
    }
}
