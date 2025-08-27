package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
        if (job == null) return null;

        // Set job to INGESTING and record startedAt
        try {
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.warn("Unable to set job started state: {}", e.getMessage(), e);
        }

        List<String> errors = new ArrayList<>();
        List<Laureate> laureatesToAdd = new ArrayList<>();

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            errors.add("sourceUrl missing");
            logger.error("Job sourceUrl is missing for jobId={}", job.getJobId());
        } else {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    String body = response.body();
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode records = root.path("records");
                    if (records.isMissingNode() || !records.isArray()) {
                        // try alternative: some APIs may return array at root
                        if (root.isArray()) {
                            records = root;
                        }
                    }

                    if (records != null && records.isArray()) {
                        for (JsonNode rec : records) {
                            try {
                                JsonNode fields = rec.has("fields") ? rec.path("fields") : rec;
                                Laureate l = new Laureate();

                                // id - try several possible field names
                                if (fields.has("id")) {
                                    JsonNode idNode = fields.get("id");
                                    if (idNode.isInt() || idNode.isLong()) {
                                        l.setId(idNode.asInt());
                                    } else if (idNode.isTextual()) {
                                        try {
                                            l.setId(Integer.parseInt(idNode.asText()));
                                        } catch (NumberFormatException nfe) {
                                            // leave null; validation will catch
                                        }
                                    }
                                } else if (fields.has("laureate_id")) {
                                    JsonNode idNode = fields.get("laureate_id");
                                    if (idNode.isInt() || idNode.isLong()) {
                                        l.setId(idNode.asInt());
                                    } else if (idNode.isTextual()) {
                                        try {
                                            l.setId(Integer.parseInt(idNode.asText()));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }

                                l.setFirstname(textOrNull(fields, "firstname", "firstname"));
                                l.setSurname(textOrNull(fields, "surname", "surname"));
                                l.setCategory(textOrNull(fields, "category", "prize_category", "category"));
                                l.setYear(textOrNull(fields, "year", "awardYear", "year"));
                                l.setMotivation(textOrNull(fields, "motivation", "motivation"));
                                l.setGender(textOrNull(fields, "gender", "sex", "gender"));
                                l.setBorn(textOrNull(fields, "born", "birthdate", "born"));
                                l.setDied(textOrNull(fields, "died", "deathdate", "died"));
                                l.setBorncity(textOrNull(fields, "borncity", "city", "borncity"));
                                l.setBorncountry(textOrNull(fields, "borncountry", "country", "borncountry"));
                                l.setBorncountrycode(textOrNull(fields, "borncountrycode", "country_code", "borncountrycode"));
                                // affiliation mapping
                                l.setAffiliationName(textOrNull(fields, "affiliationName", "name", "affiliation"));
                                l.setAffiliationCity(textOrNull(fields, "affiliationCity", "city"));
                                l.setAffiliationCountry(textOrNull(fields, "affiliationCountry", "country"));

                                // Enrichment: compute age at award if possible
                                Integer enrichedAge = null;
                                try {
                                    if (l.getBorn() != null && l.getYear() != null) {
                                        String bornStr = l.getBorn();
                                        // expecting yyyy-MM-dd or yyyy
                                        Integer birthYear = tryParseYear(bornStr);
                                        Integer awardYear = tryParseYear(l.getYear());
                                        if (birthYear != null && awardYear != null) {
                                            enrichedAge = awardYear - birthYear;
                                        }
                                    }
                                } catch (Exception ex) {
                                    // ignore enrichment error for this record but log
                                    logger.debug("Enrichment failed for laureate candidate: {}", ex.getMessage());
                                }
                                l.setEnrichedAgeAtAward(enrichedAge);

                                // Normalize country code
                                if (l.getBorncountrycode() != null) {
                                    l.setNormalizedCountryCode(l.getBorncountrycode().toUpperCase());
                                } else if (l.getBorncountry() != null && l.getBorncountry().length() == 2) {
                                    l.setNormalizedCountryCode(l.getBorncountry().toUpperCase());
                                } else {
                                    l.setNormalizedCountryCode(null);
                                }

                                // Validation status and errors
                                List<String> vErrors = new ArrayList<>();
                                if (l.getId() == null) vErrors.add("id missing or invalid");
                                if (l.getFirstname() == null || l.getFirstname().isBlank()) vErrors.add("firstname missing");
                                if (l.getSurname() == null || l.getSurname().isBlank()) vErrors.add("surname missing");
                                if (l.getCategory() == null || l.getCategory().isBlank()) vErrors.add("category missing");
                                if (l.getYear() == null || l.getYear().isBlank()) vErrors.add("year missing");
                                if (vErrors.isEmpty()) {
                                    l.setValidationStatus("OK");
                                    l.setValidationErrors(null);
                                } else {
                                    l.setValidationStatus("INVALID");
                                    l.setValidationErrors(vErrors);
                                }

                                // Only add valid or partially valid laureates to ingestion list (business decision):
                                laureatesToAdd.add(l);
                            } catch (Exception e) {
                                String msg = "Failed to map record to Laureate: " + e.getMessage();
                                errors.add(msg);
                                logger.warn(msg, e);
                            }
                        }
                    } else {
                        errors.add("No records array found in response");
                        logger.warn("No records in response for jobId={}", job.getJobId());
                    }
                } else {
                    String msg = "HTTP request failed with status " + status;
                    errors.add(msg);
                    logger.error(msg);
                }
            } catch (IOException | InterruptedException e) {
                String msg = "HTTP call failed: " + e.getMessage();
                errors.add(msg);
                logger.error(msg, e);
            } catch (Exception e) {
                String msg = "Unexpected error during ingestion: " + e.getMessage();
                errors.add(msg);
                logger.error(msg, e);
            }
        }

        // Try to persist laureates (addItems). We must not update the triggering Job via entityService.
        List<UUID> addedIds = null;
        try {
            if (!laureatesToAdd.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    laureatesToAdd
                );
                addedIds = idsFuture.get();
            } else {
                addedIds = new ArrayList<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            String msg = "Failed to add laureates: " + e.getMessage();
            errors.add(msg);
            logger.error(msg, e);
        } catch (Exception e) {
            String msg = "Unexpected error while adding laureates: " + e.getMessage();
            errors.add(msg);
            logger.error(msg, e);
        }

        // Build ingestResult
        Job.IngestResult ingestResult = new Job.IngestResult();
        int countAdded = addedIds != null ? addedIds.size() : 0;
        ingestResult.setCountAdded(countAdded);
        ingestResult.setCountUpdated(0); // no updates performed in this processor
        ingestResult.setErrors(errors.isEmpty() ? null : errors);
        job.setIngestResult(ingestResult);

        // finalize job status and timestamps
        job.setFinishedAt(Instant.now().toString());
        if (errors.isEmpty()) {
            job.setStatus("SUCCEEDED");
        } else {
            job.setStatus("FAILED");
        }

        logger.info("Job {} ingestion finished. added={}, errors={}", job.getJobId(), countAdded, errors.size());
        return job;
    }

    // Helper methods
    private static String textOrNull(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private static Integer tryParseYear(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        // common formats: "1930-09-12" or "1930"
        if (trimmed.length() >= 4) {
            String yearPart = trimmed.substring(0, 4);
            try {
                return Integer.parseInt(yearPart);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}