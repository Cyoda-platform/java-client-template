package com.java_template.application.processor;

import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RetryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing retry for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Object.class)
            .validate(e -> true, "")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Object> context) {
        try {
            logger.info("RetryProcessor invoked");
            // Generic retry processor: in this prototype simply logs and relies on orchestration's retry scheduling
            // A full implementation would examine retry counters on entities and re-queue appropriate processors.
            return context.entity();
        } catch (Exception ex) {
            logger.error("Unexpected error in RetryProcessor: {}", ex.getMessage(), ex);
            return context.entity();
        }
    }
}
