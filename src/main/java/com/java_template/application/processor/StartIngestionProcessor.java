package com.java_template.application.processor;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final ObjectMapper objectMapper;

    public StartIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        logger.info("Start ingestion logic for job id: {}", job.getId());

        // Prepare error details list
        if (job.getErrorDetails() == null) {
            job.setErrorDetails(new ArrayList<>());
        }

        // Validate source reachability
        String sourceUrl = job.getSource();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request;
        try {
            // try to use provided source as-is; prefer GET so we can retrieve data
            URI uri = URI.create(sourceUrl);
            request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
        } catch (Exception e) {
            logger.error("Invalid source URL for job {}: {}", job.getId(), e.getMessage());
            job.getErrorDetails().add("Invalid source URL: " + e.getMessage());
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().toString());
            // update result summary if present
            Job.ResultSummary rs = job.getResultSummary();
            if (rs == null) {
                rs = new Job.ResultSummary();
            }
            rs.setIngestedCount(0);
            rs.setUpdatedCount(0);
            rs.setErrorCount(1);
            job.setResultSummary(rs);
            return job;
        }

        // Try fetching a small sample to verify availability and to ingest data
        HttpResponse<String> httpResponse;
        try {
            httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            logger.error("Source unreachable for job {}: {}", job.getId(), e.getMessage());
            job.getErrorDetails().add("source unreachable: " + e.getMessage());
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().toString());
            Job.ResultSummary rs = job.getResultSummary();
            if (rs == null) {
                rs = new Job.ResultSummary();
            }
            rs.setIngestedCount(0);
            rs.setUpdatedCount(0);
            rs.setErrorCount(1);
            job.setResultSummary(rs);
            return job;
        }

        int statusCode = httpResponse.statusCode();
        if (statusCode >= 400) {
            logger.error("Source returned error {} for job {}", statusCode, job.getId());
            job.getErrorDetails().add("source returned HTTP " + statusCode);
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().toString());
            Job.ResultSummary rs = job.getResultSummary();
            if (rs == null) {
                rs = new Job.ResultSummary();
            }
            rs.setIngestedCount(0);
            rs.setUpdatedCount(0);
            rs.setErrorCount(1);
            job.setResultSummary(rs);
            return job;
        }

        // Mark job as ingesting and set startedAt
        job.setStatus("INGESTING");
        job.setStartedAt(Instant.now().toString());
        job.setNotificationsSent(false);

        // Prepare counters
        int ingestedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        // Parse response body to extract records
        String body = httpResponse.body();
        try {
            JsonNode root = objectMapper.readTree(body);
            ArrayNode recordsNode = null;

            if (root.has("records") && root.get("records").isArray()) {
                recordsNode = (ArrayNode) root.get("records");
            } else if (root.isArray()) {
                recordsNode = (ArrayNode) root;
            } else if (root.has("data") && root.get("data").isArray()) {
                recordsNode = (ArrayNode) root.get("data");
            }

            // If no records array found, attempt to treat the root as single record
            if (recordsNode == null) {
                // try single record mapping
                JsonNode single = root;
                Laureate laureate = mapJsonToLaureate(single, job.getId());
                if (laureate != null) {
                    try {
                        CompletableFuture<UUID> addF = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                Laureate.ENTITY_VERSION,
                                laureate
                        );
                        futures.add(addF);
                    } catch (Exception ex) {
                        logger.error("Failed to add laureate for job {}: {}", job.getId(), ex.getMessage());
                        job.getErrorDetails().add("failed to add laureate: " + ex.getMessage());
                        errorCount++;
                    }
                } else {
                    logger.warn("No laureate data mapped from response for job {}", job.getId());
                }
            } else {
                for (JsonNode rec : recordsNode) {
                    // attempt to find fields node in common structures
                    JsonNode fields = rec.has("record") && rec.get("record").has("fields") ? rec.get("record").get("fields")
                            : rec.has("fields") ? rec.get("fields")
                            : rec;

                    Laureate laureate = mapJsonToLaureate(fields, job.getId());
                    if (laureate == null) {
                        // skip but record an error
                        logger.warn("Skipping record - unable to map laureate fields for job {}", job.getId());
                        job.getErrorDetails().add("skipped a record - mapping failed");
                        errorCount++;
                        continue;
                    }

                    try {
                        CompletableFuture<UUID> addF = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                Laureate.ENTITY_VERSION,
                                laureate
                        );
                        futures.add(addF);
                    } catch (Exception ex) {
                        logger.error("Failed to add laureate for job {}: {}", job.getId(), ex.getMessage());
                        job.getErrorDetails().add("failed to add laureate: " + ex.getMessage());
                        errorCount++;
                    }
                }
            }

            // Wait for all add operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Count successes
            for (CompletableFuture<UUID> f : futures) {
                try {
                    UUID created = f.join();
                    if (created != null) ingestedCount++;
                } catch (Exception ex) {
                    logger.error("Add laureate future failed: {}", ex.getMessage());
                    errorCount++;
                }
            }

        } catch (Exception e) {
            logger.error("Failed parsing or ingesting records for job {}: {}", job.getId(), e.getMessage());
            job.getErrorDetails().add("parsing/ingestion error: " + e.getMessage());
            job.setStatus("FAILED");
            job.setFinishedAt(Instant.now().toString());
            Job.ResultSummary rs = job.getResultSummary();
            if (rs == null) {
                rs = new Job.ResultSummary();
            }
            rs.setIngestedCount(ingestedCount);
            rs.setUpdatedCount(updatedCount);
            rs.setErrorCount(errorCount + 1);
            job.setResultSummary(rs);
            return job;
        }

        // Finalize result summary and status
        Job.ResultSummary rs = job.getResultSummary();
        if (rs == null) {
            rs = new Job.ResultSummary();
        }
        rs.setIngestedCount(ingestedCount);
        rs.setUpdatedCount(updatedCount);
        rs.setErrorCount(errorCount);
        job.setResultSummary(rs);

        job.setFinishedAt(Instant.now().toString());
        if (errorCount > 0) {
            job.setStatus("FAILED");
        } else {
            job.setStatus("SUCCEEDED");
        }

        logger.info("Ingestion finished for job {}: ingested={}, errors={}", job.getId(), ingestedCount, errorCount);
        return job;
    }

    // Helper to map JSON node to Laureate entity
    private Laureate mapJsonToLaureate(JsonNode node, String ingestionJobId) {
        if (node == null || node.isNull() || node.isEmpty()) return null;

        Laureate l = new Laureate();
        // Map common field name variants
        l.setLaureateId(firstNonEmptyText(node, "id", "laureateId", "recordid"));
        l.setFirstname(firstNonEmptyText(node, "firstname", "first_name", "givenName"));
        l.setSurname(firstNonEmptyText(node, "surname", "family_name", "lastName"));
        l.setBorn(firstNonEmptyText(node, "born", "birth_date", "date_of_birth"));
        l.setDied(firstNonEmptyText(node, "died", "death_date"));
        l.setBorncountry(firstNonEmptyText(node, "borncountry", "birth_country", "country"));
        l.setBorncountrycode(firstNonEmptyText(node, "borncountrycode", "birth_country_code", "country_code"));
        l.setBorncity(firstNonEmptyText(node, "borncity", "birth_place", "city"));
        l.setAwardYear(firstNonEmptyText(node, "year", "awardYear", "award_year"));
        l.setCategory(firstNonEmptyText(node, "category", "field", "discipline"));
        l.setMotivation(firstNonEmptyText(node, "motivation", "motivationText", "motivation_text"));
        l.setAffiliationName(firstNonEmptyText(node, "name", "affiliationName", "affiliation_name"));
        l.setAffiliationCity(firstNonEmptyText(node, "city", "affiliationCity", "affiliation_city"));
        l.setAffiliationCountry(firstNonEmptyText(node, "country", "affiliationCountry", "affiliation_country"));
        l.setGender(firstNonEmptyText(node, "gender"));

        // Ensure essential minimal fields exist (laureateId and awardYear and category required by Laureate.isValid)
        if (l.getLaureateId() == null || l.getLaureateId().isBlank()) return null;
        if (l.getAwardYear() == null || l.getAwardYear().isBlank()) return null;
        if (l.getCategory() == null || l.getCategory().isBlank()) return null;

        // Set provenance
        Laureate.Provenance prov = new Laureate.Provenance();
        prov.setIngestionJobId(ingestionJobId);
        prov.setSourceRecordId(firstNonEmptyText(node, "id", "recordid", "laureateId"));
        prov.setSourceTimestamp(Instant.now().toString());
        l.setProvenance(prov);

        // Set initial processing status for downstream processors to pick up
        l.setProcessingStatus("PERSISTED");

        // validationErrors default empty list already initialized in entity

        return l;
    }

    private String firstNonEmptyText(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k)) {
                String txt = node.get(k).isNull() ? null : node.get(k).asText(null);
                if (txt != null && !txt.isBlank()) return txt;
            }
        }
        return null;
    }
}