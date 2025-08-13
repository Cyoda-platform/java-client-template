package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    @Autowired
    public LaureateIngestionProcessor(SerializerFactory serializerFactory, RestTemplate restTemplate, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting Laureate ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid job state")
            .map(this::ingestLaureatesFromJob)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getSourceUrl() != null && !job.getSourceUrl().isEmpty()
            && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job ingestLaureatesFromJob(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Ingesting laureates from source URL: {}", job.getSourceUrl());

        // Fetch laureates data from sourceUrl
        List<Laureate> laureates = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(job.getSourceUrl(), HttpMethod.GET, null, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String body = response.getBody();
                JsonNode root = objectMapper.readTree(body);
                ArrayNode records = (ArrayNode) root.path("records");
                for (JsonNode record : records) {
                    ObjectNode fields = (ObjectNode) record.path("fields");
                    Laureate laureate = parseLaureate(fields);
                    if (laureate.isValid()) {
                        laureate.setIngestedAt(OffsetDateTime.now());
                        laureates.add(laureate);
                    } else {
                        logger.warn("Invalid laureate data skipped: {}", laureate);
                    }
                }
            } else {
                logger.error("Failed to fetch laureates data, status code: {}", response.getStatusCode());
                job.setStatus("FAILED");
                job.setFinishedAt(OffsetDateTime.now());
                return job;
            }
        } catch (RestClientException | java.io.IOException e) {
            logger.error("Error fetching laureates data from URL: {}", job.getSourceUrl(), e);
            job.setStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            return job;
        }

        // Persist laureates asynchronously
        List<CompletableFuture<Void>> persistFutures = laureates.stream().map(laureate -> {
            return entityService.addItem(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                laureate
            ).thenAccept(id -> logger.info("Persisted laureate with id: {}", id));
        }).collect(Collectors.toList());

        // Wait for all persistence to complete
        try {
            CompletableFuture.allOf(persistFutures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error persisting laureates", e);
            job.setStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            return job;
        }

        logger.info("Successfully ingested {} laureates", laureates.size());

        // Update job status and finishedAt timestamp
        job.setStatus("SUCCEEDED");
        job.setFinishedAt(OffsetDateTime.now());

        return job;
    }

    private Laureate parseLaureate(ObjectNode fields) {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(fields.path("id").asText(null));
        laureate.setFirstname(fields.path("firstname").asText(null));
        laureate.setSurname(fields.path("surname").asText(null));
        laureate.setBorn(fields.path("born").asText(null));
        laureate.setDied(fields.path("died").isNull() ? null : fields.path("died").asText(null));
        laureate.setBorncountry(fields.path("borncountry").asText(null));
        laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
        laureate.setBorncity(fields.path("borncity").asText(null));
        laureate.setGender(fields.path("gender").asText(null));
        laureate.setYear(fields.path("year").asText(null));
        laureate.setCategory(fields.path("category").asText(null));
        laureate.setMotivation(fields.path("motivation").asText(null));
        laureate.setAffiliationName(fields.path("name").asText(null));
        laureate.setAffiliationCity(fields.path("city").asText(null));
        laureate.setAffiliationCountry(fields.path("country").asText(null));
        return laureate;
    }
}
