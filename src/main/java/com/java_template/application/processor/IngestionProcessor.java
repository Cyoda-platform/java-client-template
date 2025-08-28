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
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class IngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public IngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Initialize counts if null
        if (job.getTotalRecords() == null) job.setTotalRecords(0);
        if (job.getSucceededCount() == null) job.setSucceededCount(0);
        if (job.getFailedCount() == null) job.setFailedCount(0);

        // Mark start
        job.setStartedAt(Instant.now().toString());
        // mark ingestion progression - per workflow, this processor moves to POSTPROCESSING after ingestion
        job.setState("INGESTING");

        StringBuilder errorSummaryBuilder = new StringBuilder();
        HttpClient client = HttpClient.newHttpClient();

        try {
            String sourceUrl = job.getSourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl is missing");
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .GET()
                .build();

            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = httpResponse.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("Failed to fetch sourceUrl, status: " + status);
            }

            String body = httpResponse.body();
            JsonNode root = objectMapper.readTree(body);

            JsonNode recordsNode = null;
            if (root.has("records") && root.get("records").isArray()) {
                recordsNode = root.get("records");
            } else if (root.isArray()) {
                recordsNode = root;
            } else {
                // Some APIs wrap data inside 'records' or 'result' or similar; attempt to find first array child
                for (JsonNode child : root) {
                    if (child != null && child.isArray()) {
                        recordsNode = child;
                        break;
                    }
                }
                // fallback: if root has 'result' with 'records'
                if (recordsNode == null && root.has("result") && root.get("result").has("records")) {
                    recordsNode = root.get("result").get("records");
                }
            }

            if (recordsNode == null || !recordsNode.isArray()) {
                // Attempt to interpret root as a single record
                recordsNode = objectMapper.createArrayNode().add(root);
            }

            List<CompletableFuture<?>> addFutures = new ArrayList<>();

            for (JsonNode itemNode : recordsNode) {
                JsonNode fieldsNode = itemNode;
                // OpenDataSoft uses record -> fields
                if (itemNode.has("record") && itemNode.get("record").has("fields")) {
                    fieldsNode = itemNode.get("record").get("fields");
                } else if (itemNode.has("fields")) {
                    fieldsNode = itemNode.get("fields");
                }

                Laureate laureate = new Laureate();

                try {
                    // id (may be numeric or string)
                    if (fieldsNode.has("id") && !fieldsNode.get("id").isNull()) {
                        JsonNode idNode = fieldsNode.get("id");
                        if (idNode.isInt() || idNode.isLong()) {
                            laureate.setId(idNode.asInt());
                        } else {
                            try {
                                laureate.setId(Integer.parseInt(idNode.asText()));
                            } catch (Exception e) {
                                // ignore invalid id, will be caught by validation when needed
                            }
                        }
                    } else if (itemNode.has("id") && !itemNode.get("id").isNull()) {
                        JsonNode idNode = itemNode.get("id");
                        if (idNode.isInt() || idNode.isLong()) {
                            laureate.setId(idNode.asInt());
                        } else {
                            try {
                                laureate.setId(Integer.parseInt(idNode.asText()));
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }

                    laureate.setFirstname(fieldsNode.has("firstname") && !fieldsNode.get("firstname").isNull() ? fieldsNode.get("firstname").asText() : (fieldsNode.has("firstnameNormalized") ? fieldsNode.get("firstnameNormalized").asText() : null));
                    laureate.setSurname(fieldsNode.has("surname") && !fieldsNode.get("surname").isNull() ? fieldsNode.get("surname").asText() : (fieldsNode.has("familyName") ? fieldsNode.get("familyName").asText() : null));
                    laureate.setGender(fieldsNode.has("gender") && !fieldsNode.get("gender").isNull() ? fieldsNode.get("gender").asText() : null);
                    laureate.setBorn(fieldsNode.has("born") && !fieldsNode.get("born").isNull() ? fieldsNode.get("born").asText() : null);
                    laureate.setDied(fieldsNode.has("died") && !fieldsNode.get("died").isNull() ? fieldsNode.get("died").asText() : null);
                    laureate.setBorncountry(fieldsNode.has("borncountry") && !fieldsNode.get("borncountry").isNull() ? fieldsNode.get("borncountry").asText() : null);
                    laureate.setBorncountrycode(fieldsNode.has("borncountrycode") && !fieldsNode.get("borncountrycode").isNull() ? fieldsNode.get("borncountrycode").asText() : null);
                    laureate.setBorncity(fieldsNode.has("borncity") && !fieldsNode.get("borncity").isNull() ? fieldsNode.get("borncity").asText() : null);

                    // award info
                    laureate.setYear(fieldsNode.has("year") && !fieldsNode.get("year").isNull() ? fieldsNode.get("year").asText() : null);
                    laureate.setCategory(fieldsNode.has("category") && !fieldsNode.get("category").isNull() ? fieldsNode.get("category").asText() : null);
                    laureate.setMotivation(fieldsNode.has("motivation") && !fieldsNode.get("motivation").isNull() ? fieldsNode.get("motivation").asText() : null);

                    // affiliation fields: some datasets use 'name','city','country'
                    laureate.setAffiliationName(fieldsNode.has("name") && !fieldsNode.get("name").isNull() ? fieldsNode.get("name").asText() : (fieldsNode.has("affiliationName") ? fieldsNode.get("affiliationName").asText() : null));
                    laureate.setAffiliationCity(fieldsNode.has("city") && !fieldsNode.get("city").isNull() ? fieldsNode.get("city").asText() : (fieldsNode.has("affiliationCity") ? fieldsNode.get("affiliationCity").asText() : null));
                    laureate.setAffiliationCountry(fieldsNode.has("country") && !fieldsNode.get("country").isNull() ? fieldsNode.get("country").asText() : (fieldsNode.has("affiliationCountry") ? fieldsNode.get("affiliationCountry").asText() : null));

                    // ingest job id reference
                    laureate.setIngestJobId(job.getJobId());

                    // Enrichment: compute age at award year if possible
                    Integer computedAge = null;
                    try {
                        if (laureate.getBorn() != null && laureate.getYear() != null) {
                            String born = laureate.getBorn();
                            if (born.length() >= 4) {
                                int birthYear = Integer.parseInt(born.substring(0, 4));
                                int awardYear = Integer.parseInt(laureate.getYear());
                                computedAge = awardYear - birthYear;
                                if (computedAge < 0) computedAge = null;
                            }
                        }
                    } catch (Exception e) {
                        // ignore computation errors
                        computedAge = null;
                    }
                    laureate.setComputedAge(computedAge);

                    // Persist laureate asynchronously
                    CompletableFuture<?> addFuture = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        Laureate.ENTITY_VERSION,
                        laureate
                    ).whenComplete((uuid, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to persist Laureate id {}: {}", laureate.getId(), ex.getMessage());
                        }
                    });
                    addFutures.add(addFuture);

                    // update counters optimistically
                    job.setTotalRecords(job.getTotalRecords() + 1);

                } catch (Exception recEx) {
                    logger.error("Failed to process record: {}", recEx.getMessage(), recEx);
                    job.setTotalRecords(job.getTotalRecords() + 1);
                    job.setFailedCount(job.getFailedCount() + 1);
                    if (errorSummaryBuilder.length() > 0) errorSummaryBuilder.append("; ");
                    errorSummaryBuilder.append("Record processing error: ").append(recEx.getMessage());
                }
            }

            // Wait for all add operations to complete and count successes/failures
            for (CompletableFuture<?> f : addFutures) {
                try {
                    Object res = f.get();
                    // if succeeded increment succeededCount
                    job.setSucceededCount(job.getSucceededCount() + 1);
                } catch (Exception e) {
                    job.setFailedCount(job.getFailedCount() + 1);
                    logger.error("Failed to add laureate entity: {}", e.getMessage(), e);
                    if (errorSummaryBuilder.length() > 0) errorSummaryBuilder.append("; ");
                    errorSummaryBuilder.append("Persist error: ").append(e.getMessage());
                }
            }

            // After ingestion set next workflow state to POSTPROCESSING
            job.setState("POSTPROCESSING");
            job.setErrorSummary(errorSummaryBuilder.length() > 0 ? errorSummaryBuilder.toString() : null);

        } catch (Exception e) {
            logger.error("Ingestion failed for job {}: {}", job.getJobId(), e.getMessage(), e);
            job.setFailedCount((job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1);
            job.setErrorSummary("Ingestion error: " + e.getMessage());
            job.setState("FAILED");
        } finally {
            job.setFinishedAt(Instant.now().toString());
        }

        return job;
    }
}