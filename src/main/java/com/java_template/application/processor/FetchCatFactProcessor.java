package com.java_template.application.processor;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class FetchCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FetchCatFactProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklySendJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklySendJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Custom validation for FetchCatFactProcessor:
     * We must allow processing of WeeklySendJob entities that don't yet have a catfactRef
     * because this processor's purpose is to fetch/create the CatFact and link it.
     * Therefore only validate required fields for the job itself (jobName, scheduledDate, status).
     */
    private boolean isValidEntity(WeeklySendJob entity) {
        if (entity == null) return false;
        if (entity.getJobName() == null || entity.getJobName().isBlank()) return false;
        if (entity.getScheduledDate() == null || entity.getScheduledDate().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        // do NOT require catfactRef or targetCount here because they may be populated by this processor
        return true;
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob job = context.entity();

        // Ensure targetCount is not null to satisfy entity validation on persistence later
        if (job.getTargetCount() == null) {
            job.setTargetCount(0);
        }

        try {
            // Call external Cat Fact API
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://catfact.ninja/fact"))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                JsonNode root = objectMapper.readTree(body);
                String factText = root.path("fact").asText(null);

                if (factText == null || factText.isBlank()) {
                    logger.warn("Fetched cat fact is empty");
                    job.setStatus("FAILED");
                    return job;
                }

                // Build CatFact entity
                CatFact fact = new CatFact();
                fact.setFactId(UUID.randomUUID().toString());
                fact.setText(factText);
                fact.setFetchedDate(OffsetDateTime.now());
                fact.setValidationStatus("PENDING");
                fact.setArchivedDate(null);

                // Persist CatFact using EntityService (creates a new entity, which will trigger its workflow)
                try {
                    entityService.addItem(
                        CatFact.ENTITY_NAME,
                        String.valueOf(CatFact.ENTITY_VERSION),
                        fact
                    ).join();

                    // Link the created CatFact to the job and mark job as ready
                    job.setCatfactRef(fact.getFactId());
                    // Use READY to indicate fact is available for the next steps
                    job.setStatus("READY");
                } catch (Exception ex) {
                    logger.error("Failed to persist CatFact entity", ex);
                    job.setStatus("FAILED");
                }
            } else {
                logger.error("Failed to fetch cat fact. HTTP status: {}", response.statusCode());
                job.setStatus("FAILED");
            }
        } catch (Exception e) {
            logger.error("Error while fetching cat fact", e);
            job.setStatus("FAILED");
        }

        return job;
    }
}