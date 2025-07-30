package com.java_template.application.processor;

import com.java_template.application.entity.ReportEmail;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class DataDownloadProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public DataDownloadProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataDownload for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ReportEmail.class) // We don't have DataDownload entity in POJO provided, but by context it exists, assuming ReportEmail for example
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportEmail entity) {
        return entity != null && entity.isValid();
    }

    private ReportEmail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportEmail> context) {
        ReportEmail entity = context.entity();

        // Business logic for DataDownloadProcessor based on processDataDownload flow described in functional requirements
        // However, DataDownload POJO not provided, so logic is not fully implementable here
        // We would:
        // - download CSV data from downloadUrl
        // - update dataContent and status
        // - generate report string
        // - create ReportEmail entities for each subscriber

        // Since DataDownload POJO and full business logic are missing, return entity unchanged
        return entity;
    }
}
