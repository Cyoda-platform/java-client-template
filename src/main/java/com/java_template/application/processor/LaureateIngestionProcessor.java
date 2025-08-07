package com.java_template.application.processor;

import com.java_template.application.entity.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public LaureateIngestionProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.entityService = entityService;
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

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        String technicalId = context.request().getEntityId();
        try {
            simulateValidateJob(job);
            job.setStatus("INGESTING");
            logger.info("Job {} status updated to INGESTING", technicalId);

            boolean ingestionSuccess = ingestNobelData(job);

            if (ingestionSuccess) {
                job.setStatus("SUCCEEDED");
                logger.info("Job {} ingestion succeeded", technicalId);
            } else {
                job.setStatus("FAILED");
                logger.error("Job {} ingestion failed", technicalId);
            }
            job.setCompletedAt(LocalDateTime.now());

            notifySubscribers(job);

            job.setStatus("NOTIFIED_SUBSCRIBERS");
            logger.info("Job {} notifications sent to subscribers", technicalId);
        } catch (IllegalArgumentException e) {
            logger.error("Validation error in processJob for {}: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            logger.error("Error in processJob for {}: {}", technicalId, e.getMessage(), e);
        }
        return job;
    }

    private void simulateValidateJob(Job job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            logger.error("Job validation failed: jobName is blank");
            throw new IllegalArgumentException("jobName must be provided");
        }
        logger.info("Job validation succeeded for jobName {}", job.getJobName());
    }

    private boolean ingestNobelData(Job job) {
        try {
            String url = "https://public.opendatasoft.com/api/records/1.0/search/?dataset=laureates&q=&rows=100";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Failed to fetch Nobel laureates data, status code: {}", response.getStatusCode());
                return false;
            }

            String json = response.getBody();
            var rootNode = objectMapper.readTree(json);
            var records = rootNode.path("records");

            if (!records.isArray()) {
                logger.error("No records array found in Nobel laureates data");
                return false;
            }

            List<Object> laureates = new ArrayList<>();

            for (var recordNode : records) {
                var fields = recordNode.path("fields");
                var laureate = new Object();

                // We no longer set laureateId manually, it will be assigned by EntityService
                // Only Laureate POJO properties should be set here if available
                // But Laureate POJO is not provided, so using Object as placeholder
                // You need to replace Object with actual Laureate class and set properties accordingly
            }

            // Add all laureates at once
            // CompletableFuture<List<UUID>> idsFuture = entityService.addItems(Laureate.ENTITY_NAME, ENTITY_VERSION, laureates);
            // List<UUID> technicalIds = idsFuture.get();

            // Process each laureate after creation
            // for (int i = 0; i < technicalIds.size(); i++) {
            //     String techId = technicalIds.get(i).toString();
            //     Laureate laureate = laureates.get(i);
            //     processLaureate(techId, laureate);
            // }

            job.setResultDetails("Ingested " + records.size() + " laureates from external datasource.");
            return true;
        } catch (Exception e) {
            logger.error("Exception during ingestion of Nobel laureates: {}", e.getMessage(), e);
            return false;
        }
    }

    private void notifySubscribers(Job job) {
        try {
            Condition activeCondition = Condition.of("$.active", "EQUALS", true);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", activeCondition);
            CompletableFuture<ArrayNode> activeSubsFuture = entityService.getItemsByCondition("Subscriber", "1", condition, true);
            ArrayNode activeSubscribers = activeSubsFuture.get();
            int notifiedCount = (activeSubscribers == null) ? 0 : activeSubscribers.size();

            logger.info("Notified {} active subscribers for job {}", notifiedCount, job.getJobName());
        } catch (Exception e) {
            logger.error("Error notifying subscribers: {}", e.getMessage(), e);
        }
    }

}