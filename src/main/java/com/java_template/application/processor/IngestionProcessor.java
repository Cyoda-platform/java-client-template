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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class IngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        // Set ingest start time
        try {
            job.setStartedAt(Instant.now().toString());
            job.setState("INGESTING");
        } catch (Exception e) {
            logger.warn("Failed to set start metadata", e);
        }

        String sourceUrl = job.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            job.setErrorSummary("Missing sourceUrl");
            job.setState("FAILED");
            job.setFinishedAt(Instant.now().toString());
            job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1);
            return job;
        }

        List<Laureate> laureatesToPersist = new ArrayList<>();
        int processed = 0;
        int failed = 0;

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                JsonNode root = objectMapper.readTree(body);

                // OpenDataSoft responses frequently contain 'records' array where each record has 'record' or 'fields'.
                JsonNode recordsNode = root.has("records") ? root.get("records") : root;

                if (recordsNode != null && recordsNode.isArray()) {
                    for (JsonNode rec : recordsNode) {
                        try {
                            JsonNode fieldsNode = null;
                            if (rec.has("record") && rec.get("record").has("fields")) {
                                fieldsNode = rec.get("record").get("fields");
                            } else if (rec.has("fields")) {
                                fieldsNode = rec.get("fields");
                            } else if (rec.has("record")) {
                                // some variations: record itself may be fields
                                fieldsNode = rec.get("record");
                            } else {
                                // fallback: treat rec as fields
                                fieldsNode = rec;
                            }

                            if (fieldsNode == null || fieldsNode.isNull()) {
                                failed++;
                                logger.debug("Skipping record due to missing fields node");
                                continue;
                            }

                            Laureate laureate = mapFieldsToLaureate(fieldsNode);
                            // set snapshot
                            try {
                                laureate.setSourceSnapshot(objectMapper.writeValueAsString(fieldsNode));
                            } catch (Exception ex) {
                                laureate.setSourceSnapshot("{}");
                            }
                            laureate.setLastUpdatedAt(Instant.now().toString());

                            // Enrichment: ageAtAward
                            try {
                                Integer age = computeAgeAtAward(laureate.getBorn(), laureate.getYear());
                                laureate.setAgeAtAward(age);
                            } catch (Exception ex) {
                                // leave null if unable to compute
                                logger.debug("Unable to compute age for laureate id {}: {}", laureate.getId(), ex.getMessage());
                            }

                            // Normalize country code
                            String code = laureate.getBornCountryCode();
                            if (code != null && !code.isBlank()) {
                                laureate.setNormalizedCountryCode(code.trim().toUpperCase());
                            } else if (laureate.getBornCountry() != null && !laureate.getBornCountry().isBlank()) {
                                String inferred = laureate.getBornCountry().trim();
                                laureate.setNormalizedCountryCode(inferred.length() >= 2 ? inferred.substring(0, 2).toUpperCase() : inferred.toUpperCase());
                            }

                            laureatesToPersist.add(laureate);
                            processed++;
                        } catch (Exception ex) {
                            failed++;
                            logger.warn("Failed to process record: {}", ex.getMessage(), ex);
                        }
                    }
                } else {
                    // If the root is a single element (not array), try parse as single fields object
                    try {
                        JsonNode fieldsNode = root;
                        Laureate laureate = mapFieldsToLaureate(fieldsNode);
                        laureate.setSourceSnapshot(objectMapper.writeValueAsString(fieldsNode));
                        laureate.setLastUpdatedAt(Instant.now().toString());
                        try {
                            Integer age = computeAgeAtAward(laureate.getBorn(), laureate.getYear());
                            laureate.setAgeAtAward(age);
                        } catch (Exception ex) { }
                        String code = laureate.getBornCountryCode();
                        if (code != null && !code.isBlank()) {
                            laureate.setNormalizedCountryCode(code.trim().toUpperCase());
                        } else if (laureate.getBornCountry() != null && !laureate.getBornCountry().isBlank()) {
                            String inferred = laureate.getBornCountry().trim();
                            laureate.setNormalizedCountryCode(inferred.length() >= 2 ? inferred.substring(0, 2).toUpperCase() : inferred.toUpperCase());
                        }
                        laureatesToPersist.add(laureate);
                        processed++;
                    } catch (Exception ex) {
                        failed++;
                        logger.warn("Failed to process single response body: {}", ex.getMessage(), ex);
                    }
                }

                // Persist laureates in batch if any
                if (!laureatesToPersist.isEmpty()) {
                    try {
                        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                            Laureate.ENTITY_NAME,
                            Laureate.ENTITY_VERSION,
                            laureatesToPersist
                        );
                        // block to ensure creation before proceeding
                        idsFuture.get();
                    } catch (Exception ex) {
                        // If persisting fails, count all as failed
                        logger.error("Failed to persist laureates: {}", ex.getMessage(), ex);
                        failed += laureatesToPersist.size();
                        processed -= laureatesToPersist.size();
                    }
                }

                // update job metadata
                job.setProcessedCount((job.getProcessedCount() == null ? 0 : job.getProcessedCount()) + processed);
                job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + failed);
                job.setFinishedAt(Instant.now().toString());
                job.setState(failed == 0 ? "SUCCEEDED" : "FAILED");
                if (failed > 0) {
                    job.setErrorSummary("Some records failed during ingestion. Failed: " + failed);
                } else {
                    job.setErrorSummary(null);
                }

            } else {
                // non-2xx
                job.setFinishedAt(Instant.now().toString());
                job.setState("FAILED");
                job.setErrorSummary("Source returned non-success status: " + response.statusCode());
                job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("HTTP or IO error during ingestion: {}", e.getMessage(), e);
            job.setFinishedAt(Instant.now().toString());
            job.setState("FAILED");
            job.setErrorSummary("Ingestion error: " + e.getMessage());
            job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1);
        } catch (Exception e) {
            logger.error("Unexpected error during ingestion: {}", e.getMessage(), e);
            job.setFinishedAt(Instant.now().toString());
            job.setState("FAILED");
            job.setErrorSummary("Unexpected ingestion error: " + e.getMessage());
            job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1);
        }

        return job;
    }

    private Laureate mapFieldsToLaureate(JsonNode fields) {
        Laureate l = new Laureate();

        // id: try several possible field names and types
        if (fields.has("id") && !fields.get("id").isNull()) {
            JsonNode idNode = fields.get("id");
            if (idNode.isInt() || idNode.isLong()) {
                l.setId(idNode.intValue());
            } else {
                try {
                    l.setId(Integer.valueOf(idNode.asText()));
                } catch (NumberFormatException e) {
                    // leave null if cannot parse
                }
            }
        } else if (fields.has("laureateId") && !fields.get("laureateId").isNull()) {
            try { l.setId(Integer.valueOf(fields.get("laureateId").asText())); } catch (Exception ex) {}
        }

        // simple string mappings
        l.setFirstname(getTextIfPresent(fields, "firstname", "first_name", "firstName"));
        l.setSurname(getTextIfPresent(fields, "surname", "last_name", "lastName", "surname"));
        l.setBorn(getTextIfPresent(fields, "born"));
        l.setDied(getTextIfPresent(fields, "died"));
        l.setBornCountry(getTextIfPresent(fields, "borncountry", "born_country", "bornCountry"));
        l.setBornCountryCode(getTextIfPresent(fields, "borncountrycode", "born_country_code", "bornCountryCode"));
        l.setBornCity(getTextIfPresent(fields, "borncity", "born_city", "bornCity"));
        l.setGender(getTextIfPresent(fields, "gender"));
        l.setYear(getTextIfPresent(fields, "year"));
        l.setCategory(getTextIfPresent(fields, "category"));
        l.setMotivation(getTextIfPresent(fields, "motivation"));
        // affiliation fields may be provided under various names
        l.setAffiliationName(getTextIfPresent(fields, "name", "affiliationName", "affiliation_name", "org"));
        l.setAffiliationCity(getTextIfPresent(fields, "city", "affiliationCity", "affiliation_city"));
        l.setAffiliationCountry(getTextIfPresent(fields, "country", "affiliationCountry", "affiliation_country"));

        return l;
    }

    private String getTextIfPresent(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String v = node.get(key).asText();
                if (v != null) return v;
            }
        }
        return null;
    }

    private Integer computeAgeAtAward(String born, String year) {
        if (born == null || born.isBlank() || year == null || year.isBlank()) return null;
        try {
            LocalDate bornDate = LocalDate.parse(born);
            int awardYear = Integer.parseInt(year.trim());
            int age = awardYear - bornDate.getYear();
            return age;
        } catch (DateTimeParseException | NumberFormatException e) {
            // fallback: try to extract year from born string if possible
            try {
                String bornYearStr = born.length() >= 4 ? born.substring(0, 4) : null;
                if (bornYearStr != null) {
                    int bornYear = Integer.parseInt(bornYearStr);
                    int awardYear = Integer.parseInt(year.trim());
                    return awardYear - bornYear;
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return null;
    }
}