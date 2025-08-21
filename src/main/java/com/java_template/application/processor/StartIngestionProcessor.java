package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
import com.java_template.application.entity.userrecord.version_1.UserRecord;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StartIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartIngestionProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid batch job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob job) {
        return job != null && job.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob job = context.entity();
        try {
            job.setStatus("IN_PROGRESS");
            job.setStartedAt(Instant.now().toString());

            // Fetch users from Fakerest
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://fakerestapi.azurewebsites.net/api/v1/Users"))
                .GET()
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                ArrayNode array = (ArrayNode) objectMapper.readTree(resp.body());
                int count = 0;
                List<CompletableFuture<UUID>> futures = new ArrayList<>();
                for (JsonNode n : array) {
                    try {
                        UserRecord u = new UserRecord();
                        u.setSourcePayload(n.toString());
                        u.setStatus("INGESTED");
                        u.setTransformedAt(null);
                        u.setNormalized(false);
                        u.setStoredAt(null);
                        u.setLastSeen(null);

                        // Add user via entity service
                        CompletableFuture<UUID> f = entityService.addItem(
                            UserRecord.ENTITY_NAME,
                            String.valueOf(UserRecord.ENTITY_VERSION),
                            u
                        );
                        futures.add(f);
                        count++;
                    } catch (Exception ex) {
                        logger.warn("Failed to create UserRecord for one item", ex);
                    }
                }

                // Wait for all add operations to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                job.setProcessedUserCount(count);
                job.setStatus("COLLECTING_RESULTS");
                logger.info("StartIngestionProcessor ingested {} users", count);
                return job;
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to fetch users, status=" + resp.statusCode());
                logger.error("Failed to fetch users from Fakerest: status {}", resp.statusCode());
                return job;
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during StartIngestionProcessor", ex);
            job.setStatus("FAILED");
            job.setErrorMessage("Unexpected error during ingestion: " + ex.getMessage());
            return job;
        }
    }
}
