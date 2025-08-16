package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.pet.version_1.Pet;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Component
public class ImportFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ImportFetchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob fetch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getJobId() != null && !job.getJobId().isEmpty() && job.getSourceUrl() != null && !job.getSourceUrl().isEmpty();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            String status = job.getStatus();
            if (status != null && !"PENDING".equals(status)) {
                logger.info("ImportJob {} is in status {} - skipping fetch", job.getJobId(), status);
                return job;
            }

            job.setStatus("IN_PROGRESS");
            try {
                job.setStartedAt(Instant.now().toString());
            } catch (Throwable ignore) {
            }

            // perform HTTP GET to job.getSourceUrl() with retry/backoff
            String sourceUrl = job.getSourceUrl();
            int maxAttempts = 3;
            int attempt = 0;
            String lastError = null;
            ArrayNode items = null;

            while (attempt < maxAttempts) {
                attempt++;
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(sourceUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    int statusCode = response.statusCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        String body = response.body();
                        JsonNode root = objectMapper.readTree(body);
                        // Support two shapes: array at root, or { "pets": [ ... ] }
                        if (root.isArray()) {
                            items = (ArrayNode) root;
                        } else if (root.has("pets") && root.get("pets").isArray()) {
                            items = (ArrayNode) root.get("pets");
                        } else if (root.has("data") && root.get("data").isArray()) {
                            items = (ArrayNode) root.get("data");
                        } else {
                            // unknown shape - wrap single object into array
                            items = objectMapper.createArrayNode();
                            items.add(root);
                        }
                        lastError = null;
                        break;
                    } else {
                        lastError = "HTTP " + statusCode + " returned";
                        logger.warn("ImportJob {} fetch attempt {} returned status {}", job.getJobId(), attempt, statusCode);
                    }
                } catch (IOException | InterruptedException e) {
                    lastError = e.getMessage();
                    logger.warn("ImportJob {} fetch attempt {} failed: {}", job.getJobId(), attempt, e.getMessage());
                    // exponential backoff
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            if (items == null) {
                job.setStatus("FAILED");
                job.setErrorMessage(lastError == null ? "fetch failed" : lastError);
                try {
                    job.setCompletedAt(Instant.now().toString());
                } catch (Throwable ignore) {
                }
                logger.error("ImportJob {} fetch failed after {} attempts: {}", job.getJobId(), maxAttempts, lastError);
                return job;
            }

            // mark processing and upsert each pet
            job.setStatus("PROCESSING");
            int successCount = 0;
            int total = items.size();

            Iterator<JsonNode> it = items.elements();
            while (it.hasNext()) {
                JsonNode node = it.next();
                try {
                    Pet pet = objectMapper.treeToValue(node, Pet.class);
                    if (pet.getPetId() == null || pet.getPetId().isEmpty()) {
                        pet.setPetId(java.util.UUID.randomUUID().toString());
                    }

                    if (pet.getStatus() == null || pet.getStatus().isEmpty()) {
                        pet.setStatus("CREATED");
                    }

                    ObjectNode petNode = objectMapper.valueToTree(pet);
                    CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        petNode
                    );
                    addFuture.get();
                    successCount++;
                } catch (Exception e) {
                    logger.warn("ImportJob {} failed to import one pet: {}", job.getJobId(), e.getMessage());
                    // continue with others
                }
            }

            job.setImportedCount(successCount);
            job.setStatus(successCount == total ? "COMPLETED" : "FAILED");
            try {
                job.setCompletedAt(Instant.now().toString());
            } catch (Throwable ignore) {
            }

            logger.info("ImportJob {} processed: {}/{} imported", job.getJobId(), successCount, total);
            return job;
        } catch (Exception e) {
            logger.error("Unhandled error while fetching import job {}", job == null ? "<null>" : job.getJobId(), e);
            if (job != null) {
                job.setStatus("FAILED");
                try {
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(Instant.now().toString());
                } catch (Throwable ignore) {
                }
            }
            return job;
        }
    }
}
