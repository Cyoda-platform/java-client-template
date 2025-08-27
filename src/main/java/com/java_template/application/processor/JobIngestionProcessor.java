package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

@Component
public class JobIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public JobIngestionProcessor(SerializerFactory serializerFactory,
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

        // Mark job started
        String now = Instant.now().toString();
        job.setStartedAt(now);
        logger.info("Job [{}] ingestion started at {}", job.getJobId(), now);

        String source = job.getSourceEndpoint();
        if (source == null || source.isBlank()) {
            job.setErrorDetails("Missing sourceEndpoint");
            job.setState("FAILED");
            job.setFinishedAt(Instant.now().toString());
            return job;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(source))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
        } catch (Exception e) {
            logger.error("Invalid sourceEndpoint URI for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setErrorDetails("Invalid sourceEndpoint URI: " + e.getMessage());
            job.setState("FAILED");
            job.setFinishedAt(Instant.now().toString());
            return job;
        }

        List<Laureate> created = new ArrayList<>();
        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode recordsNode = null;

                // Try common paths for records/items
                if (root.has("records")) {
                    recordsNode = root.get("records");
                } else if (root.has("items")) {
                    recordsNode = root.get("items");
                } else if (root.isArray()) {
                    recordsNode = root;
                } else if (root.has("result") && root.get("result").has("records")) {
                    recordsNode = root.get("result").get("records");
                }

                if (recordsNode == null || !recordsNode.isArray()) {
                    // Try to fallback: maybe top-level has "data" or "dataset"
                    if (root.has("data") && root.get("data").isArray()) {
                        recordsNode = root.get("data");
                    }
                }

                if (recordsNode == null || !recordsNode.isArray()) {
                    // No iterable records found; treat as zero results but succeeded
                    job.setRecordsFetchedCount(0);
                    job.setState("SUCCEEDED");
                    job.setFinishedAt(Instant.now().toString());
                    return job;
                }

                int count = 0;
                for (JsonNode element : recordsNode) {
                    JsonNode fields = null;
                    if (element.has("record") && element.get("record").has("fields")) {
                        fields = element.get("record").get("fields");
                    } else if (element.has("fields")) {
                        fields = element.get("fields");
                    } else {
                        // Sometimes records are direct field objects
                        fields = element;
                    }

                    Laureate laureate = mapFieldsToLaureate(fields, job.getJobId());
                    if (laureate == null) {
                        // skip malformed record
                        continue;
                    }

                    // Persist laureate via entityService (do not update the triggering Job via entityService)
                    try {
                        CompletableFuture<UUID> idFuture = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                laureate
                        );
                        // Wait for persistence to ensure downstream workflows are triggered in order
                        idFuture.join();
                        created.add(laureate);
                        count++;
                    } catch (Exception ex) {
                        logger.warn("Failed to persist laureate (id={}) from job {}: {}", laureate.getId(), job.getJobId(), ex.getMessage());
                        // continue processing other records
                    }
                }

                job.setRecordsFetchedCount(count);
                job.setState("SUCCEEDED");
                job.setErrorDetails(null);
                job.setFinishedAt(Instant.now().toString());
                logger.info("Job [{}] ingestion succeeded. Records persisted: {}", job.getJobId(), count);
            } else {
                logger.error("Failed HTTP fetch for job {}: status={} body={}", job.getJobId(), status, body);
                job.setErrorDetails("HTTP " + status + ": " + (body == null ? "" : body));
                job.setState("FAILED");
                job.setFinishedAt(Instant.now().toString());
            }
        } catch (Exception e) {
            logger.error("Exception during ingestion for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setErrorDetails(e.getMessage());
            job.setState("FAILED");
            job.setFinishedAt(Instant.now().toString());
        }

        return job;
    }

    private Laureate mapFieldsToLaureate(JsonNode fields, String ingestJobId) {
        if (fields == null || fields.isNull()) return null;
        Laureate l = new Laureate();
        try {
            // Helper getters
            Integer id = null;
            if (fields.has("id") && !fields.get("id").isNull()) {
                JsonNode idNode = fields.get("id");
                if (idNode.isInt() || idNode.isLong()) {
                    id = idNode.intValue();
                } else if (idNode.isTextual()) {
                    try {
                        id = Integer.valueOf(idNode.asText());
                    } catch (NumberFormatException ignored) { }
                }
            } else if (fields.has("laureateId") && !fields.get("laureateId").isNull()) {
                JsonNode idNode = fields.get("laureateId");
                if (idNode.isInt() || idNode.isLong()) {
                    id = idNode.intValue();
                } else if (idNode.isTextual()) {
                    try {
                        id = Integer.valueOf(idNode.asText());
                    } catch (NumberFormatException ignored) { }
                }
            }
            l.setId(id);

            l.setFirstname(getText(fields, "firstname", "firstname", "firstName", "givenname", "given_name"));
            l.setSurname(getText(fields, "surname", "surname", "familyname", "family_name", "name"));
            l.setCategory(getText(fields, "category"));
            l.setYear(getText(fields, "year"));
            l.setMotivation(getText(fields, "motivation"));
            l.setAffiliationName(getText(fields, "affiliationName", "affiliation_name", "affiliation"));
            l.setAffiliationCity(getText(fields, "affiliationCity", "affiliation_city"));
            l.setAffiliationCountry(getText(fields, "affiliationCountry", "affiliation_country"));
            l.setBorn(getText(fields, "born", "born_date", "birth_date"));
            l.setBornCity(getText(fields, "borncity", "bornCity", "birth_place", "born_city"));
            l.setBornCountry(getText(fields, "borncountry", "bornCountry", "birth_country"));
            l.setBornCountryCode(getText(fields, "borncountrycode", "bornCountryCode", "birth_country_code", "country_code"));
            l.setDied(getText(fields, "died"));
            l.setGender(getText(fields, "gender"));
            l.setNormalizedCountryCode(null); // enrichment later processors may fill this
            l.setIngestJobId(ingestJobId);

            // Derive age at award if possible here as a convenience
            try {
                String born = l.getBorn();
                String yearStr = l.getYear();
                if (born != null && !born.isBlank() && yearStr != null && !yearStr.isBlank()) {
                    int birthYear = LocalDate.parse(born).getYear();
                    int awardYear = Integer.parseInt(yearStr.replaceAll("\\D", ""));
                    l.setDerivedAgeAtAward(awardYear - birthYear);
                }
            } catch (Exception ignored) {
                // leave derivedAgeAtAward null if computation fails
            }

            // Validate minimal required fields per Laureate.isValid() - will be validated by its processors
            return l;
        } catch (Exception e) {
            logger.warn("Failed to map laureate fields to entity: {}", e.getMessage());
            return null;
        }
    }

    private String getText(JsonNode node, String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            if (node.has(c) && !node.get(c).isNull()) {
                JsonNode v = node.get(c);
                if (v.isTextual()) return v.asText();
                else return v.toString();
            }
        }
        return null;
    }
}