package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.store.InMemoryDataStore;
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

@Component
public class AggregateResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AggregateResultsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Aggregating results for job: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.getTechnicalId() != null;
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        long processed = InMemoryDataStore.importEvents.stream()
            .filter(e -> job.getTechnicalId().equals(e.getJobTechnicalId()))
            .count();
        long failures = InMemoryDataStore.importEvents.stream()
            .filter(e -> job.getTechnicalId().equals(e.getJobTechnicalId()) && "FAILURE".equals(e.getStatus()))
            .count();
        job.setProcessedCount((int) processed);
        job.setFailureCount((int) failures);
        InMemoryDataStore.jobsByTechnicalId.put(job.getTechnicalId(), job);
        logger.info("Aggregated results for job {} processed={} failures={}", job.getTechnicalId(), processed, failures);
        return job;
    }
}
