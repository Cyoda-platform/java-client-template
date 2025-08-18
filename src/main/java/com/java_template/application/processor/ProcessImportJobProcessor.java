package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class ProcessImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessImportJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob orchestration for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            // Transition to VALIDATING
            job.setStatus("VALIDATING");

            // The actual validation criterion runs separately in the workflow; here we only prepare the job
            // If validation criteria passed, the workflow will invoke EnrichProcessor and StoreHNItemProcessor
            // We ensure created counters are zeroed
            if (job.getItemsCreatedCount() == null) job.setItemsCreatedCount(0);
            if (job.getItemsUpdatedCount() == null) job.setItemsUpdatedCount(0);
            if (job.getItemsIgnoredCount() == null) job.setItemsIgnoredCount(0);

        } catch (Exception e) {
            logger.error("Error in ProcessImportJobProcessor for {}: {}", context.requestId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        return job;
    }
}
