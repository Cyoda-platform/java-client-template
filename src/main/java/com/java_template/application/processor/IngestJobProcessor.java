package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Component
public class IngestJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public IngestJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            logger.info("Starting ingest for job: {}", job.getTechnicalId());
            job.setStartTime(Instant.now().toString());
            job.setStatus("INGESTING");

            // Validate sourceUrl
            if (job.getSourceUrl() == null || job.getSourceUrl().isEmpty()) {
                job.setStatus("FAILED");
                job.setErrorDetails("Missing sourceUrl");
                job.setEndTime(Instant.now().toString());
                logger.error("Job {} failed because sourceUrl is missing", job.getTechnicalId());
                return job;
            }

            job.setStatus("FETCHING");

            // Fetch with retries
            JsonNode fetched = fetchWithRetries(job.getSourceUrl(), 3, Duration.ofSeconds(2));
            if (fetched == null) {
                job.setStatus("FAILED");
                job.setErrorDetails("Failed to fetch records from source");
                job.setEndTime(Instant.now().toString());
                return job;
            }

            // Extract records. Support both OpenDataSoft structure (records[].fields) and plain array
            ArrayNode records = objectMapper.createArrayNode();
            if (fetched.has("records") && fetched.get("records").isArray()) {
                for (JsonNode rec : fetched.get("records")) {
                    if (rec.has("fields")) {
                        records.add(rec.get("fields"));
                    } else {
                        records.add(rec);
                    }
                }
            } else if (fetched.isArray()) {
                for (JsonNode rec : fetched) records.add(rec);
            } else if (fetched.has("data") && fetched.get("data").isArray()) {
                for (JsonNode rec : fetched.get("data")) records.add(rec);
            } else {
                // If response shape unexpected, attempt to find a nested array
                Iterator<JsonNode> it = fetched.elements();
                while (it.hasNext()) {
                    JsonNode n = it.next();
                    if (n.isArray()) {
                        for (JsonNode rec : n) records.add(rec);
                        break;
                    }
                }
            }

            job.setFetchedRecordCount(records.size());
            job.setStatus("PROCESSING_RECORDS");

            // Initialize counters if null
            job.setPersistedRecordCount(job.getPersistedRecordCount() == null ? 0 : job.getPersistedRecordCount());
            job.setSucceededCount(job.getSucceededCount() == null ? 0 : job.getSucceededCount());
            job.setFailedCount(job.getFailedCount() == null ? 0 : job.getFailedCount());

            String dedupeStrategy = job.getDedupeStrategy() == null ? "UPSERT" : job.getDedupeStrategy();

            for (JsonNode rec : records) {
                try {
                    Laureate laureate = objectMapper.convertValue(rec, Laureate.class);
                    if (laureate == null || laureate.getId() == null) {
                        job.setFailedCount(job.getFailedCount() + 1);
                        logger.warn("Skipping record because laureate id is missing for job {}", job.getTechnicalId());
                        continue;
                    }

                    // Set provenance fields
                    laureate.setSourceJobTechnicalId(job.getTechnicalId());
                    laureate.setPersistedAt(Instant.now().toString());

                    // Check existing by business id
                    ArrayNode found = entityService.getItemsByCondition(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        SearchConditionRequest.group(
                            "AND",
                            Condition.of("$.id", "EQUALS", String.valueOf(laureate.getId()))
                        ),
                        true
                    ).join();

                    boolean exists = found != null && found.size() > 0;

                    if (!exists) {
                        // Create
                        CompletableFuture<java.util.UUID> addF = entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                        );
                        addF.join();
                        job.setPersistedRecordCount(job.getPersistedRecordCount() + 1);
                        job.setSucceededCount(job.getSucceededCount() + 1);
                    } else {
                        // Handle dedupe strategies
                        if ("SKIP_DUPLICATE".equalsIgnoreCase(dedupeStrategy)) {
                            logger.info("Skipping duplicate laureate {} due to SKIP_DUPLICATE", laureate.getId());
                            // skip - do not increment persistedRecordCount
                        } else if ("FAIL_ON_DUPLICATE".equalsIgnoreCase(dedupeStrategy)) {
                            logger.warn("Failing on duplicate laureate {} due to FAIL_ON_DUPLICATE", laureate.getId());
                            job.setFailedCount(job.getFailedCount() + 1);
                        } else {
                            // UPSERT - update existing
                            JsonNode first = found.get(0);
                            String existingTechId = null;
                            if (first.has("technicalId")) existingTechId = first.get("technicalId").asText();
                            else if (first.has("technical_id")) existingTechId = first.get("technical_id").asText();

                            if (existingTechId != null && !existingTechId.isEmpty()) {
                                try {
                                    entityService.updateItem(
                                        Laureate.ENTITY_NAME,
                                        String.valueOf(Laureate.ENTITY_VERSION),
                                        java.util.UUID.fromString(existingTechId),
                                        laureate
                                    ).join();
                                    job.setPersistedRecordCount(job.getPersistedRecordCount() + 1);
                                    job.setSucceededCount(job.getSucceededCount() + 1);
                                } catch (Exception ue) {
                                    logger.error("Failed to upsert laureate {}: {}", laureate.getId(), ue.getMessage(), ue);
                                    job.setFailedCount(job.getFailedCount() + 1);
                                }
                            } else {
                                // No technicalId found - fall back to creating a new item
                                entityService.addItem(
                                    Laureate.ENTITY_NAME,
                                    String.valueOf(Laureate.ENTITY_VERSION),
                                    laureate
                                ).join();
                                job.setPersistedRecordCount(job.getPersistedRecordCount() + 1);
                                job.setSucceededCount(job.getSucceededCount() + 1);
                            }
                        }
                    }

                } catch (Exception recEx) {
                    job.setFailedCount(job.getFailedCount() + 1);
                    logger.error("Error processing record for job {}: {}", job.getTechnicalId(), recEx.getMessage(), recEx);
                }
            }

            // Decide final job status
            if (job.getErrorDetails() != null && !job.getErrorDetails().isEmpty()) {
                job.setStatus("FAILED");
            } else if (job.getFailedCount() != null && job.getFailedCount() > 0) {
                job.setStatus("PARTIAL_FAILURE");
            } else {
                job.setStatus("SUCCEEDED");
            }

        } catch (Exception e) {
            logger.error("Fatal error while processing job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.toString());
        } finally {
            job.setEndTime(Instant.now().toString());
            logger.info("Job {} finished with status {}", job.getTechnicalId(), job.getStatus());
        }

        return job;
    }

    private JsonNode fetchWithRetries(String url, int maxAttempts, Duration initialBackoff) {
        int attempt = 0;
        Duration backoff = initialBackoff;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code >= 200 && code < 300) {
                    return objectMapper.readTree(response.body());
                } else if (code >= 500 && attempt < maxAttempts) {
                    logger.warn("Transient error fetching {}: status {}. Retrying in {} ms", url, code, backoff.toMillis());
                    Thread.sleep(backoff.toMillis());
                    backoff = backoff.multipliedBy(2);
                } else {
                    logger.error("Non-recoverable HTTP error fetching {}: status {}", url, code);
                    return null;
                }
            } catch (Exception e) {
                logger.warn("Exception fetching {} (attempt {}): {}", url, attempt, e.getMessage());
                try { Thread.sleep(backoff.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                backoff = backoff.multipliedBy(2);
            }
        }
        return null;
    }
}
