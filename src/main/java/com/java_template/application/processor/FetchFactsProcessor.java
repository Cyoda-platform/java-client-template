package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.catfact.version_1.CatFact;
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
import java.util.List;
import java.util.Map;

@Component
public class FetchFactsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchFactsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchFactsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchFacts for request: {}", request.getId());

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

    @SuppressWarnings("unchecked")
    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            Object params = job.getParameters();
            if (params instanceof ObjectNode) {
                ObjectNode p = (ObjectNode) params;
                // For prototype, assume sources is an array of URLs; we'll simulate fetching a single fact per source
                // and increment processed count in resultSummary
                int processed = 0;
                if (p.has("sources") && p.get("sources").isArray()) {
                    for (var node : p.withArray("sources")) {
                        String src = node.asText();
                        CatFact fact = new CatFact();
                        fact.setFactText("Fact fetched from " + src);
                        fact.setSource(src);
                        fact.setFetchedAt(Instant.now().toString());
                        fact.setStatus("ingested");
                        // In a real implementation we'd persist fact via EntityService; in workflow engine the new entity would be created.
                        processed++;
                        logger.info("Fetched fact from {} and created CatFact placeholder", src);
                    }
                }
                ObjectNode summary = job.getResultSummary();
                if (summary == null) {
                    logger.info("Initializing resultSummary for Job {}", job.getId());
                } else {
                    summary.put("processed", processed + summary.path("processed").asInt(0));
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching facts for Job {}: {}", job.getId(), ex.getMessage(), ex);
        }
        return job;
    }
}
