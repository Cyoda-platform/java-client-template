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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

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
        // Mark as INGESTING and set startedAt, increment attempts
        try {
            job.setState("INGESTING");
            job.setStartedAt(Instant.now().toString());
            Integer attempts = job.getAttempts();
            job.setAttempts((attempts == null ? 0 : attempts) + 1);
            job.setLastError(null);

            String apiEndpoint = job.getApiEndpoint();
            if (apiEndpoint == null || apiEndpoint.isBlank()) {
                job.setLastError("Missing apiEndpoint");
                job.setState("FAILED");
                job.setFinishedAt(Instant.now().toString());
                return job;
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response;
            try {
                response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ex) {
                logger.error("Failed to fetch data from apiEndpoint {}", apiEndpoint, ex);
                job.setLastError("Fetch error: " + ex.getMessage());
                job.setState("FAILED");
                job.setFinishedAt(Instant.now().toString());
                return job;
            }

            int persistedCount = 0;
            StringBuilder errors = new StringBuilder();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                JsonNode root;
                try {
                    root = objectMapper.readTree(body);
                } catch (IOException e) {
                    logger.error("Failed to parse response body", e);
                    job.setLastError("Parse error: " + e.getMessage());
                    job.setState("FAILED");
                    job.setFinishedAt(Instant.now().toString());
                    return job;
                }

                // Identify records array: try "records", else try root if array, else try "data"
                Iterator<JsonNode> recordsIter = null;
                if (root.has("records") && root.get("records").isArray()) {
                    recordsIter = root.get("records").elements();
                } else if (root.isArray()) {
                    recordsIter = root.elements();
                } else if (root.has("data") && root.get("data").isArray()) {
                    recordsIter = root.get("data").elements();
                }

                if (recordsIter == null) {
                    // No records found: treat as failure
                    logger.warn("No records array found in API response");
                    job.setLastError("No records found in response");
                    job.setState("FAILED");
                    job.setFinishedAt(Instant.now().toString());
                    return job;
                }

                while (recordsIter.hasNext()) {
                    JsonNode recordNode = recordsIter.next();
                    // Some APIs wrap fields under "fields"
                    JsonNode fields = recordNode.has("fields") ? recordNode.get("fields") : recordNode;
                    // Some wrappers: record -> record.fields
                    if (fields == null && recordNode.has("record") && recordNode.get("record").has("fields")) {
                        fields = recordNode.get("record").get("fields");
                    }
                    if (fields == null) {
                        fields = recordNode;
                    }

                    try {
                        Laureate laureate = new Laureate();

                        // Map common fields defensively
                        if (fields.has("id")) {
                            JsonNode n = fields.get("id");
                            if (n.canConvertToInt()) laureate.setId(n.asInt());
                            else {
                                try {
                                    laureate.setId(Integer.valueOf(n.asText()));
                                } catch (NumberFormatException ignored) {}
                            }
                        } else if (fields.has("laureate_id")) {
                            JsonNode n = fields.get("laureate_id");
                            if (n.canConvertToInt()) laureate.setId(n.asInt());
                        }

                        if (fields.has("firstname")) laureate.setFirstname(asTextOrNull(fields.get("firstname")));
                        if (fields.has("firstname")) laureate.setFirstname(asTextOrNull(fields.get("firstname")));
                        if (fields.has("firstname")) laureate.setFirstname(asTextOrNull(fields.get("firstname")));
                        if (fields.has("firstname")) laureate.setFirstname(asTextOrNull(fields.get("firstname")));
                        if (fields.has("firstname")) laureate.setFirstname(asTextOrNull(fields.get("firstname")));
                        // surname / familyname
                        if (fields.has("surname")) laureate.setSurname(asTextOrNull(fields.get("surname")));
                        else if (fields.has("familyname")) laureate.setSurname(asTextOrNull(fields.get("familyname")));
                        else if (fields.has("family_name")) laureate.setSurname(asTextOrNull(fields.get("family_name")));
                        // personal info
                        if (fields.has("born")) laureate.setBorn(asTextOrNull(fields.get("born")));
                        if (fields.has("died")) laureate.setDied(asTextOrNull(fields.get("died")));
                        if (fields.has("borncountry")) laureate.setBornCountry(asTextOrNull(fields.get("borncountry")));
                        else if (fields.has("born_country")) laureate.setBornCountry(asTextOrNull(fields.get("born_country")));
                        if (fields.has("borncountrycode")) laureate.setBornCountryCode(asTextOrNull(fields.get("borncountrycode")));
                        else if (fields.has("born_country_code")) laureate.setBornCountryCode(asTextOrNull(fields.get("born_country_code")));
                        if (fields.has("borncity")) laureate.setBornCity(asTextOrNull(fields.get("borncity")));
                        else if (fields.has("born_city")) laureate.setBornCity(asTextOrNull(fields.get("born_city")));
                        if (fields.has("gender")) laureate.setGender(asTextOrNull(fields.get("gender")));
                        // award info
                        if (fields.has("year")) laureate.setYear(asTextOrNull(fields.get("year")));
                        if (fields.has("category")) laureate.setCategory(asTextOrNull(fields.get("category")));
                        if (fields.has("motivation")) laureate.setMotivation(asTextOrNull(fields.get("motivation")));
                        // affiliation - try multiple possible names
                        if (fields.has("affiliation_name")) laureate.setAffiliationName(asTextOrNull(fields.get("affiliation_name")));
                        else if (fields.has("name")) laureate.setAffiliationName(asTextOrNull(fields.get("name")));
                        if (fields.has("affiliation_city")) laureate.setAffiliationCity(asTextOrNull(fields.get("affiliation_city")));
                        else if (fields.has("city")) laureate.setAffiliationCity(asTextOrNull(fields.get("city")));
                        if (fields.has("affiliation_country")) laureate.setAffiliationCountry(asTextOrNull(fields.get("affiliation_country")));
                        else if (fields.has("country")) laureate.setAffiliationCountry(asTextOrNull(fields.get("country")));

                        // Set createdAt and source job id
                        laureate.setCreatedAt(Instant.now().toString());
                        laureate.setSourceJobId(job.getId());

                        // Add laureate record asynchronously and wait for completion
                        try {
                            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                laureate
                            );
                            idFuture.join();
                            persistedCount++;
                        } catch (Exception e) {
                            logger.error("Failed to persist laureate {}", laureate, e);
                            errors.append("Persist error for laureate id=").append(laureate.getId()).append(": ").append(e.getMessage()).append("; ");
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to process record node", ex);
                        errors.append("Record processing error: ").append(ex.getMessage()).append("; ");
                    }
                } // end while recordsIter

                // finalize job state based on persistedCount and errors
                if (persistedCount > 0 && errors.length() == 0) {
                    job.setState("SUCCEEDED");
                    job.setLastError(null);
                } else if (persistedCount > 0) {
                    job.setState("SUCCEEDED");
                    job.setLastError(errors.toString());
                } else {
                    job.setState("FAILED");
                    job.setLastError(errors.length() > 0 ? errors.toString() : "No laureates persisted");
                }
                job.setFinishedAt(Instant.now().toString());
                return job;

            } else {
                logger.error("Received non-success status {} from apiEndpoint {}", response.statusCode(), apiEndpoint);
                job.setLastError("HTTP error: " + response.statusCode());
                job.setState("FAILED");
                job.setFinishedAt(Instant.now().toString());
                return job;
            }

        } catch (Exception e) {
            logger.error("Unhandled error in StartIngestionProcessor", e);
            job.setLastError("Unhandled error: " + e.getMessage());
            job.setState("FAILED");
            job.setFinishedAt(Instant.now().toString());
            return job;
        }
    }

    private String asTextOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }
}