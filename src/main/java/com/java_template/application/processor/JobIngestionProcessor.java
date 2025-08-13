package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

@Component
public class JobIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        if (job == null) {
            return false;
        }
        // Validate required fields for ingestion start
        if (job.getApiUrl() == null || job.getApiUrl().isEmpty()) {
            logger.error("Job apiUrl is null or empty");
            return false;
        }
        if (!"scheduled".equalsIgnoreCase(job.getStatus())) {
            logger.error("Job status is not 'scheduled' for ingestion start");
            return false;
        }
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // 1. Set status to 'INGESTING'
        job.setStatus("INGESTING");
        // 2. Set startedAt timestamp to current time
        job.setStartedAt(Instant.now().toString());
        // 3. Clear completedAt and message fields
        job.setCompletedAt(null);
        job.setMessage(null);

        // 4. Fetch Nobel laureates data from API
        try {
            URL url = new URL(job.getApiUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorMsg = "Failed to fetch data from API, response code: " + responseCode;
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                job.setMessage(errorMsg);
                logger.error(errorMsg);
                return job;
            }

            JsonNode rootNode = objectMapper.readTree(conn.getInputStream());
            JsonNode recordsNode = rootNode.path("records");
            if (!recordsNode.isArray()) {
                String errorMsg = "API response 'records' field is missing or not an array";
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                job.setMessage(errorMsg);
                logger.error(errorMsg);
                return job;
            }

            int successCount = 0;
            int failureCount = 0;

            for (JsonNode recordNode : recordsNode) {
                JsonNode fieldsNode = recordNode.path("fields");
                if (fieldsNode.isMissingNode()) {
                    failureCount++;
                    continue;
                }

                Laureate laureate = convertJsonToLaureate(fieldsNode);
                if (laureate == null) {
                    failureCount++;
                    continue;
                }

                // Validation processor
                boolean valid = validateLaureate(laureate);
                if (!valid) {
                    failureCount++;
                    continue;
                }

                // Enrichment processor
                enrichLaureate(laureate);

                // Persistence processor - save laureate asynchronously
                try {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureate
                    );
                    fut.get();
                    successCount++;
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to persist laureate with id {}: {}", laureate.getId(), e.getMessage());
                    failureCount++;
                }
            }

            if (failureCount > 0) {
                job.setStatus("FAILED");
                job.setMessage("Ingestion completed with " + failureCount + " failures and " + successCount + " successes.");
            } else {
                job.setStatus("SUCCEEDED");
                job.setMessage("Ingestion completed successfully with " + successCount + " records.");
            }
            job.setCompletedAt(Instant.now().toString());

        } catch (IOException e) {
            String errorMsg = "IOException during data ingestion: " + e.getMessage();
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            job.setMessage(errorMsg);
            logger.error(errorMsg);
        }

        return job;
    }

    private Laureate convertJsonToLaureate(JsonNode fieldsNode) {
        try {
            Laureate laureate = new Laureate();

            laureate.setId(fieldsNode.path("id").isInt() ? fieldsNode.get("id").asInt() : null);
            laureate.setFirstname(fieldsNode.path("firstname").asText(null));
            laureate.setSurname(fieldsNode.path("surname").asText(null));
            laureate.setBorn(fieldsNode.path("born").asText(null));
            laureate.setDied(fieldsNode.path("died").asText(null));
            laureate.setBorncountry(fieldsNode.path("borncountry").asText(null));
            laureate.setBorncountrycode(fieldsNode.path("borncountrycode").asText(null));
            laureate.setBorncity(fieldsNode.path("borncity").asText(null));
            laureate.setGender(fieldsNode.path("gender").asText(null));
            laureate.setYear(fieldsNode.path("year").asText(null));
            laureate.setCategory(fieldsNode.path("category").asText(null));
            laureate.setMotivation(fieldsNode.path("motivation").asText(null));
            laureate.setName(fieldsNode.path("name").asText(null));
            laureate.setCity(fieldsNode.path("city").asText(null));
            laureate.setCountry(fieldsNode.path("country").asText(null));

            return laureate;
        } catch (Exception e) {
            logger.error("Error converting JSON to Laureate: {}", e.getMessage());
            return null;
        }
    }

    private boolean validateLaureate(Laureate laureate) {
        if (laureate == null) {
            return false;
        }
        if (laureate.getId() == null) {
            logger.warn("Laureate id is null");
            return false;
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) {
            logger.warn("Laureate firstname is null or empty");
            return false;
        }
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) {
            logger.warn("Laureate surname is null or empty");
            return false;
        }
        if (laureate.getBorn() == null || laureate.getBorn().isEmpty()) {
            logger.warn("Laureate born date is null or empty");
            return false;
        }
        if (laureate.getBorncountry() == null || laureate.getBorncountry().isEmpty()) {
            logger.warn("Laureate borncountry is null or empty");
            return false;
        }
        if (laureate.getYear() == null || laureate.getYear().isEmpty()) {
            logger.warn("Laureate year is null or empty");
            return false;
        }
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) {
            logger.warn("Laureate category is null or empty");
            return false;
        }
        return true;
    }

    private void enrichLaureate(Laureate laureate) {
        try {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate bornDate = java.time.LocalDate.parse(laureate.getBorn(), formatter);
            java.time.LocalDate diedDate = null;
            if (laureate.getDied() != null && !laureate.getDied().isEmpty()) {
                diedDate = java.time.LocalDate.parse(laureate.getDied(), formatter);
            }
            int age = (diedDate == null) ? java.time.Period.between(bornDate, java.time.LocalDate.now()).getYears()
                    : java.time.Period.between(bornDate, diedDate).getYears();
            // No age field to set, so just log
            logger.info("Calculated age for laureate {} {}: {} years", laureate.getFirstname(), laureate.getSurname(), age);
        } catch (Exception e) {
            logger.warn("Failed to calculate age for laureate {} {}: {}", laureate.getFirstname(), laureate.getSurname(), e.getMessage());
        }
    }
}
