package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

    @Autowired
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

        // Initialize job ingestion state
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        job.setState("INGESTING");
        job.setStartedAt(now);
        job.setFinishedAt(null);
        job.setRecordsFetchedCount(0);
        job.setRecordsProcessedCount(0);
        job.setRecordsFailedCount(0);
        job.setErrorSummary(null);

        HttpClient httpClient = HttpClient.newHttpClient();
        // fetch a reasonable number of records; can be adjusted
        String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=100";

        List<Laureate> laureatesToPersist = new ArrayList<>();
        int fetchedCount = 0;
        int mappingFailures = 0;
        StringBuilder errors = new StringBuilder();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status != 200) {
                String err = "Failed to fetch records, HTTP status: " + status;
                logger.error(err);
                job.setState("FAILED");
                job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                job.setErrorSummary(err);
                return job;
            }

            String body = response.body();
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode recordsNode = root.path("records");
            if (!recordsNode.isArray()) {
                // try fallback to 'records' being top-level or single object
                logger.warn("Unexpected JSON structure: 'records' is not an array");
                job.setState("FAILED");
                job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                job.setErrorSummary("Unexpected API response structure");
                return job;
            }

            fetchedCount = recordsNode.size();

            for (com.fasterxml.jackson.databind.JsonNode rec : recordsNode) {
                try {
                    com.fasterxml.jackson.databind.JsonNode fields = rec.has("fields") ? rec.get("fields") : rec;

                    Laureate l = new Laureate();

                    // id (expected integer)
                    if (fields.has("id") && !fields.get("id").isNull()) {
                        try {
                            l.setId(fields.get("id").asInt());
                        } catch (Exception e) {
                            // if id cannot be parsed, try other keys or treat as mapping failure
                            logger.debug("Unable to parse id for record: {}", e.getMessage());
                        }
                    } else if (fields.has("laureateid") && !fields.get("laureateid").isNull()) {
                        // sometimes datasets use different key names
                        try {
                            l.setId(fields.get("laureateid").asInt());
                        } catch (Exception ignored) {}
                    }

                    // Basic personal details
                    l.setFirstname(getTextOrNull(fields, "firstname", "firstname_en", "firstname_en"));
                    l.setSurname(getTextOrNull(fields, "surname", "surname_en", "familyname"));

                    // Dates and origin
                    l.setBorn(getTextOrNull(fields, "born"));
                    l.setDied(getTextOrNull(fields, "died"));
                    l.setBornCountry(getTextOrNull(fields, "borncountry", "born_country"));
                    l.setBornCountryCode(getTextOrNull(fields, "borncountrycode", "born_country_code", "borncountrycode"));
                    l.setBornCity(getTextOrNull(fields, "borncity", "born_city"));

                    // Award info
                    l.setYear(getTextOrNull(fields, "year"));
                    l.setCategory(getTextOrNull(fields, "category"));
                    l.setMotivation(getTextOrNull(fields, "motivation"));

                    // Affiliation info - dataset example uses name/city/country for affiliation
                    l.setAffiliationName(getTextOrNull(fields, "name", "affiliation_name", "org_name"));
                    l.setAffiliationCity(getTextOrNull(fields, "city", "affiliation_city"));
                    l.setAffiliationCountry(getTextOrNull(fields, "country", "affiliation_country"));

                    l.setGender(getTextOrNull(fields, "gender"));

                    // initial enrichment/status fields
                    l.setValidationStatus("PENDING");
                    l.setLastSeenAt(now);

                    // Basic sanity: require id, firstname and surname per entity validation; if absent treat as mapping failure
                    if (l.getId() == null || l.getFirstname() == null || l.getSurname() == null) {
                        mappingFailures++;
                        String msg = "Missing required fields (id/firstname/surname) for record index; skipping";
                        errors.append(msg).append("; ");
                        logger.warn(msg);
                        continue;
                    }

                    laureatesToPersist.add(l);
                } catch (Exception ex) {
                    mappingFailures++;
                    String msg = "Error mapping record: " + ex.getMessage();
                    errors.append(msg).append("; ");
                    logger.error(msg, ex);
                }
            }

            // Persist laureates (if any)
            int processed = 0;
            if (!laureatesToPersist.isEmpty()) {
                try {
                    CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                        Laureate.ENTITY_NAME,
                        Laureate.ENTITY_VERSION,
                        laureatesToPersist
                    );
                    List<UUID> ids = idsFuture.get();
                    if (ids != null) processed = ids.size();
                } catch (Exception e) {
                    // treat as fatal error for ingestion
                    String err = "Failed to persist laureates: " + e.getMessage();
                    logger.error(err, e);
                    job.setState("FAILED");
                    job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                    job.setErrorSummary(err);
                    // set counts before returning
                    job.setRecordsFetchedCount(fetchedCount);
                    job.setRecordsProcessedCount(0);
                    job.setRecordsFailedCount(mappingFailures + laureatesToPersist.size());
                    return job;
                }
            }

            // finalize job state: if fatal not hit then SUCCEEDED
            job.setState("SUCCEEDED");
            job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            job.setRecordsFetchedCount(fetchedCount);
            job.setRecordsProcessedCount(processed);
            job.setRecordsFailedCount(mappingFailures + (laureatesToPersist.size() - processed));

            if (errors.length() > 0) {
                job.setErrorSummary(errors.toString());
            } else {
                job.setErrorSummary(null);
            }

            return job;

        } catch (Exception e) {
            logger.error("Unexpected error during ingestion", e);
            job.setState("FAILED");
            job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            String err = "Unexpected ingestion error: " + e.getMessage();
            job.setErrorSummary(err);
            job.setRecordsFetchedCount(fetchedCount);
            job.setRecordsProcessedCount(0);
            job.setRecordsFailedCount(fetchedCount); // assume all failed
            return job;
        }
    }

    // Helper to pick first present text field from candidates or null
    private String getTextOrNull(com.fasterxml.jackson.databind.JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }
}