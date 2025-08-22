package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.service.EntityService;

@Component
public class JobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
    private static final String OPEN_DATA_SOFT_API = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=nobel-prizes&q=&rows=100";

    public JobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

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

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String technicalId = context.request().getEntityId();

        logger.info("Starting processJob for {}", technicalId);
        try {
            // Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now().toString());

            // Call OpenDataSoft API for Nobel laureates data
            org.springframework.http.ResponseEntity<Map> response = restTemplate.getForEntity(OPEN_DATA_SOFT_API, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch data from OpenDataSoft API");
            }

            Map body = response.getBody();
            if (body == null || !body.containsKey("records")) {
                throw new RuntimeException("Invalid data received from OpenDataSoft API");
            }

            List records = (List) body.get("records");
            if (records == null) records = Collections.emptyList();

            for (Object recordObj : records) {
                Map recordMap = (Map) recordObj;
                if (!recordMap.containsKey("record")) continue;
                Map record = (Map) recordMap.get("record");
                if (!record.containsKey("fields")) continue;
                Map fields = (Map) record.get("fields");

                com.java_template.application.entity.Laureate laureate = new com.java_template.application.entity.Laureate();
                // Map fields carefully with null checks and type casts
                laureate.setLaureateId(String.valueOf(fields.getOrDefault("id", UUID.randomUUID().toString())));
                laureate.setFirstname((String) fields.getOrDefault("firstname", ""));
                laureate.setSurname((String) fields.getOrDefault("surname", ""));
                laureate.setBorn((String) fields.getOrDefault("born", ""));
                laureate.setDied((String) fields.getOrDefault("died", ""));
                laureate.setBornCountry((String) fields.getOrDefault("borncountry", ""));
                laureate.setBornCity((String) fields.getOrDefault("borncity", ""));
                laureate.setGender((String) fields.getOrDefault("gender", ""));
                laureate.setYear((String) fields.getOrDefault("year", ""));
                laureate.setCategory((String) fields.getOrDefault("category", ""));
                laureate.setMotivation((String) fields.getOrDefault("motivation", ""));
                laureate.setAffiliationName((String) fields.getOrDefault("name", ""));
                laureate.setAffiliationCity((String) fields.getOrDefault("city", ""));
                laureate.setAffiliationCountry((String) fields.getOrDefault("country", ""));

                if (laureate.isValid()) {
                    CompletableFuture<UUID> addFuture = entityService.addItem(com.java_template.application.entity.Laureate.ENTITY_NAME, com.java_template.common.config.Config.ENTITY_VERSION, laureate);
                    UUID laureateUUID = addFuture.get();
                    String laureateId = "laureate-" + laureateUUID.toString();
                    processLaureate(laureateId, laureate);
                } else {
                    logger.error("Invalid laureate data skipped: {}", laureate);
                }
            }

            // Update job status to SUCCEEDED and finishedAt timestamp
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now().toString());

            // Notify all active subscribers
            notifySubscribers();

            // Update job status to NOTIFIED
            job.setStatus("NOTIFIED");

            logger.info("processJob completed successfully for {}", technicalId);

        } catch (Exception e) {
            logger.error("processJob failed for {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setMessage(e.getMessage());
            job.setFinishedAt(Instant.now().toString());

            try {
                notifySubscribers();
                job.setStatus("NOTIFIED");
            } catch (Exception notifyEx) {
                logger.error("Failed to notify subscribers after job failure: {}", notifyEx.getMessage());
            }
        }

        return job;
    }

    private void processLaureate(String technicalId, com.java_template.application.entity.Laureate laureate) {
        // Validation and enrichment already done in isValid() and ingestion step
        logger.info("Processed Laureate {}", technicalId);
    }

    private void notifySubscribers() {
        try {
            List<com.java_template.application.entity.Subscriber> subscribers = entityService.getItemsByCondition(com.java_template.application.entity.Subscriber.ENTITY_NAME, com.java_template.common.config.Config.ENTITY_VERSION, Collections.singletonMap("active", true), true).get();
            for (com.java_template.application.entity.Subscriber sub : subscribers) {
                // Simulate notification logic, e.g., send email or webhook
                logger.info("Notified subscriber: {} via {}", sub.getContactValue(), sub.getContactType());
            }
        } catch (Exception e) {
            logger.error("Failed to notify subscribers: {}", e.getMessage());
        }
    }
}
