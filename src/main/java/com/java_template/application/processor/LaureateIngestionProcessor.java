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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class LaureateIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LaureateIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl("https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records").build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Ingesting laureates for Job: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidJob, "Invalid Job entity state for ingestion")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidJob(Job job) {
        return job != null && job.getJobName() != null && !job.getJobName().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Update status to INGESTING and set startedAt timestamp
            job.setStatus("INGESTING");
            job.setStartedAt(Instant.now().toString());
            logger.info("Job status updated to INGESTING: {}", job.getJobName());

            // Fetch laureates from external API
            List<Laureate> laureates = fetchLaureatesFromApi();

            // Save each laureate (this triggers Laureate workflow)
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (Laureate laureate : laureates) {
                logger.info("Saving Laureate: {} {}", laureate.getFirstname(), laureate.getSurname());
                CompletableFuture<?> future = entityService.addItem(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    laureate
                );
                futures.add(future);
            }

            // Wait for all saves to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // On success, update job status and finishedAt
            job.setStatus("SUCCEEDED");
            job.setFinishedAt(Instant.now().toString());
            logger.info("Job status updated to SUCCEEDED: {}", job.getJobName());

        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(Instant.now().toString());
            logger.error("Error ingesting laureates for Job {}: {}", job.getJobName(), e.getMessage());
        }
        return job;
    }

    private List<Laureate> fetchLaureatesFromApi() throws Exception {
        // Call external API and parse laureates
        String responseBody = webClient.get()
            .retrieve()
            .bodyToMono(String.class)
            .block();

        if (responseBody == null || responseBody.isEmpty()) {
            throw new Exception("Empty response from laureates API");
        }

        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode recordsNode = rootNode.path("records");

        List<Laureate> laureates = new ArrayList<>();
        if (recordsNode.isArray()) {
            for (JsonNode recordNode : recordsNode) {
                JsonNode fields = recordNode.path("record").path("fields");
                Laureate laureate = parseLaureateFromJson(fields);
                laureates.add(laureate);
            }
        }
        return laureates;
    }

    private Laureate parseLaureateFromJson(JsonNode fields) {
        Laureate laureate = new Laureate();
        laureate.setLaureateId(fields.path("id").isInt() ? fields.path("id").intValue() : null);
        laureate.setFirstname(fields.path("firstname").asText(null));
        laureate.setSurname(fields.path("surname").asText(null));
        laureate.setGender(fields.path("gender").asText(null));
        laureate.setBorn(fields.path("born").asText(null));
        laureate.setDied(fields.path("died").asText(null));
        laureate.setBorncountry(fields.path("borncountry").asText(null));
        laureate.setBorncountrycode(fields.path("borncountrycode").asText(null));
        laureate.setBorncity(fields.path("borncity").asText(null));
        laureate.setYear(fields.path("year").asText(null));
        laureate.setCategory(fields.path("category").asText(null));
        laureate.setMotivation(fields.path("motivation").asText(null));
        laureate.setAffiliationName(fields.path("name").asText(null));
        laureate.setAffiliationCity(fields.path("city").asText(null));
        laureate.setAffiliationCountry(fields.path("country").asText(null));
        return laureate;
    }
}
