package com.java_template.application.processor;

import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
import com.java_template.application.entity.transformjob.version_1.TransformJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
public class ApplyTransformationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApplyTransformationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ApplyTransformationProcessor(SerializerFactory serializerFactory,
                                        EntityService entityService,
                                        ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TransformJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(TransformJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformJob entity) {
        return entity != null && entity.isValid();
    }

    private TransformJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformJob> context) {
        TransformJob job = context.entity();
        try {
            logger.info("ApplyTransformationProcessor started for job id={}, status={}", job.getId(), job.getStatus());

            // Mark job as running if not already started
            if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
                job.setStartedAt(Instant.now().toString());
            }
            job.setStatus("RUNNING");

            // Basic validation: searchFilterId required for search_transform jobs
            if ("search_transform".equalsIgnoreCase(job.getJobType())) {
                String sfId = job.getSearchFilterId();
                if (sfId == null || sfId.isBlank()) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("Missing searchFilterId for search_transform job");
                    job.setCompletedAt(Instant.now().toString());
                    job.setResultCount(0);
                    return job;
                }

                // Attempt to load SearchFilter by technical id (serialized UUID expected)
                try {
                    ObjectNode sfNode = entityService.getItem(
                        SearchFilter.ENTITY_NAME,
                        String.valueOf(SearchFilter.ENTITY_VERSION),
                        UUID.fromString(sfId)
                    ).join();

                    if (sfNode == null) {
                        job.setStatus("FAILED");
                        job.setErrorMessage("SearchFilter not found for id: " + sfId);
                        job.setCompletedAt(Instant.now().toString());
                        job.setResultCount(0);
                        return job;
                    }

                    SearchFilter filter = objectMapper.treeToValue(sfNode, SearchFilter.class);
                    if (filter == null || !filter.isValid()) {
                        job.setStatus("FAILED");
                        job.setErrorMessage("Invalid SearchFilter referenced by job: " + sfId);
                        job.setCompletedAt(Instant.now().toString());
                        job.setResultCount(0);
                        return job;
                    }

                    // NOTE: The environment for this template does not expose a generic search API here.
                    // Implementers should replace the following simulation with a real search + transform pipeline:
                    //  - Query Pet entities matching the SearchFilter
                    //  - For each Pet apply transformation rules from job.getRuleNames()
                    //  - Store transformed results and set outputLocation/resultCount accordingly
                    //
                    // For now we simulate result generation based on presence of rules.

                    List<String> rules = job.getRuleNames();
                    int simulatedResults = 0;
                    if (rules != null && !rules.isEmpty()) {
                        // Simulate that each rule produced some transformed items
                        simulatedResults = Math.max(0, rules.size() * 2); // arbitrary simulation
                    }

                    // Simulate writing results to an output location and updating job
                    String outLocation = "/results/" + (job.getId() != null ? job.getId() : UUID.randomUUID().toString()) + ".json";
                    job.setOutputLocation(outLocation);
                    job.setResultCount(simulatedResults);
                    job.setStatus("COMPLETED");
                    job.setCompletedAt(Instant.now().toString());
                    job.setErrorMessage(null);

                    logger.info("ApplyTransformationProcessor completed job id={}, results={}", job.getId(), simulatedResults);
                    return job;

                } catch (IllegalArgumentException ex) {
                    // UUID parsing failed
                    job.setStatus("FAILED");
                    job.setErrorMessage("Invalid UUID format for searchFilterId: " + ex.getMessage());
                    job.setCompletedAt(Instant.now().toString());
                    job.setResultCount(0);
                    return job;
                } catch (CompletionException ce) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("Error retrieving SearchFilter: " + ce.getCause());
                    job.setCompletedAt(Instant.now().toString());
                    job.setResultCount(0);
                    return job;
                } catch (Exception e) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("Unexpected error while processing job: " + e.getMessage());
                    job.setCompletedAt(Instant.now().toString());
                    job.setResultCount(0);
                    return job;
                }
            } else {
                // For other job types (e.g., bulk_transform) provide a basic handling
                List<String> rules = job.getRuleNames();
                int simulatedResults = (rules != null) ? rules.size() : 0;

                String outLocation = "/results/" + (job.getId() != null ? job.getId() : UUID.randomUUID().toString()) + ".json";
                job.setOutputLocation(outLocation);
                job.setResultCount(simulatedResults);
                job.setStatus("COMPLETED");
                job.setCompletedAt(Instant.now().toString());
                job.setErrorMessage(null);

                logger.info("ApplyTransformationProcessor completed non-search job id={}, results={}", job.getId(), simulatedResults);
                return job;
            }

        } catch (Exception ex) {
            logger.error("Unhandled exception in ApplyTransformationProcessor for job id={}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setErrorMessage("Processor error: " + ex.getMessage());
            job.setCompletedAt(Instant.now().toString());
            job.setResultCount(job.getResultCount() != null ? job.getResultCount() : 0);
            return job;
        }
    }
}