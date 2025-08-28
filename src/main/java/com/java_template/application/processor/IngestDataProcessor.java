package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class IngestDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public IngestDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        // mark start of ingestion
        try {
            String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
            job.setStartedAt(now);
            job.setStatus("INGESTING");

            // Build HTTP client and request
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(job.getSourceUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String msg = "HTTP error " + response.statusCode();
                logger.error("Job {} failed to fetch source: {}", job.getJobId(), msg);
                job.setStatus("FAILED");
                job.setSummary(msg);
                job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
                return job;
            }

            String body = response.body();
            List<Laureate> created = new ArrayList<>();

            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                com.fasterxml.jackson.databind.JsonNode recordsNode = root.has("records") ? root.get("records") : null;
                com.fasterxml.jackson.databind.JsonNode iterableNode = recordsNode != null && recordsNode.isArray() ? recordsNode : (root.isArray() ? root : null);

                if (iterableNode == null) {
                    // Single object/fields style response
                    iterableNode = objectMapper.createArrayNode().add(root);
                }

                for (com.fasterxml.jackson.databind.JsonNode recNode : iterableNode) {
                    com.fasterxml.jackson.databind.JsonNode fieldsNode = null;
                    if (recNode.has("record") && recNode.get("record").has("fields")) {
                        fieldsNode = recNode.get("record").get("fields");
                    } else if (recNode.has("fields")) {
                        fieldsNode = recNode.get("fields");
                    } else {
                        fieldsNode = recNode;
                    }
                    if (fieldsNode == null || fieldsNode.isNull()) continue;

                    Laureate la = new Laureate();
                    // id
                    String idVal = getFirstText(fieldsNode, "id", "ID", "laureate_id");
                    if (idVal == null) {
                        // try nested "id" under record id
                        idVal = getFirstText(recNode, "id");
                    }
                    if (idVal != null) la.setId(idVal);
                    // personal
                    la.setFirstname(getFirstText(fieldsNode, "firstname", "firstName", "givenname"));
                    la.setSurname(getFirstText(fieldsNode, "surname", "lastName", "familyname"));
                    la.setGender(getFirstText(fieldsNode, "gender"));
                    la.setBorn(getFirstText(fieldsNode, "born", "birth_date", "born_date"));
                    la.setDied(getFirstText(fieldsNode, "died", "death_date", "died_date"));
                    // origin
                    la.setBornCountry(getFirstText(fieldsNode, "borncountry", "bornCountry", "birth_country"));
                    la.setBornCountryCode(getFirstText(fieldsNode, "borncountrycode", "bornCountryCode", "birth_country_code"));
                    la.setBornCity(getFirstText(fieldsNode, "borncity", "bornCity", "birth_city"));
                    // award and affiliation
                    la.setYear(getFirstText(fieldsNode, "year"));
                    la.setCategory(getFirstText(fieldsNode, "category"));
                    la.setMotivation(getFirstText(fieldsNode, "motivation"));
                    // affiliation fields: sometimes named name/city/country
                    la.setAffiliationName(getFirstText(fieldsNode, "name", "affiliation_name", "affiliationName"));
                    la.setAffiliationCity(getFirstText(fieldsNode, "city", "affiliation_city", "affiliationCity"));
                    la.setAffiliationCountry(getFirstText(fieldsNode, "country", "affiliation_country", "affiliationCountry"));

                    // Enrichment: compute age at award if possible
                    Integer age = computeAgeAtAward(la.getBorn(), la.getYear());
                    la.setAgeAtAward(age);

                    // normalized country code - prefer bornCountryCode if present
                    la.setNormalizedCountryCode(la.getBornCountryCode());

                    // validated left null - LaureateValidationProcessor will handle it
                    la.setValidated(null);

                    // set category and other mandatory fields already assigned above

                    // ensure id and mandatory fields exist per Laureate.isValid() - if missing, we still persist to allow downstream processors to mark invalid
                    try {
                        CompletableFuture<UUID> idFuture = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                Laureate.ENTITY_VERSION,
                                la
                        );
                        UUID createdId = idFuture.get();
                        created.add(la);
                        logger.debug("Created Laureate entity for source id={}, technicalId={}", la.getId(), createdId);
                    } catch (Exception ex) {
                        logger.error("Failed to persist laureate: {}", ex.getMessage(), ex);
                        // continue with others
                    }
                }
            } catch (Exception parseEx) {
                logger.error("Failed to parse response for job {}: {}", job.getJobId(), parseEx.getMessage(), parseEx);
                job.setStatus("FAILED");
                job.setSummary("Failed to parse response: " + parseEx.getMessage());
                job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
                return job;
            }

            job.setStatus("SUCCEEDED");
            job.setSummary("ingested " + created.size() + " laureates");
            job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
            return job;

        } catch (Exception ex) {
            logger.error("Ingestion failed for job {}: {}", job != null ? job.getJobId() : "unknown", ex.getMessage(), ex);
            if (job != null) {
                job.setStatus("FAILED");
                job.setSummary(ex.getMessage());
                job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
            }
            return job;
        }
    }

    // Helper to get first non-null text value from a JsonNode given candidate field names
    private String getFirstText(com.fasterxml.jackson.databind.JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            if (k == null) continue;
            com.fasterxml.jackson.databind.JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                if (v.isTextual()) return v.asText();
                // sometimes numbers
                return v.asText();
            }
        }
        return null;
    }

    private Integer computeAgeAtAward(String bornIso, String awardYear) {
        if (bornIso == null || bornIso.isBlank() || awardYear == null || awardYear.isBlank()) return null;
        try {
            // bornIso expected yyyy-MM-dd, but may contain time - parse only date part
            LocalDate birthDate;
            try {
                birthDate = LocalDate.parse(bornIso, DateTimeFormatter.ISO_DATE);
            } catch (DateTimeParseException dte) {
                // try to parse first 10 chars
                if (bornIso.length() >= 10) {
                    birthDate = LocalDate.parse(bornIso.substring(0, 10), DateTimeFormatter.ISO_DATE);
                } else {
                    return null;
                }
            }
            int y = Integer.parseInt(awardYear);
            return y - birthDate.getYear();
        } catch (Exception e) {
            logger.debug("Failed to compute ageAtAward for born='{}', year='{}': {}", bornIso, awardYear, e.getMessage());
            return null;
        }
    }
}