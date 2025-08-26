package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class JobExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JobExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Business logic:
        // 1. Fetch data from job.sourceEndpoint (expected JSON array of records)
        // 2. For each record map to Laureate entity and persist via entityService.addItem
        // 3. Update job.resultSummary, lastRunAt, status accordingly
        String source = job.getSourceEndpoint();
        Map<String, Object> params = job.getParameters();

        int processed = 0;
        int created = 0;
        int failed = 0;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        if (source == null || source.isBlank()) {
            logger.error("Job {} has empty sourceEndpoint", job.getJobId());
            job.setStatus("FAILED");
            Integer rc = job.getRetryCount();
            job.setRetryCount(rc == null ? 1 : rc + 1);
            job.setLastRunAt(Instant.now().toString());
            job.setResultSummary("no source endpoint");
            return job;
        }

        try {
            // Build URL with simple query params if provided (parameters are optional)
            String url = source;
            if (params != null && !params.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                boolean first = !url.contains("?");
                if (params.containsKey("query")) {
                    sb.append(first ? "?" : "&").append("q=").append(params.get("query"));
                    first = false;
                }
                if (params.containsKey("limit")) {
                    sb.append(first ? "?" : "&").append("limit=").append(params.get("limit"));
                }
                url = sb.toString();
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                logger.error("Failed to fetch source for job {}: HTTP {}", job.getJobId(), statusCode);
                job.setStatus("FAILED");
                Integer rc = job.getRetryCount();
                job.setRetryCount(rc == null ? 1 : rc + 1);
                job.setLastRunAt(Instant.now().toString());
                job.setResultSummary("fetch failed: HTTP " + statusCode);
                return job;
            }

            String body = httpResponse.body();
            JsonNode root = objectMapper.readTree(body);

            // Support both array root and object with "records" field
            JsonNode recordsNode = root.isArray() ? root : root.path("records");
            if (recordsNode == null || !recordsNode.isArray()) {
                logger.warn("Source returned no array records for job {}.", job.getJobId());
                job.setStatus("COMPLETED");
                job.setLastRunAt(Instant.now().toString());
                job.setResultSummary("no records");
                return job;
            }

            for (JsonNode record : recordsNode) {
                processed++;
                try {
                    Laureate laureate = mapRecordToLaureate(record);
                    // Persist laureate as a new entity to trigger Laureate workflow
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureate
                    );
                    futures.add(idFuture);
                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to map/persist record for job {}: {}", job.getJobId(), e.getMessage(), e);
                }
            }

            // Await addItem completions and count successes/failures
            for (CompletableFuture<UUID> f : futures) {
                try {
                    UUID id = f.join();
                    if (id != null) created++;
                    else failed++;
                } catch (Exception e) {
                    failed++;
                    logger.error("Error while waiting for Laureate addItem completion: {}", e.getMessage(), e);
                }
            }

            // Compose result summary
            String summary = String.format("processed %d, created %d, failed %d", processed, created, failed);
            job.setResultSummary(summary);
            job.setLastRunAt(Instant.now().toString());
            job.setStatus(failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");

            // reset retry count on success path
            job.setRetryCount(0);

        } catch (Exception ex) {
            logger.error("Exception executing job {}: {}", job.getJobId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            Integer rc = job.getRetryCount();
            job.setRetryCount(rc == null ? 1 : rc + 1);
            job.setLastRunAt(Instant.now().toString());
            job.setResultSummary("error: " + ex.getMessage());
        }

        return job;
    }

    private Laureate mapRecordToLaureate(JsonNode record) {
        Laureate l = new Laureate();

        String laureateId = textOrNull(record.path("laureateId"));
        if (laureateId == null) laureateId = textOrNull(record.path("id"));
        l.setLaureateId(laureateId);

        l.setFullName(textOrNull(record.path("fullName")));
        if (l.getFullName() == null) l.setFullName(textOrNull(record.path("name")));

        if (record.has("prizeYear") && record.get("prizeYear").canConvertToInt()) {
            l.setPrizeYear(record.get("prizeYear").asInt());
        } else if (record.has("year") && record.get("year").canConvertToInt()) {
            l.setPrizeYear(record.get("year").asInt());
        }

        l.setCategory(textOrNull(record.path("category")));
        l.setCountry(textOrNull(record.path("country")));

        // affiliations array
        if (record.has("affiliations") && record.get("affiliations").isArray()) {
            List<String> aff = new ArrayList<>();
            for (JsonNode a : record.get("affiliations")) {
                if (!a.isNull()) {
                    String val = textOrNull(a);
                    if (val != null) aff.add(val);
                }
            }
            l.setAffiliations(aff);
        }

        String changeType = textOrNull(record.path("changeType"));
        l.setChangeType(changeType != null ? changeType : "new");
        l.setRawPayload(record.toString());
        l.setDetectedAt(Instant.now().toString());
        l.setPublished(Boolean.FALSE);

        return l;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText();
        return (t == null || t.isBlank()) ? null : t;
    }
}