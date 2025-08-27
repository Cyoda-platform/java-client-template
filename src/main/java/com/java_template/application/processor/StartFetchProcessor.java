package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob entity = context.entity();

        // Business logic:
        // - Transition the ingestion job to FETCHING state to indicate the fetch has started.
        // - Log the intended fetch details (source URL, formats, time window).
        // - Do not call external services or persist other entities here; persistence of this entity's state
        //   will be handled by Cyoda automatically after the processor completes.
        try {
            String currentStatus = entity.getStatus();
            logger.info("IngestionJob[id={}, status={}] - preparing to start fetch from {} with formats={} for last {} days",
                entity.getId(), currentStatus, entity.getSourceUrl(), entity.getDataFormats(), entity.getTimeWindowDays());

            // Only move to FETCHING as part of the fetch start. Allow transition from PENDING or VALIDATING,
            // but if processor invoked in other states, still mark as FETCHING to reflect the attempt.
            entity.setStatus("FETCHING");

            // Additional diagnostics: log recognized formats
            String formats = entity.getDataFormats();
            if (formats != null) {
                String normalized = formats.trim().toUpperCase();
                boolean supportsJson = normalized.contains("JSON");
                boolean supportsXml = normalized.contains("XML");
                logger.info("Data formats support - JSON: {}, XML: {}", supportsJson, supportsXml);
            }

            // Note: Actual fetching of data and emission of RawDataBundleEvent is handled by downstream
            // components or other processors. This processor's responsibility is to update the job state
            // to FETCHING and initiate (log) the fetch intent.
        } catch (Exception ex) {
            logger.error("Error while starting fetch for IngestionJob[id={}]: {}", entity.getId(), ex.getMessage(), ex);
            // On unexpected errors we set status to FAILED to make the failure explicit.
            try {
                entity.setStatus("FAILED");
            } catch (Exception ignore) {}
        }

        return entity;
    }
}