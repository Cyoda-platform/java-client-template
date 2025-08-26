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

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

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
            .validate(this::isValidEntity, "Invalid SearchFilter state")
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

        // Create a TransformJob for this SearchFilter trigger.
        TransformJob job = new TransformJob();
        job.setId(UUID.randomUUID().toString());
        // Use the SearchFilter's user reference as creator when available
        job.setCreatedBy(filter.getUserId());
        job.setJobType("search_transform");
        job.setStatus("QUEUED");
        job.setSearchFilterId(filter.getId());
        job.setPriority(filter.getPageSize() != null ? Math.max(1, filter.getPageSize() / 10) : 5);
        job.setRuleNames(Collections.emptyList());
        job.setResultCount(0);
        // Optionally set an output location placeholder where results will be stored
        job.setOutputLocation("/transform-results/" + job.getId() + ".json");

        // Add TransformJob as a new entity via EntityService (do not update the triggering entity)
        try {
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                TransformJob.ENTITY_NAME,
                String.valueOf(TransformJob.ENTITY_VERSION),
                job
            );

            idFuture.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to enqueue TransformJob for SearchFilter {}: {}", filter.getId(), ex.getMessage(), ex);
                } else {
                    logger.info("Enqueued TransformJob {} (technicalId={}) for SearchFilter {}", job.getId(), uuid, filter.getId());
                }
            });
        } catch (Exception ex) {
            logger.error("Exception while enqueueing TransformJob for SearchFilter {}: {}", filter.getId(), ex.getMessage(), ex);
        }

        // We do not modify the triggering entity via EntityService. If desired,
        // we could update in-memory fields on the SearchFilter that will be persisted by Cyoda automatically.
        // For now, leave filter unchanged except logging.
        logger.debug("SearchFilter {} processed by EnqueueSearchJobProcessor", filter.getId());

        return filter;
    }
}