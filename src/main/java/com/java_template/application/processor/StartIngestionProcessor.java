package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.activity.version_1.Activity;
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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StartIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Starting ingestion for Job request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for ingestion")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && job.getParameters() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Use parameters to fetch events from fakerest endpoint if provided, otherwise skip
            Map<String, Object> params = job.getParameters();
            String fakerest = params.containsKey("fakerest_endpoint") ? String.valueOf(params.get("fakerest_endpoint")) : null;

            // simulate fetch: query existing Activity items to avoid external calls in processor
            // For demo, we will create a synthetic activity if none exist for job
            SearchConditionRequest cond = SearchConditionRequest.group("AND");
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();

            if (items == null || items.size() == 0) {
                // create a synthetic activity to drive pipeline
                Activity a = new Activity();
                a.setActivityId("synthetic-" + Instant.now().toEpochMilli());
                a.setUserId("system");
                a.setTimestamp(Instant.now().toString());
                a.setPayload(new java.util.HashMap<>());
                a.setSourceJobId(job.getTechnicalId());
                a.setIngestionStatus("RAW");

                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(Activity.ENTITY_NAME, String.valueOf(Activity.ENTITY_VERSION), a);
                idFuture.get();
            }

            job.setStartedAt(Instant.now().toString());
            job.setStatus("IN_PROGRESS");

        } catch (Exception ex) {
            logger.error("Error starting ingestion for job {}: {}", job == null ? "<null>" : job.getTechnicalId(), ex.getMessage(), ex);
            if (job != null) {
                job.setStatus("FAILED");
            }
        }
        return job;
    }
}
