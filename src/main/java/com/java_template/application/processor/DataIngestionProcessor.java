package com.java_template.application.processor;

import com.java_template.application.entity.Job;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DataIngestionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DataIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        UUID technicalId = context.request().getEntityId();

        try {
            logger.info("Processing job {} - state: {}", technicalId, job.getState());

            // Validation logic moved to isValidEntity

            job.setState("INGESTING");
            // No update method available for external service, assume persistence handled externally

            logger.info("Job {} state transitioned to INGESTING", technicalId);

            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                job.setState("FAILED");
                job.setResultSummary("Failed to fetch laureates data: HTTP status " + response.getStatusCodeValue());
                job.setCompletedAt(OffsetDateTime.now());
                logger.error("Job {} failed during data fetch", technicalId);
                return job;
            }

            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode records = root.path("records");
            if (records.isMissingNode() || !records.isArray()) {
                job.setState("FAILED");
                job.setResultSummary("Invalid laureates data structure");
                job.setCompletedAt(OffsetDateTime.now());
                logger.error("Job {} failed due to invalid laureates data structure", technicalId);
                return job;
            }

            int processedCount = 0;
            List<Object> laureatesToAdd = new ArrayList<>();

            for (JsonNode record : records) {
                JsonNode fields = record.path("fields");
                if (fields.isMissingNode()) continue;

                com.java_template.application.entity.Laureate laureate = new com.java_template.application.entity.Laureate();
                laureate.setId(null); // will be assigned by external service
                laureate.setLaureateId(fields.path("id").asInt(0));
                laureate.setFirstname(fields.path("firstname").asText(null));
                laureate.setSurname(fields.path("surname").asText(null));
                laureate.setGender(fields.path("gender").asText(null));
                laureate.setBorn(fields.path("born").asText(null));
                laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
                laureate.setBorncountry(fields.path("borncountry").asText(null));
                laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
                laureate.setBorncity(fields.path("borncity").asText(null));
                laureate.setYear(fields.path("year").asText(null));
                laureate.setCategory(fields.path("category").asText(null));
                laureate.setMotivation(fields.path("motivation").asText(null));
                laureate.setAffiliationName(fields.path("name").asText(null));
                laureate.setAffiliationCity(fields.path("city").asText(null));
                laureate.setAffiliationCountry(fields.path("country").asText(null));

                laureatesToAdd.add(laureate);
            }

            if (!laureatesToAdd.isEmpty()) {
                CompletableFuture<java.util.List<UUID>> idsFuture = entityService.addItems(com.java_template.application.entity.Laureate.ENTITY_NAME, "1", laureatesToAdd);
                java.util.List<UUID> technicalIds = idsFuture.get();

                for (int i = 0; i < technicalIds.size(); i++) {
                    UUID laureateId = technicalIds.get(i);
                    com.java_template.application.entity.Laureate laureate = (com.java_template.application.entity.Laureate) laureatesToAdd.get(i);
                    laureate.setId(laureateId);
                    // Process laureate logic here if needed
                    logger.info("Laureate {} processed successfully", laureateId);
                    processedCount++;
                }
            }

            job.setState("SUCCEEDED");
            job.setResultSummary("Ingested " + processedCount + " laureates");
            job.setCompletedAt(OffsetDateTime.now());
            logger.info("Job {} ingestion succeeded with {} laureates", technicalId, processedCount);

            // Notify subscribers and set state to NOTIFIED_SUBSCRIBERS should be handled by another processor

        } catch (IOException e) {
            job.setState("FAILED");
            job.setResultSummary("Exception during processing: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            logger.error("Job {} failed with exception: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            job.setState("FAILED");
            job.setResultSummary("Unexpected error: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            logger.error("Job {} failed with unexpected error: {}", technicalId, e.getMessage());
        }

        return job;
    }
}
