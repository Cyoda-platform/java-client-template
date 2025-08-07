package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Laureate;
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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.client.RestTemplate;

@Component
public class IngestNobelLaureatesProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public IngestNobelLaureatesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
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

        try {
            // Transition to INGESTING
            job.setStatus("INGESTING");
            entityService.addItem(Job.ENTITY_NAME, "1", job).get();
            logger.info("Job {} status updated to INGESTING", job.getJobName());

            // Call OpenDataSoft API to fetch Nobel laureates data
            String url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=1000";
            var response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed API call with status: " + response.getStatusCode());
            }
            String body = response.getBody();
            var rootNode = objectMapper.readTree(body);
            var records = rootNode.path("records");
            if (!records.isArray()) {
                throw new RuntimeException("API response does not contain records array");
            }

            int ingestedCount = 0;
            List<Laureate> laureatesToAdd = new ArrayList<>();
            for (var recordNode : records) {
                var fields = recordNode.path("fields");
                if (fields.isMissingNode()) continue;

                Laureate laureate = new Laureate();
                laureate.setLaureateId(fields.path("id").asText(""));
                laureate.setFirstname(fields.path("firstname").asText(""));
                laureate.setSurname(fields.path("surname").asText(""));
                laureate.setGender(fields.path("gender").asText(""));
                laureate.setBorn(fields.path("born").asText(""));
                laureate.setDied(fields.path("died").asText(""));
                laureate.setBornCountry(fields.path("borncountry").asText(""));
                laureate.setBornCountryCode(fields.path("borncountrycode").asText(""));
                laureate.setBornCity(fields.path("borncity").asText(""));
                laureate.setYear(fields.path("year").asText(""));
                laureate.setCategory(fields.path("category").asText(""));
                laureate.setMotivation(fields.path("motivation").asText(""));
                laureate.setAffiliationName(fields.path("name").asText(""));
                laureate.setAffiliationCity(fields.path("city").asText(""));
                laureate.setAffiliationCountry(fields.path("country").asText(""));
                laureate.setIngestedAt(Instant.now().toString());

                laureatesToAdd.add(laureate);
            }

            if (!laureatesToAdd.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems(Laureate.ENTITY_NAME, "1", laureatesToAdd);
                List<UUID> ids = idsFuture.get();
                for (int i = 0; i < ids.size(); i++) {
                    String laureateTechnicalId = "laureate-" + ids.get(i).toString();
                    Laureate laureate = laureatesToAdd.get(i);
                    // processLaureate logic can be added here if needed
                    ingestedCount++;
                }
            }

            job.setStatus("SUCCEEDED");
            job.setCompletedAt(Instant.now().toString());
            entityService.addItem(Job.ENTITY_NAME, "1", job).get();
            logger.info("Job {} ingestion succeeded, {} laureates ingested", job.getJobName(), ingestedCount);

        } catch (Exception e) {
            logger.error("Job {} ingestion failed: {}", job.getJobName(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            try {
                entityService.addItem(Job.ENTITY_NAME, "1", job).get();
            } catch (Exception ex) {
                logger.error("Failed to update job after failure: {}", ex.getMessage(), ex);
            }
        }

        // Trigger job notification workflow
        // processJobNotification logic is invoked via NotifySubscribersProcessor
        return job;
    }
}
