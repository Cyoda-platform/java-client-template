package com.java_template.application.processor;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.io.IOException;

@Component
public class FetchAndSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public FetchAndSendProcessor(SerializerFactory serializerFactory,
                                 EntityService entityService,
                                 ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklySendJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklySendJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid WeeklySendJob state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * WeeklySendJob entity.isValid() is too strict for runtime execution because
     * the job is created before the CatFact is associated. Validate only required
     * properties for running the fetch/send workflow (scheduledFor present).
     */
    private boolean isValidEntity(WeeklySendJob entity) {
        if (entity == null) return false;
        // scheduledFor is required to know when job should run
        if (entity.getScheduledFor() == null || entity.getScheduledFor().isBlank()) return false;
        // status may be CREATED/RUNNING etc. Accept any non-blank status or allow null (runner will set it)
        return true;
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob job = context.entity();

        try {
            // Mark as RUNNING (if not already). Do not persist here via entityService for the triggering entity —
            // returning the modified job will be persisted by the platform.
            job.setStatus("RUNNING");
            job.setRunAt(Instant.now().toString());

            // Call external Cat Fact API
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://catfact.ninja/fact"))
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                String err = "CatFact API returned non-2xx status: " + httpResponse.statusCode();
                logger.error(err + " body: {}", httpResponse.body());
                job.setStatus("FAILED");
                job.setErrorMessage(err + " body: " + httpResponse.body());
                return job;
            }

            // Parse response
            String body = httpResponse.body();
            String factText = null;
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
                if (root.has("fact") && !root.get("fact").isNull()) {
                    factText = root.get("fact").asText();
                }
            } catch (IOException e) {
                logger.error("Failed to parse CatFact API response", e);
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to parse CatFact API response: " + e.getMessage());
                return job;
            }

            if (factText == null || factText.isBlank()) {
                String err = "CatFact API returned empty fact";
                logger.error(err);
                job.setStatus("FAILED");
                job.setErrorMessage(err);
                return job;
            }

            // Build CatFact entity
            CatFact catFact = new CatFact();
            catFact.setText(factText);
            catFact.setSource("catfact.ninja");
            catFact.setFetchedAt(Instant.now().toString());
            catFact.setValidationStatus("PENDING");
            catFact.setSendCount(0);
            catFact.setEngagementScore(0.0);

            // Persist CatFact (this will trigger CatFact workflow)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    CatFact.ENTITY_NAME,
                    CatFact.ENTITY_VERSION,
                    catFact
            );
            UUID catId = idFuture.get();

            // Associate created CatFact technical id with the job entity
            job.setCatFactTechnicalId(catId.toString());

            // Retrieve subscribers and send emails (simulated)
            CompletableFuture<List<DataPayload>> subsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = subsFuture.get();
            int sentCount = 0;
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode dataNode = payload.getData();
                        Subscriber subscriber = objectMapper.treeToValue(dataNode, Subscriber.class);
                        if (subscriber != null && subscriber.getStatus() != null &&
                                "ACTIVE".equalsIgnoreCase(subscriber.getStatus()) &&
                                subscriber.getEmail() != null && !subscriber.getEmail().isBlank()) {

                            // Simulate sending email by logging
                            logger.info("Sending weekly cat fact to subscriber: {}", subscriber.getEmail());

                            // Count as sent
                            sentCount++;

                            // Optionally update subscriber entity (allowed: updating other entities)
                            try {
                                String technicalId = null;
                                com.fasterxml.jackson.databind.JsonNode meta = payload.getMeta();
                                if (meta != null && meta.has("entityId")) {
                                    technicalId = meta.get("entityId").asText();
                                }
                                if (technicalId != null && !technicalId.isBlank()) {
                                    // interactionsCount represents opens/clicks; do not increment here.
                                    // Persisting subscriber back without modifications is unnecessary,
                                    // but if you want to record an alternative metric you could update here.
                                    // We'll attempt to persist unchanged subscriber to ensure entity shape is consistent.
                                    try {
                                        entityService.updateItem(UUID.fromString(technicalId), subscriber).get();
                                    } catch (Exception ue) {
                                        logger.warn("Failed to update subscriber {}: {}", technicalId, ue.getMessage());
                                    }
                                }
                            } catch (Exception ex) {
                                logger.warn("Failed to update subscriber after send: {}", ex.getMessage());
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to process subscriber payload: {}", ex.getMessage());
                    }
                }
            }

            // Update CatFact send count
            catFact.setSendCount(sentCount);
            try {
                entityService.updateItem(catId, catFact).get();
            } catch (Exception ex) {
                logger.warn("Failed to update CatFact sendCount for {}: {}", catId, ex.getMessage());
            }

            // Mark job as DISPATCHED
            job.setStatus("DISPATCHED");
            job.setErrorMessage(null);
            return job;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during FetchAndSendProcessor", ie);
            job.setStatus("FAILED");
            job.setErrorMessage("Interrupted: " + ie.getMessage());
            return job;
        } catch (ExecutionException ee) {
            logger.error("Execution error in FetchAndSendProcessor", ee);
            job.setStatus("FAILED");
            job.setErrorMessage("Execution error: " + ee.getMessage());
            return job;
        } catch (IOException ioe) {
            logger.error("I/O error in FetchAndSendProcessor", ioe);
            job.setStatus("FAILED");
            job.setErrorMessage("I/O error: " + ioe.getMessage());
            return job;
        } catch (Exception e) {
            logger.error("Unexpected error in FetchAndSendProcessor", e);
            job.setStatus("FAILED");
            job.setErrorMessage("Unexpected error: " + e.getMessage());
            return job;
        }
    }
}