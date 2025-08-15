package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;

@Component
public class IngestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpUtils httpUtils;

    // Default OpenDataSoft endpoint
    private static final String DEFAULT_ENDPOINT = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

    public IngestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper, HttpUtils httpUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpUtils = httpUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            logger.info("Starting ingestion for job id={}, name={}", job.getId(), job.getName());
            job.setStartedAt(Instant.now().toString());
            job.setStatus("INGESTING");

            int processed = 0;

            // Business-friendly ingestion: support inlined sample payloads in job.parameters for testing and deterministic behavior
            if (job.getParameters() != null && job.getParameters().containsKey("sampleRecords")) {
                Object raw = job.getParameters().get("sampleRecords");
                if (raw instanceof List) {
                    List<?> raws = (List<?>) raw;
                    for (Object item : raws) {
                        try {
                            // Map each item to Laureate using ObjectMapper for flexibility
                            Laureate laureate = objectMapper.convertValue(item, Laureate.class);
                            // set ingestion metadata
                            laureate.setSourceFetchedAt(Instant.now().toString());
                            laureate.setStatus("RECEIVED");

                            // Persist the laureate (create)
                            try {
                                UUID technicalId = entityService.addItem(
                                    Laureate.ENTITY_NAME,
                                    String.valueOf(Laureate.ENTITY_VERSION),
                                    laureate
                                ).join();
                                logger.info("Persisted Laureate (ingest) id={}, technicalId={}", laureate.getId(), technicalId);
                            } catch (CompletionException ce) {
                                logger.error("Failed to persist laureate during ingest: {}", ce.getMessage(), ce);
                                // mark lastError on job but continue processing
                                job.setLastError(ce.getMessage());
                            }

                            processed++;
                        } catch (Exception e) {
                            logger.error("Failed to map/persist sample record during ingest: {}", e.getMessage(), e);
                            job.setLastError(e.getMessage());
                        }
                    }
                }
            } else {
                // External HTTP ingestion with pagination and basic rate-limit handling
                String apiUrl = job.getSourceEndpoint() != null && !job.getSourceEndpoint().isBlank() ? job.getSourceEndpoint() : DEFAULT_ENDPOINT;
                Map<String, Object> params = job.getParameters() == null ? Collections.emptyMap() : job.getParameters();
                int pageSize = 100;
                if (params.containsKey("pageSize") && params.get("pageSize") instanceof Number) {
                    pageSize = ((Number) params.get("pageSize")).intValue();
                } else if (params.containsKey("limit") && params.get("limit") instanceof Number) {
                    pageSize = ((Number) params.get("limit")).intValue();
                }

                int offset = 0;
                if (params.containsKey("offset") && params.get("offset") instanceof Number) {
                    offset = ((Number) params.get("offset")).intValue();
                }

                int maxPages = params.containsKey("maxPages") && params.get("maxPages") instanceof Number ? ((Number) params.get("maxPages")).intValue() : Integer.MAX_VALUE;
                int pages = 0;

                // Job-level retry policy for rate-limits
                Map<String, Object> rp = params.containsKey("retryPolicy") && params.get("retryPolicy") instanceof Map ? (Map<String,Object>) params.get("retryPolicy") : job.getSubscriberFilters() == null ? null : null;
                Map<String, Object> jobRetry = job.getParameters() != null && job.getParameters().get("retryPolicy") instanceof Map ? (Map<String,Object>) job.getParameters().get("retryPolicy") : null;
                if (jobRetry == null) jobRetry = job.getSubscriberFilters() == null ? null : null; // keep null if absent

                int defaultRetries = 3;
                int initialBackoffSeconds = 2;
                boolean exponential = true;
                if (job.getParameters() != null && job.getParameters().get("retryPolicy") instanceof Map) {
                    Map<String,Object> jrp = (Map<String,Object>) job.getParameters().get("retryPolicy");
                    if (jrp.get("maxRetries") instanceof Number) defaultRetries = ((Number) jrp.get("maxRetries")).intValue();
                    if (jrp.get("initialBackoffSeconds") instanceof Number) initialBackoffSeconds = ((Number) jrp.get("initialBackoffSeconds")).intValue();
                    if (jrp.get("exponential") instanceof Boolean) exponential = (Boolean) jrp.get("exponential");
                } else if (job.getSubscriberFilters() != null && job.getSubscriberFilters().containsKey("retryPolicy")) {
                    // fallback ignored
                }

                boolean continuePaging = true;
                while (continuePaging && pages < maxPages) {
                    pages++;
                    Map<String,String> query = new HashMap<>();
                    query.put("limit", String.valueOf(pageSize));
                    query.put("offset", String.valueOf(offset));

                    int attempt = 0;
                    boolean pageFetched = false;
                    while (!pageFetched && attempt <= defaultRetries) {
                        attempt++;
                        try {
                            logger.info("Fetching page {} (limit={}, offset={}) from {}", pages, pageSize, offset, apiUrl);
                            ObjectNodeWrapper response = new ObjectNodeWrapper(httpUtils.sendGetRequest(null, apiUrl, null, query).join());
                            int status = response.getStatus();
                            JsonNode json = response.getJson();

                            if (json == null) {
                                logger.warn("Empty JSON response for page {}", pages);
                                pageFetched = true; // stop on empty
                                continuePaging = false;
                                break;
                            }

                            JsonNode records = null;
                            if (json.has("records")) {
                                records = json.get("records");
                            } else if (json.has("data")) {
                                records = json.get("data");
                            } else if (json.isArray()) {
                                records = json;
                            }

                            if (records == null || !records.isArray() || records.size() == 0) {
                                logger.info("No records found on page {}", pages);
                                continuePaging = false;
                                pageFetched = true;
                                break;
                            }

                            for (JsonNode rec : records) {
                                try {
                                    JsonNode fields = rec;
                                    if (rec.has("record") && rec.get("record").has("fields")) {
                                        fields = rec.get("record").get("fields");
                                    } else if (rec.has("fields")) {
                                        fields = rec.get("fields");
                                    }

                                    Laureate laureate = objectMapper.convertValue(fields, Laureate.class);
                                    laureate.setSourceFetchedAt(Instant.now().toString());
                                    laureate.setStatus("RECEIVED");

                                    try {
                                        UUID technicalId = entityService.addItem(
                                            Laureate.ENTITY_NAME,
                                            String.valueOf(Laureate.ENTITY_VERSION),
                                            laureate
                                        ).join();
                                        logger.info("Persisted Laureate id={} technicalId={}", laureate.getId(), technicalId);
                                    } catch (CompletionException ce) {
                                        logger.error("Failed to persist laureate during ingest: {}", ce.getMessage(), ce);
                                        job.setLastError(ce.getMessage());
                                    }

                                    processed++;
                                } catch (Exception e) {
                                    logger.error("Failed to map/persist record: {}", e.getMessage(), e);
                                    job.setLastError(e.getMessage());
                                }
                            }

                            // If fewer records than page size, we are done
                            if (records.size() < pageSize) {
                                continuePaging = false;
                            } else {
                                offset += pageSize;
                            }

                            pageFetched = true;
                        } catch (CompletionException ce) {
                            Throwable cause = ce.getCause();
                            logger.warn("Error fetching page {} attempt {}: {}", pages, attempt, ce.getMessage());
                            job.setLastError(ce.getMessage());
                            boolean is429 = cause != null && cause.getMessage() != null && cause.getMessage().contains("429");
                            if (is429) {
                                // Rate limited — backoff and retry
                                int backoff = initialBackoffSeconds * (exponential ? (1 << (attempt - 1)) : attempt);
                                try {
                                    logger.info("Rate limited, backing off for {} seconds (attempt {})", backoff, attempt);
                                    Thread.sleep(backoff * 1000L);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                // retry
                            } else {
                                // Non-rate-limit error: break after retries
                                if (attempt >= defaultRetries) {
                                    logger.error("Failed to fetch page {} after {} attempts", pages, attempt);
                                    continuePaging = false;
                                    break;
                                }
                                try {
                                    Thread.sleep(1000L * attempt);
                                } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            }
                        } catch (Exception e) {
                            logger.error("Unexpected error during HTTP ingestion: {}", e.getMessage(), e);
                            job.setLastError(e.getMessage());
                            continuePaging = false;
                            break;
                        }
                    }
                }
            }

            job.setProcessedRecordsCount(processed);
            // Do not set terminal status here; downstream processors/criteria determine SUCCEEDED/FAILED
            logger.info("Ingest completed for job id={} processedRecords={}", job.getId(), processed);
        } catch (Exception e) {
            logger.error("Unexpected error in IngestProcessor: {}", e.getMessage(), e);
            job.setLastError(e.getMessage());
            job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
            // leave status as INGESTING or set to FAILED depending on retry policy — orchestration handles retries
            job.setStatus("INGESTING");
        }
        return job;
    }

    // Simple wrapper to avoid importing Jackson ObjectNode class at top-level repeatedly
    private static class ObjectNodeWrapper {
        private final com.fasterxml.jackson.databind.node.ObjectNode node;
        ObjectNodeWrapper(com.fasterxml.jackson.databind.node.ObjectNode node) { this.node = node; }
        int getStatus() { return node.has("status") ? node.get("status").asInt() : 200; }
        com.fasterxml.jackson.databind.JsonNode getJson() { return node.has("json") ? node.get("json") : null; }
    }
}
