package com.java_template.application.processor;

import com.java_template.application.entity.searchfilter.version_1.SearchFilter;
import com.java_template.application.entity.transformjob.version_1.TransformJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class EnqueueSearchJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueSearchJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EnqueueSearchJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SearchFilter for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchFilter.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchFilter entity) {
        return entity != null && entity.isValid();
    }

    private SearchFilter processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchFilter> context) {
        SearchFilter filter = context.entity();

        // Create a TransformJob for this SearchFilter and enqueue it via EntityService.
        TransformJob job = new TransformJob();
        job.setId(UUID.randomUUID().toString());
        job.setCreatedBy(filter.getUserId());
        job.setJobType("search_transform");
        // Mark as QUEUED by this processor; downstream processors can transition to RUNNING.
        job.setStatus("QUEUED");
        job.setSearchFilterId(filter.getId());
        // No output yet
        job.setOutputLocation(null);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setResultCount(null);
        // Ensure ruleNames is non-null (TransformJob.isValid requires non-null)
        List<String> rules = new ArrayList<>();
        job.setRuleNames(rules);
        // Priority can be left null or set to a default; leave null to allow system defaults
        job.setPriority(null);

        try {
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                TransformJob.ENTITY_NAME,
                String.valueOf(TransformJob.ENTITY_VERSION),
                job
            );
            // Wait for persistence to complete; if it fails an exception will be thrown and caught below
            idFuture.join();
            logger.info("Enqueued TransformJob {} for SearchFilter {}", job.getId(), filter.getId());
        } catch (Exception ex) {
            logger.error("Failed to enqueue TransformJob for SearchFilter {}: {}", filter.getId(), ex.getMessage(), ex);
            // Do not modify the triggering entity beyond this point. Optionally, could set diagnostic info on filter,
            // but SearchFilter has no fields for that; simply return the filter unchanged so the workflow can handle retries.
        }

        return filter;
    }
}