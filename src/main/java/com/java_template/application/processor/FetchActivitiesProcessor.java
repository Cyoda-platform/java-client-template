package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.activity.version_1.Activity;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class FetchActivitiesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchActivitiesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchActivitiesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchActivities for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.getRunDate() != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        try {
            job.setStatus("FETCHING");
            if (job.getStartedAt() == null) job.setStartedAt(Instant.now().toString());

            // Simulate paging fetch with retry/backoff for transient errors
            ArrayNode all = objectMapper.createArrayNode();
            int page = 1;
            int pagesFetched = 0;
            int maxPages = 5; // safety cap for demo
            boolean transientError = false;

            while (page <= maxPages) {
                try {
                    // In production this is where you call external HTTP client with page param
                    List<Activity> fetched = new ArrayList<>();
                    for (int i = 0; i < 10; i++) {
                        Activity a = new Activity();
                        a.setActivityId(job.getJobId() + "-p" + page + "-act-" + i);
                        a.setUserId("user-" + ((i + page) % 5 + 1));
                        a.setTimestamp(Instant.now().toString());
                        a.setType(i % 3 == 0 ? "login" : (i % 3 == 1 ? "purchase" : "view"));
                        a.setSource(job.getSource());
                        a.setProcessed(false);
                        a.setValid(null);
                        a.setPersistedAt(null);
                        fetched.add(a);
                    }

                    // convert to Json and append
                    JsonNode node = objectMapper.valueToTree(fetched);
                    if (node != null && node.isArray()) {
                        ArrayNode arr = (ArrayNode) node;
                        arr.forEach(all::add);
                    }

                    pagesFetched++;
                    // For demo, stop after 2 pages sometimes
                    if (page >= 2) break;
                    page++;
                } catch (RuntimeException rex) {
                    // transient error handling: retry with simple backoff up to small number
                    logger.warn("Transient error fetching page {} for job {}: {}", page, job.getJobId(), rex.getMessage());
                    transientError = true;
                    try {
                        TimeUnit.MILLISECONDS.sleep(200 * page);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw rex;
                    }
                    // try one more time then break for demo
                    page++;
                }
            }

            // attach fetched activities into job.summary for downstream processors to pick
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("activitiesFetched", all.size());
            summary.put("pagesFetched", pagesFetched);
            summary.set("items", all);
            job.setSummary(summary);
            job.setStatus(transientError ? "FETCH_COMPLETED_WITH_ERRORS" : "FETCH_COMPLETED");
            job.setFailureReason(transientError ? "fetch had transient errors" : null);
            logger.info("Fetched {} activities for job {} (pages={})", all.size(), job.getJobId(), pagesFetched);
        } catch (Exception ex) {
            logger.error("Error fetching activities", ex);
            job.setFailureReason("fetch error: " + ex.getMessage());
            job.setStatus("FAILED");
        }

        return job;
    }
}
