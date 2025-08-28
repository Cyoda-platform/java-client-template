package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
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
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public StartIngestionProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
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
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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
        // mark as INGESTING and set run timestamp
        try {
            logger.info("Start ingestion for job id={}, sourceUrl={}", job.getId(), job.getSourceUrl());
            job.setState("INGESTING");
            job.setRunTimestamp(Instant.now().toString());

            List<String> errors = new ArrayList<>();
            int ingestedCount = 0;
            int failedCount = 0;

            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                String err = "Missing sourceUrl in job";
                logger.error(err);
                errors.add(err);
                failedCount = 0;
                job.setSummary(buildSummary(ingestedCount, failedCount, errors));
                job.setState("FAILED");
                job.setCompletedTimestamp(Instant.now().toString());
                return job;
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String err = "Failed to fetch sourceUrl, status=" + response.statusCode();
                logger.error(err);
                errors.add(err);
                job.setSummary(buildSummary(ingestedCount, failedCount, errors));
                job.setState("FAILED");
                job.setCompletedTimestamp(Instant.now().toString());
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            List<Laureate> laureatesToPersist = new ArrayList<>();

            // Expecting array under "records" or direct array. Be resilient to multiple shapes.
            JsonNode recordsNode = null;
            if (root.has("records") && root.get("records").isArray()) {
                recordsNode = root.get("records");
            } else if (root.isArray()) {
                recordsNode = root;
            }

            if (recordsNode != null && recordsNode.isArray()) {
                for (JsonNode item : recordsNode) {
                    JsonNode fieldsNode = null;
                    if (item.has("fields")) {
                        fieldsNode = item.get("fields");
                    } else if (item.has("record") && item.get("record").has("fields")) {
                        fieldsNode = item.get("record").get("fields");
                    } else {
                        // maybe item itself is the fields
                        fieldsNode = item;
                    }

                    try {
                        Laureate la = mapFieldsToLaureate(fieldsNode);
                        if (la != null && la.isValid()) {
                            laureatesToPersist.add(la);
                        } else {
                            failedCount++;
                            String msg = "Invalid laureate payload skipped: " + (fieldsNode != null ? fieldsNode.toString() : "null");
                            logger.warn(msg);
                            errors.add(msg);
                        }
                    } catch (Exception ex) {
                        failedCount++;
                        String msg = "Exception mapping laureate: " + ex.getMessage();
                        logger.error(msg, ex);
                        errors.add(msg);
                    }
                }
            } else {
                // try to interpret root as single record with fields
                JsonNode fieldsNode = root.has("fields") ? root.get("fields") : root;
                try {
                    Laureate la = mapFieldsToLaureate(fieldsNode);
                    if (la != null && la.isValid()) {
                        laureatesToPersist.add(la);
                    } else {
                        failedCount++;
                        String msg = "Invalid laureate payload in response";
                        logger.warn(msg);
                        errors.add(msg);
                    }
                } catch (Exception ex) {
                    failedCount++;
                    String msg = "Exception mapping laureate: " + ex.getMessage();
                    logger.error(msg, ex);
                    errors.add(msg);
                }
            }

            // Persist laureates (other entity) using entityService
            if (!laureatesToPersist.isEmpty()) {
                try {
                    CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                        Laureate.ENTITY_NAME,
                        Laureate.ENTITY_VERSION,
                        laureatesToPersist
                    );
                    List<java.util.UUID> persistedIds = idsFuture.get();
                    ingestedCount = persistedIds != null ? persistedIds.size() : laureatesToPersist.size();
                    logger.info("Persisted {} laureates for job {}", ingestedCount, job.getId());
                } catch (Exception ex) {
                    String msg = "Failed to persist laureates: " + ex.getMessage();
                    logger.error(msg, ex);
                    errors.add(msg);
                    failedCount += laureatesToPersist.size();
                }
            }

            // finalize job summary and state
            job.setSummary(buildSummary(ingestedCount, failedCount, errors));
            job.setCompletedTimestamp(Instant.now().toString());
            if (errors.isEmpty()) {
                job.setState("SUCCEEDED");
            } else {
                job.setState("FAILED");
            }

        } catch (IOException | InterruptedException ex) {
            logger.error("Ingestion failed for job id={}: {}", job.getId(), ex.getMessage(), ex);
            List<String> errors = new ArrayList<>();
            errors.add("Ingestion exception: " + ex.getMessage());
            job.setSummary(buildSummary(0, 0, errors));
            job.setState("FAILED");
            job.setCompletedTimestamp(Instant.now().toString());
        } catch (Exception ex) {
            logger.error("Unexpected error during ingestion for job id={}: {}", job.getId(), ex.getMessage(), ex);
            List<String> errors = new ArrayList<>();
            errors.add("Unexpected error: " + ex.getMessage());
            job.setSummary(buildSummary(0, 0, errors));
            job.setState("FAILED");
            job.setCompletedTimestamp(Instant.now().toString());
        }

        return job;
    }

    private Job.Summary buildSummary(int ingestedCount, int failedCount, List<String> errors) {
        Job.Summary summary = new Job.Summary();
        summary.setIngestedCount(ingestedCount);
        summary.setFailedCount(failedCount);
        summary.setErrors(errors != null ? errors : new ArrayList<>());
        return summary;
    }

    private Laureate mapFieldsToLaureate(JsonNode fields) {
        if (fields == null || fields.isNull()) return null;
        Laureate la = new Laureate();

        // id: may be numeric or string - try to parse int
        if (fields.has("id") && !fields.get("id").isNull()) {
            JsonNode idNode = fields.get("id");
            if (idNode.isInt() || idNode.isLong()) {
                la.setId(Integer.valueOf(idNode.asInt()));
            } else if (idNode.isTextual()) {
                try {
                    la.setId(Integer.valueOf(idNode.asText()));
                } catch (NumberFormatException ignored) {
                    la.setId(null);
                }
            }
        }

        if (fields.has("firstname")) la.setFirstname(textOrNull(fields.get("firstname")));
        if (fields.has("firstname_")) la.setFirstname(textOrNull(fields.get("firstname_"))); // fallback
        if (fields.has("surname")) la.setSurname(textOrNull(fields.get("surname")));
        if (fields.has("gender")) la.setGender(textOrNull(fields.get("gender")));
        if (fields.has("motivation")) la.setMotivation(textOrNull(fields.get("motivation")));
        if (fields.has("category")) la.setCategory(textOrNull(fields.get("category")));
        if (fields.has("year")) la.setYear(textOrNull(fields.get("year")));
        if (fields.has("born")) la.setBorn(textOrNull(fields.get("born")));
        if (fields.has("died")) la.setDied(textOrNull(fields.get("died")));
        if (fields.has("borncity")) la.setBorncity(textOrNull(fields.get("borncity")));
        if (fields.has("borncountry")) la.setBorncountry(textOrNull(fields.get("borncountry")));
        if (fields.has("borncountrycode")) la.setBorncountrycode(textOrNull(fields.get("borncountrycode")));
        if (fields.has("affiliation_name")) la.setAffiliation_name(textOrNull(fields.get("affiliation_name")));
        if (fields.has("affiliation_city")) la.setAffiliation_city(textOrNull(fields.get("affiliation_city")));
        if (fields.has("affiliation_country")) la.setAffiliation_country(textOrNull(fields.get("affiliation_country")));
        if (fields.has("normalizedCountryCode")) la.setNormalizedCountryCode(textOrNull(fields.get("normalizedCountryCode")));
        // derived_ageAtAward is computed later by EnrichmentProcessor; leave null

        return la;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String txt = node.asText();
        return txt != null && !txt.isBlank() ? txt : null;
    }
}