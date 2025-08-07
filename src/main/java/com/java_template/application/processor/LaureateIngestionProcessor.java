package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

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
                .validate(this::isValidEntity, "Invalid Job state")
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

            List<Laureate> laureates = new ArrayList<>();

            for (var recordNode : records) {
                var fields = recordNode.path("fields");
                Laureate laureate = new Laureate();

                laureate.setFirstname(getTextValue(fields, "firstname"));
                laureate.setSurname(getTextValue(fields, "surname"));
                laureate.setGender(getTextValue(fields, "gender"));
                laureate.setBorn(parseDate(getTextValue(fields, "born")));
                laureate.setDied(parseDate(getTextValue(fields, "died")));
                laureate.setBorncountry(getTextValue(fields, "borncountry"));
                laureate.setBorncountrycode(getTextValue(fields, "borncountrycode"));
                laureate.setBorncity(getTextValue(fields, "borncity"));
                laureate.setYear(getTextValue(fields, "year"));
                laureate.setCategory(getTextValue(fields, "category"));
                laureate.setMotivation(getTextValue(fields, "motivation"));
                laureate.setAffiliationName(getTextValue(fields, "affiliationname"));
                laureate.setAffiliationCity(getTextValue(fields, "affiliationcity"));
                laureate.setAffiliationCountry(getTextValue(fields, "affiliationcountry"));

                laureates.add(laureate);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(Laureate.ENTITY_NAME, "1", laureates);
            List<UUID> technicalIds = idsFuture.get();

            for (int i = 0; i < technicalIds.size(); i++) {
                String techId = technicalIds.get(i).toString();
                Laureate laureate = laureates.get(i);
                processLaureate(techId, laureate);
            }

            job.setResultDetails("Ingested " + records.size() + " laureates from external datasource.");
            return true;
        } catch (Exception e) {
            logger.error("Exception during ingestion of Nobel laureates: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getTextValue(com.fasterxml.jackson.databind.JsonNode node, String fieldName) {
        var valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            logger.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private void notifySubscribers(Job job) {
        try {
            Condition activeCondition = Condition.of("$.active", "EQUALS", true);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", activeCondition);
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> activeSubsFuture = entityService.getItemsByCondition(Subscriber.ENTITY_NAME, "1", condition, true);
            com.fasterxml.jackson.databind.node.ArrayNode activeSubscribers = activeSubsFuture.get();
            int notifiedCount = (activeSubscribers == null) ? 0 : activeSubscribers.size();

            logger.info("Notified {} active subscribers for job {}", notifiedCount, job.getJobName());
        } catch (Exception e) {
            logger.error("Error notifying subscribers: {}", e.getMessage(), e);
        }
    }

    private void processLaureate(String technicalId, Laureate laureate) {
        // This method can be implemented if additional processing for laureates is needed
    }
}
